package es.altia.domeadapter.backend.shared.domain.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.altia.domeadapter.backend.shared.domain.exception.InvalidRetryStatusException;
import es.altia.domeadapter.backend.shared.domain.exception.ProcedureRetryRecordNotFoundException;
import es.altia.domeadapter.backend.shared.domain.exception.ResponseUriDeliveryException;
import es.altia.domeadapter.backend.shared.domain.exception.RetryConfigurationException;
import es.altia.domeadapter.backend.shared.domain.exception.RetryPayloadException;
import es.altia.domeadapter.backend.shared.domain.model.dto.ResponseUriDeliveryResult;
import es.altia.domeadapter.backend.shared.domain.model.dto.retry.LabelCredentialDeliveryPayload;
import es.altia.domeadapter.backend.shared.domain.model.entities.ProcedureRetry;
import es.altia.domeadapter.backend.shared.domain.model.enums.ActionType;
import es.altia.domeadapter.backend.shared.domain.model.enums.RetryStatus;
import es.altia.domeadapter.backend.shared.domain.service.CredentialDeliveryService;
import es.altia.domeadapter.backend.shared.domain.service.EmailService;
import es.altia.domeadapter.backend.shared.domain.service.M2MTokenService;
import es.altia.domeadapter.backend.shared.domain.service.ProcedureRetryService;
import es.altia.domeadapter.backend.shared.infrastructure.config.AppConfig;
import es.altia.domeadapter.backend.shared.infrastructure.repository.ProcedureRetryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.net.ConnectException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProcedureRetryServiceImpl implements ProcedureRetryService {

    private final ProcedureRetryRepository procedureRetryRepository;
    private final ObjectMapper objectMapper;
    private final CredentialDeliveryService credentialDeliveryService;
    private final M2MTokenService m2mTokenService;
    private final EmailService emailService;
    private final AppConfig appConfig;

    private static final int INITIAL_RETRY_ATTEMPTS = 2;
    private static final Duration[] INITIAL_RETRY_DELAYS = {
            Duration.ofMinutes(2),
            Duration.ofMinutes(5),
            Duration.ofMinutes(15)
    };

    private static final Duration EXHAUSTION_THRESHOLD = Duration.ofDays(14);
    private static final int MAX_ERROR_LENGTH = 1000;

    // ──────────────────────────────────────────────────────────────────────
    // A. Initial Issuance Orchestration
    // ──────────────────────────────────────────────────────────────────────

    @Override
    public Mono<Void> handleInitialAction(UUID credentialId, ActionType actionType, Object payload) {
        log.info("[RETRY] Handling initial action for credentialId={} actionType={}", credentialId, actionType);
        return switch (actionType) {
            case UPLOAD_LABEL_TO_RESPONSE_URI ->
                    handleInitialLabelDeliveryAction(credentialId, castPayload(payload, LabelCredentialDeliveryPayload.class));
        };
    }

    private Mono<Void> handleInitialLabelDeliveryAction(UUID credentialId, LabelCredentialDeliveryPayload payload) {
        return deliverLabelWithImmediateRetries(payload)
                .flatMap(result -> {
                    log.info("[DELIVERY] Initial delivery succeeded for credId: {}", payload.credentialId());
                    return sendSuccessNotificationSafely(payload.email(), payload.productSpecificationId(), payload.credentialId(), result);
                })
                .onErrorResume(e -> {
                    log.error("[DELIVERY] Initial delivery failed after all retries for credId: {} - {}",
                            payload.credentialId(), e.getMessage());

                    return doCreateRetryRecord(credentialId, ActionType.UPLOAD_LABEL_TO_RESPONSE_URI, payload, e)
                            .then(sendInitialFailureNotificationSafely(payload.productSpecificationId(), payload.credentialId(), payload.email()));
                });
    }

    // ──────────────────────────────────────────────────────────────────────
    // B. Scheduler Retry Orchestration
    // ──────────────────────────────────────────────────────────────────────

    @Override
    public Mono<Void> processPendingRetries() {
        log.info("[RETRY] Starting processing of pending retries");
        return procedureRetryRepository.findByStatus(RetryStatus.PENDING)
                .flatMap(retryRecord ->
                        executeRetryAction(retryRecord)
                                .onErrorResume(error -> {
                                    log.warn("[SCHEDULER] Continuing after error processing retry for credential {}: {}",
                                            retryRecord.getCredentialId(), error.getMessage(), error);
                                    return Mono.empty();
                                })
                )
                .then()
                .doOnSuccess(unused -> log.info("[SCHEDULER] Completed processing all pending retries"));
    }

    private Mono<Void> executeRetryAction(ProcedureRetry retryRecord) {
        return switch (retryRecord.getActionType()) {
            case UPLOAD_LABEL_TO_RESPONSE_URI -> handleScheduledLabelDelivery(retryRecord);
        };
    }

    private Mono<Void> handleScheduledLabelDelivery(ProcedureRetry retryRecord) {
        log.info("[SCHEDULER] Processing retry attempt {} for credential {} action {}",
                retryRecord.getAttemptCount() + 1, retryRecord.getCredentialId(), retryRecord.getActionType());

        return deserializePayload(retryRecord)
                .flatMap(payload ->
                        deliverLabelWithImmediateRetries(payload)
                                .flatMap(result -> {
                                    log.info("[SCHEDULER] Delivery succeeded for credential {}", retryRecord.getCredentialId());
                                    return markRetryAsCompleted(retryRecord.getCredentialId(), retryRecord.getActionType())
                                            .then(sendScheduledSuccessNotifications(payload, result));
                                })
                )
                .onErrorResume(e -> {
                    log.warn("[SCHEDULER] Delivery failed for credential {}: {}",
                            retryRecord.getCredentialId(), e.getMessage(), e);
                    return updateRetryAfterScheduledFailure(retryRecord, e);
                });
    }

    private Mono<Void> sendScheduledSuccessNotifications(LabelCredentialDeliveryPayload payload, ResponseUriDeliveryResult result) {
        return Mono.when(
                sendSuccessNotificationSafely(payload.email(), payload.productSpecificationId(), payload.credentialId(), result),
                sendSuccessNotificationSafely(appConfig.getLabelUploadCertifierEmail(), payload.productSpecificationId(), payload.credentialId(), result),
                sendSuccessNotificationSafely(appConfig.getLabelUploadMarketplaceEmail(), payload.productSpecificationId(), payload.credentialId(), result)
        );
    }

    // ──────────────────────────────────────────────────────────────────────
    // Pure delivery with immediate retries
    // ──────────────────────────────────────────────────────────────────────

    private Mono<ResponseUriDeliveryResult> deliverLabelWithImmediateRetries(LabelCredentialDeliveryPayload payload) {
        log.info("[DELIVERY] Attempting to deliver label for credId: {} with immediate retries", payload.credentialId());
        return m2mTokenService.getM2MToken()
                .flatMap(m2mToken ->
                        credentialDeliveryService.deliverLabelToResponseUri(
                                payload.responseUri(),
                                payload.signedCredential(),
                                payload.credentialId(),
                                m2mToken.accessToken()
                        )
                )
                .retryWhen(createRetrySpec("deliverLabel", INITIAL_RETRY_ATTEMPTS, INITIAL_RETRY_DELAYS));
    }

    // ──────────────────────────────────────────────────────────────────────
    // Retry record management
    // ──────────────────────────────────────────────────────────────────────

    @Override
    public Mono<Void> createRetryRecord(UUID credentialId, ActionType actionType, Object payload) {
        return doCreateRetryRecord(credentialId, actionType, payload, null);
    }

    private Mono<Void> doCreateRetryRecord(UUID credentialId, ActionType actionType, Object payload, Throwable initialError) {
        log.debug("[RETRY] Creating retry record for credentialId={} actionType={}", credentialId, actionType);

        return Mono.fromCallable(() -> {
                    try {
                        String payloadJson = objectMapper.writeValueAsString(payload);
                        return ProcedureRetry.builder()
                                .id(UUID.randomUUID())
                                .credentialId(credentialId)
                                .actionType(actionType)
                                .status(RetryStatus.PENDING)
                                .attemptCount(0)
                                .firstFailureAt(Instant.now())
                                .payload(payloadJson)
                                .lastError(truncateError(initialError))
                                .issuedBy(extractIssuedBy(actionType, payload))
                                .build();
                    } catch (Exception e) {
                        log.error("[RETRY] Error serializing payload for credentialId={}: {}", credentialId, e.getMessage(), e);
                        throw new RetryPayloadException("Failed to serialize retry payload", e);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(procedureRetryRepository::upsert)
                .doOnNext(rowsAffected -> {
                    if (rowsAffected == null || rowsAffected == 0) {
                        log.warn("[RETRY] No retry record inserted/updated for credentialId={} actionType={}", credentialId, actionType);
                    } else {
                        log.info("[RETRY] Upserted retry record for credentialId={} actionType={} (rows: {})", credentialId, actionType, rowsAffected);
                    }
                })
                .then()
                .onErrorResume(e -> {
                    log.error("[RETRY] Error upserting retry record for credentialId={}: {}", credentialId, e.getMessage(), e);
                    return Mono.empty();
                });
    }

    @Override
    public Mono<Void> retryAction(UUID credentialId, ActionType actionType) {
        return procedureRetryRepository.findByCredentialIdAndActionType(credentialId, actionType)
                .switchIfEmpty(Mono.error(new ProcedureRetryRecordNotFoundException(
                        "No retry record found for credential " + credentialId + " and action " + actionType)))
                .filter(retryRecord -> retryRecord.getStatus() == RetryStatus.PENDING)
                .switchIfEmpty(Mono.error(new InvalidRetryStatusException("Retry record is not in PENDING status")))
                .flatMap(this::executeRetryAction);
    }

    @Override
    public Mono<Void> markRetryAsCompleted(UUID credentialId, ActionType actionType) {
        return procedureRetryRepository.markAsCompleted(credentialId, actionType)
                .doOnNext(rowsAffected -> {
                    if (rowsAffected == null || rowsAffected == 0) {
                        log.warn("[RETRY] No retry record found to mark as completed for credential {} action {}", credentialId, actionType);
                    } else {
                        log.debug("[RETRY] Marked retry as completed for credential {} action {}", credentialId, actionType);
                    }
                })
                .then();
    }

    @Override
    public Mono<Void> markRetryAsExhausted() {
        return markRetryAsExhausted(EXHAUSTION_THRESHOLD);
    }

    @Override
    public Mono<Void> markRetryAsExhausted(Duration customExhaustionThreshold) {
        Duration threshold = customExhaustionThreshold != null ? customExhaustionThreshold : EXHAUSTION_THRESHOLD;
        Instant exhaustionThreshold = Instant.now().minus(threshold);

        return procedureRetryRepository.findPendingRecordsOlderThan(exhaustionThreshold)
                .flatMap(retryRecord -> {
                    log.debug("[RETRY] Marking retry as exhausted for credential {} action {} (first failure at: {})",
                            retryRecord.getCredentialId(), retryRecord.getActionType(), retryRecord.getFirstFailureAt());

                    return procedureRetryRepository.markAsExhausted(retryRecord.getCredentialId(), retryRecord.getActionType())
                            .doOnSuccess(rowsAffected -> {
                                if (rowsAffected > 0) {
                                    log.debug("[RETRY] Successfully marked retry as exhausted for credential {}", retryRecord.getCredentialId());
                                } else {
                                    log.warn("[RETRY] Failed to mark retry as exhausted for credential {} (record may have been modified)", retryRecord.getCredentialId());
                                }
                            })
                            .then(sendExhaustionNotificationSafely(retryRecord));
                })
                .then();
    }

    // ──────────────────────────────────────────────────────────────────────
    // Non-blocking email notifications
    // ──────────────────────────────────────────────────────────────────────

    private Mono<Void> sendSuccessNotificationSafely(String email, String productSpecificationId, String credentialId, ResponseUriDeliveryResult result) {
        if (email == null || email.isBlank()) {
            log.warn("[NOTIFICATION] No email available for success notification, credId: {}", credentialId);
            return Mono.empty();
        }

        Mono<Void> emailMono;
        if (result.acceptedWithHtml() && result.html() != null) {
            emailMono = emailService.sendResponseUriAcceptedWithHtml(email, credentialId, result.html());
        } else {
            emailMono = emailService.sendCertificationUploaded(email, productSpecificationId, credentialId);
        }

        return emailMono
                .doOnSuccess(unused -> log.info("[NOTIFICATION] Success email sent for credId: {}", credentialId))
                .onErrorResume(e -> {
                    log.error("[NOTIFICATION] Failed to send success email for credId: {}: {}", credentialId, e.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<Void> sendInitialFailureNotificationSafely(String productSpecificationId, String credentialId, String providerEmail) {
        return Mono.when(
                sendFailureNotificationSafely(appConfig.getLabelUploadCertifierEmail(), productSpecificationId, credentialId, providerEmail, "certifier"),
                sendFailureNotificationSafely(appConfig.getLabelUploadMarketplaceEmail(), productSpecificationId, credentialId, providerEmail, "marketplace")
        );
    }

    private Mono<Void> sendFailureNotificationSafely(String email, String productSpecificationId, String credentialId, String providerEmail, String recipientType) {
        if (email == null || email.isBlank()) {
            log.warn("[NOTIFICATION] No {} email available for failure notification, productSpecId: {}", recipientType, productSpecificationId);
            return Mono.empty();
        }

        return emailService.sendResponseUriFailed(
                        email,
                        productSpecificationId,
                        credentialId,
                        providerEmail
                )
                .doOnSuccess(unused ->
                        log.info("[NOTIFICATION] Failure email sent to {} (recipient type: {}) for productSpecId: {}, credId: {}", email, recipientType, productSpecificationId, credentialId)
                )
                .onErrorResume(e -> {
                    log.error("[NOTIFICATION] Failed to send failure email to {} (recipient type: {}) for productSpecId: {}, credId: {}: {}",
                            email, recipientType, productSpecificationId, credentialId, e.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<Void> sendExhaustionNotificationSafely(ProcedureRetry retryRecord) {
        return deserializePayload(retryRecord)
                .flatMap(payload ->
                        Mono.when(
                                sendExhaustionNotificationSafely(
                                        appConfig.getLabelUploadCertifierEmail(),
                                        payload.productSpecificationId(),
                                        payload.credentialId(),
                                        payload.email(),
                                        retryRecord.getCredentialId(),
                                        "certifier"
                                ),
                                sendExhaustionNotificationSafely(
                                        appConfig.getLabelUploadMarketplaceEmail(),
                                        payload.productSpecificationId(),
                                        payload.credentialId(),
                                        payload.email(),
                                        retryRecord.getCredentialId(),
                                        "marketplace"
                                )
                        )
                )
                .onErrorResume(e -> {
                    log.error("[NOTIFICATION] Failed to deserialize payload for exhaustion notification, credential: {}: {}",
                            retryRecord.getCredentialId(), e.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<Void> sendExhaustionNotificationSafely(
            String email, String productSpecificationId, String credentialId,
            String providerEmail, UUID credentialUuid, String recipientType
    ) {
        if (email == null || email.isBlank()) {
            log.warn("[NOTIFICATION] No {} email available for exhaustion notification, credential: {}", recipientType, credentialUuid);
            return Mono.empty();
        }

        return emailService.sendResponseUriExhausted(email, productSpecificationId, credentialId, providerEmail)
                .doOnSuccess(unused -> log.info("[NOTIFICATION] Exhaustion email sent to {} for credential: {}, credId: {}",
                        recipientType, credentialUuid, credentialId))
                .onErrorResume(e -> {
                    log.error("[NOTIFICATION] Failed to send exhaustion email to {} for credential: {}, credId: {}: {}",
                            recipientType, credentialUuid, credentialId, e.getMessage());
                    return Mono.empty();
                });
    }

    // ──────────────────────────────────────────────────────────────────────
    // Scheduler failure handling
    // ──────────────────────────────────────────────────────────────────────

    private Mono<Void> updateRetryAfterScheduledFailure(ProcedureRetry retryRecord, Throwable error) {
        return procedureRetryRepository.incrementAttemptCount(
                        retryRecord.getCredentialId(),
                        retryRecord.getActionType(),
                        Instant.now(),
                        truncateError(error)
                )
                .doOnSuccess(rowsAffected -> log.info("[SCHEDULER] Incremented attempt count for credential {} (rows: {})",
                        retryRecord.getCredentialId(), rowsAffected))
                .then();
    }

    // ──────────────────────────────────────────────────────────────────────
    // Retry spec and helpers
    // ──────────────────────────────────────────────────────────────────────

    private Retry createRetrySpec(String operationName, int attempts, Duration[] delays) {
        validateRetryConfiguration(attempts, delays);
        return Retry.from(companion ->
                companion.concatMap(retrySignal -> handleRetrySignal(operationName, attempts, delays, retrySignal))
        );
    }

    private Mono<Long> handleRetrySignal(String operationName, int attempts, Duration[] delays, Retry.RetrySignal retrySignal) {
        long attempt = retrySignal.totalRetries() + 1;
        Throwable failure = requireFailure(retrySignal);

        if (!isRetryableError(failure)) {
            return propagateNonRetryableError(operationName, failure);
        }
        if (hasExhaustedAttempts(attempt, attempts)) {
            return propagateExhaustedRetries(operationName, attempts, failure);
        }

        Duration nextDelay = resolveNextDelay(attempt, delays);
        logRetryAttempt(operationName, attempt, attempts, nextDelay, failure);
        return Mono.delay(nextDelay);
    }

    private void validateRetryConfiguration(int attempts, Duration[] delays) {
        if (attempts < 1) throw new RetryConfigurationException("attempts must be greater than 0");
        if (delays == null || delays.length == 0) throw new RetryConfigurationException("delays must contain at least one value");
        for (Duration delay : delays) {
            if (delay == null || delay.isNegative()) throw new RetryConfigurationException("delays must not contain null or negative values");
        }
    }

    private Throwable requireFailure(Retry.RetrySignal retrySignal) {
        Throwable failure = retrySignal.failure();
        if (failure == null) throw new RetryConfigurationException("Retry failure is null");
        return failure;
    }

    private Mono<Long> propagateNonRetryableError(String operationName, Throwable failure) {
        log.warn("[RETRY] Not retrying {} - error is not retryable: {}", operationName, failure.getMessage());
        return Mono.error(failure);
    }

    private boolean hasExhaustedAttempts(long attempt, int attempts) {
        return attempt > attempts;
    }

    private Mono<Long> propagateExhaustedRetries(String operationName, int attempts, Throwable failure) {
        log.error("[RETRY] Retry attempts exhausted for {} after {} attempts. Final error: {}",
                operationName, attempts, failure.getMessage());
        return Mono.error(failure);
    }

    private Duration resolveNextDelay(long attempt, Duration[] delays) {
        return attempt <= delays.length ? delays[(int) attempt - 1] : delays[delays.length - 1];
    }

    private void logRetryAttempt(String operationName, long attempt, int attempts, Duration nextDelay, Throwable failure) {
        log.warn("[RETRY] Retrying {} - attempt {} of {}, next delay: {}, previous failure: {}",
                operationName, attempt, attempts, nextDelay, failure.getMessage());
    }

    private boolean isRetryableError(Throwable throwable) {
        if (throwable instanceof WebClientResponseException ex) {
            int statusCode = ex.getStatusCode().value();
            return ex.getStatusCode().is5xxServerError() || statusCode == 408 || statusCode == 429;
        }
        if (throwable instanceof ResponseUriDeliveryException ex) {
            int statusCode = ex.getHttpStatusCode();
            return statusCode >= 500 || statusCode == 408 || statusCode == 429 || statusCode == 401 || statusCode == 403;
        }
        return throwable instanceof ConnectException
                || throwable instanceof TimeoutException
                || throwable instanceof WebClientRequestException;
    }

    private Mono<LabelCredentialDeliveryPayload> deserializePayload(ProcedureRetry retryRecord) {
        return Mono.fromCallable(() -> {
                    try {
                        return objectMapper.readValue(retryRecord.getPayload(), LabelCredentialDeliveryPayload.class);
                    } catch (Exception e) {
                        log.error("[RETRY] Error deserializing retry payload for credential {}: {}", retryRecord.getCredentialId(), e.getMessage(), e);
                        throw new RetryPayloadException("Failed to deserialize retry payload", e);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private String extractIssuedBy(ActionType actionType, Object payload) {
        return switch (actionType) {
            case UPLOAD_LABEL_TO_RESPONSE_URI ->
                    payload instanceof LabelCredentialDeliveryPayload p ? p.issuedBy() : null;
        };
    }

    private String truncateError(Throwable error) {
        if (error == null) return null;
        String message = error.getMessage();
        if (message == null) return error.getClass().getSimpleName();
        return message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
    }

    private <T> T castPayload(Object payload, Class<T> expectedType) {
        if (!expectedType.isInstance(payload)) {
            throw new RetryPayloadException(
                    "Invalid payload type. Expected " + expectedType.getSimpleName()
                            + " but got " + (payload == null ? "null" : payload.getClass().getSimpleName())
            );
        }
        return expectedType.cast(payload);
    }
}
