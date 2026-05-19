package es.altia.domeadapter.backend.shared.domain.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.altia.domeadapter.backend.shared.domain.exception.InvalidRetryStatusException;
import es.altia.domeadapter.backend.shared.domain.exception.ProcedureRetryRecordNotFoundException;
import es.altia.domeadapter.backend.shared.domain.exception.ResponseUriDeliveryException;
import es.altia.domeadapter.backend.shared.domain.exception.RetryPayloadException;
import es.altia.domeadapter.backend.shared.domain.model.dto.ResponseUriDeliveryResult;
import es.altia.domeadapter.backend.shared.domain.model.dto.VerifierOauth2AccessToken;
import es.altia.domeadapter.backend.shared.domain.model.dto.retry.LabelCredentialDeliveryPayload;
import es.altia.domeadapter.backend.shared.domain.model.entities.ProcedureRetry;
import es.altia.domeadapter.backend.shared.domain.model.enums.ActionType;
import es.altia.domeadapter.backend.shared.domain.model.enums.RetryStatus;
import es.altia.domeadapter.backend.shared.domain.service.CredentialDeliveryService;
import es.altia.domeadapter.backend.shared.domain.service.EmailService;
import es.altia.domeadapter.backend.shared.domain.service.M2MTokenService;
import es.altia.domeadapter.backend.shared.infrastructure.config.AppConfig;
import es.altia.domeadapter.backend.shared.infrastructure.repository.ProcedureRetryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProcedureRetryServiceImplTest {

    @Mock private ProcedureRetryRepository procedureRetryRepository;
    @Spy  private ObjectMapper objectMapper = new ObjectMapper();
    @Mock private CredentialDeliveryService credentialDeliveryService;
    @Mock private M2MTokenService m2mTokenService;
    @Mock private EmailService emailService;
    @Mock private AppConfig appConfig;

    private ProcedureRetryServiceImpl service;

    private static final UUID CREDENTIAL_UUID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String CRED_ID = "cred-123";
    private static final String PROD_SPEC_ID = "prod-spec-456";
    private static final String PROVIDER_EMAIL = "provider@example.com";
    private static final String RESPONSE_URI = "https://example.com/response-uri";
    private static final String SIGNED_VC = "signed-vc-jwt";
    private static final String ACCESS_TOKEN = "m2m-access-token";
    private static final VerifierOauth2AccessToken M2M_TOKEN =
            VerifierOauth2AccessToken.builder().accessToken(ACCESS_TOKEN).build();

    @BeforeEach
    void setUp() {
        service = new ProcedureRetryServiceImpl(
                procedureRetryRepository, objectMapper, credentialDeliveryService,
                m2mTokenService, emailService, appConfig
        );
    }

    // ── createRetryRecord ─────────────────────────────────────────────────

    @Test
    void createRetryRecord_happyPath_upsertsRecordWithPendingStatus() {
        when(procedureRetryRepository.upsert(any())).thenReturn(Mono.just(1));
        LabelCredentialDeliveryPayload payload = buildPayload();

        StepVerifier.create(service.createRetryRecord(CREDENTIAL_UUID, ActionType.UPLOAD_LABEL_TO_RESPONSE_URI, payload))
                .verifyComplete();

        verify(procedureRetryRepository).upsert(argThat(retryRecord ->
                retryRecord.getCredentialId().equals(CREDENTIAL_UUID) &&
                        retryRecord.getActionType() == ActionType.UPLOAD_LABEL_TO_RESPONSE_URI &&
                        retryRecord.getStatus() == RetryStatus.PENDING &&
                        retryRecord.getAttemptCount() == 0
        ));
    }

    @Test
    void createRetryRecord_upsertReturnsZero_completesWithoutError() {
        when(procedureRetryRepository.upsert(any())).thenReturn(Mono.just(0));

        StepVerifier.create(service.createRetryRecord(CREDENTIAL_UUID, ActionType.UPLOAD_LABEL_TO_RESPONSE_URI, buildPayload()))
                .verifyComplete();
    }

    @Test
    void createRetryRecord_repositoryError_swallowedAndCompletes() {
        when(procedureRetryRepository.upsert(any())).thenReturn(Mono.error(new RuntimeException("db error")));

        StepVerifier.create(service.createRetryRecord(CREDENTIAL_UUID, ActionType.UPLOAD_LABEL_TO_RESPONSE_URI, buildPayload()))
                .verifyComplete();
    }

    // ── retryAction ───────────────────────────────────────────────────────

    @Test
    void retryAction_recordNotFound_throwsProcedureRetryRecordNotFoundException() {
        when(procedureRetryRepository.findByCredentialIdAndActionType(CREDENTIAL_UUID, ActionType.UPLOAD_LABEL_TO_RESPONSE_URI))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.retryAction(CREDENTIAL_UUID, ActionType.UPLOAD_LABEL_TO_RESPONSE_URI))
                .expectError(ProcedureRetryRecordNotFoundException.class)
                .verify();
    }

    @Test
    void retryAction_recordNotPending_throwsInvalidRetryStatusException() {
        ProcedureRetry completed = buildRetryRecord(RetryStatus.COMPLETED, 3, "{}");
        when(procedureRetryRepository.findByCredentialIdAndActionType(CREDENTIAL_UUID, ActionType.UPLOAD_LABEL_TO_RESPONSE_URI))
                .thenReturn(Mono.just(completed));

        StepVerifier.create(service.retryAction(CREDENTIAL_UUID, ActionType.UPLOAD_LABEL_TO_RESPONSE_URI))
                .expectError(InvalidRetryStatusException.class)
                .verify();
    }

    @Test
    void retryAction_pendingRecord_executesDeliveryAndMarksCompleted() {
        ProcedureRetry retryRecord = buildRetryRecord(RetryStatus.PENDING, 0, buildPayloadJson());
        when(procedureRetryRepository.findByCredentialIdAndActionType(CREDENTIAL_UUID, ActionType.UPLOAD_LABEL_TO_RESPONSE_URI))
                .thenReturn(Mono.just(retryRecord));
        when(m2mTokenService.getM2MToken()).thenReturn(Mono.just(M2M_TOKEN));
        when(credentialDeliveryService.deliverLabelToResponseUri(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.just(ResponseUriDeliveryResult.success()));
        when(procedureRetryRepository.markAsCompleted(CREDENTIAL_UUID, ActionType.UPLOAD_LABEL_TO_RESPONSE_URI))
                .thenReturn(Mono.just(1));
        when(emailService.sendCertificationUploaded(anyString(), anyString(), anyString()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.retryAction(CREDENTIAL_UUID, ActionType.UPLOAD_LABEL_TO_RESPONSE_URI))
                .verifyComplete();

        verify(credentialDeliveryService).deliverLabelToResponseUri(RESPONSE_URI, SIGNED_VC, CRED_ID, ACCESS_TOKEN);
        verify(procedureRetryRepository).markAsCompleted(CREDENTIAL_UUID, ActionType.UPLOAD_LABEL_TO_RESPONSE_URI);
    }

    // ── markRetryAsCompleted ──────────────────────────────────────────────

    @Test
    void markRetryAsCompleted_delegatesToRepository() {
        when(procedureRetryRepository.markAsCompleted(CREDENTIAL_UUID, ActionType.UPLOAD_LABEL_TO_RESPONSE_URI))
                .thenReturn(Mono.just(1));

        StepVerifier.create(service.markRetryAsCompleted(CREDENTIAL_UUID, ActionType.UPLOAD_LABEL_TO_RESPONSE_URI))
                .verifyComplete();

        verify(procedureRetryRepository).markAsCompleted(CREDENTIAL_UUID, ActionType.UPLOAD_LABEL_TO_RESPONSE_URI);
    }

    // ── markRetryAsExhausted ──────────────────────────────────────────────

    @Test
    void markRetryAsExhausted_withOldRecords_marksThemAndSendsNotifications(){
        ProcedureRetry retryRecord = buildRetryRecord(RetryStatus.PENDING, 5, buildPayloadJson());
        when(procedureRetryRepository.findPendingRecordsOlderThan(any(Instant.class)))
                .thenReturn(Flux.just(retryRecord));
        when(procedureRetryRepository.markAsExhausted(CREDENTIAL_UUID, ActionType.UPLOAD_LABEL_TO_RESPONSE_URI))
                .thenReturn(Mono.just(1));
        when(appConfig.getLabelUploadCertifierEmail()).thenReturn("certifier@example.com");
        when(appConfig.getLabelUploadMarketplaceEmail()).thenReturn("marketplace@example.com");
        when(emailService.sendResponseUriExhausted(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.markRetryAsExhausted(Duration.ofDays(1)))
                .verifyComplete();

        verify(procedureRetryRepository).markAsExhausted(CREDENTIAL_UUID, ActionType.UPLOAD_LABEL_TO_RESPONSE_URI);
        verify(emailService, times(2)).sendResponseUriExhausted(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void markRetryAsExhausted_withNullThreshold_usesDefaultAndCompletes() {
        when(procedureRetryRepository.findPendingRecordsOlderThan(any(Instant.class)))
                .thenReturn(Flux.empty());

        StepVerifier.create(service.markRetryAsExhausted((Duration) null))
                .verifyComplete();
    }

    @Test
    void markRetryAsExhausted_noOldRecords_completesImmediately() {
        when(procedureRetryRepository.findPendingRecordsOlderThan(any(Instant.class)))
                .thenReturn(Flux.empty());

        StepVerifier.create(service.markRetryAsExhausted())
                .verifyComplete();

        verify(procedureRetryRepository, never()).markAsExhausted(any(), any());
    }

    // ── processPendingRetries ─────────────────────────────────────────────

    @Test
    void processPendingRetries_noPendingRecords_completesImmediately() {
        when(procedureRetryRepository.findByStatus(RetryStatus.PENDING)).thenReturn(Flux.empty());

        StepVerifier.create(service.processPendingRetries())
                .verifyComplete();

        verify(credentialDeliveryService, never()).deliverLabelToResponseUri(any(), any(), any(), any());
    }

    @Test
    void processPendingRetries_deliverySucceeds_marksCompletedAndNotifies(){
        ProcedureRetry retryRecord = buildRetryRecord(RetryStatus.PENDING, 0, buildPayloadJson());
        when(procedureRetryRepository.findByStatus(RetryStatus.PENDING)).thenReturn(Flux.just(retryRecord));
        when(m2mTokenService.getM2MToken()).thenReturn(Mono.just(M2M_TOKEN));
        when(credentialDeliveryService.deliverLabelToResponseUri(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.just(ResponseUriDeliveryResult.success()));
        when(procedureRetryRepository.markAsCompleted(CREDENTIAL_UUID, ActionType.UPLOAD_LABEL_TO_RESPONSE_URI))
                .thenReturn(Mono.just(1));
        when(emailService.sendCertificationUploaded(anyString(), anyString(), anyString()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.processPendingRetries())
                .verifyComplete();

        verify(credentialDeliveryService).deliverLabelToResponseUri(RESPONSE_URI, SIGNED_VC, CRED_ID, ACCESS_TOKEN);
        verify(procedureRetryRepository).markAsCompleted(CREDENTIAL_UUID, ActionType.UPLOAD_LABEL_TO_RESPONSE_URI);
    }

    @Test
    void processPendingRetries_deliveryFails_incrementsAttemptAndContinues() {
        ProcedureRetry retryRecord = buildRetryRecord(RetryStatus.PENDING, 1, buildPayloadJson());
        when(procedureRetryRepository.findByStatus(RetryStatus.PENDING)).thenReturn(Flux.just(retryRecord));
        when(m2mTokenService.getM2MToken()).thenReturn(Mono.just(M2M_TOKEN));
        when(credentialDeliveryService.deliverLabelToResponseUri(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.error(new ResponseUriDeliveryException("not found", 404, RESPONSE_URI, CRED_ID)));
        when(procedureRetryRepository.incrementAttemptCount(eq(CREDENTIAL_UUID), eq(ActionType.UPLOAD_LABEL_TO_RESPONSE_URI), any(Instant.class), nullable(String.class)))
                .thenReturn(Mono.just(1));

        StepVerifier.create(service.processPendingRetries())
                .verifyComplete();

        verify(procedureRetryRepository).incrementAttemptCount(eq(CREDENTIAL_UUID), eq(ActionType.UPLOAD_LABEL_TO_RESPONSE_URI), any(Instant.class), nullable(String.class));
        verify(procedureRetryRepository, never()).markAsCompleted(any(), any());
    }

    // ── handleInitialAction ───────────────────────────────────────────────

    @Test
    void handleInitialAction_deliverySucceeds_sendsProviderNotification() {
        when(m2mTokenService.getM2MToken()).thenReturn(Mono.just(M2M_TOKEN));
        when(credentialDeliveryService.deliverLabelToResponseUri(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.just(ResponseUriDeliveryResult.success()));
        when(emailService.sendCertificationUploaded(anyString(), anyString(), anyString()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.handleInitialAction(CREDENTIAL_UUID, ActionType.UPLOAD_LABEL_TO_RESPONSE_URI, buildPayload()))
                .verifyComplete();

        verify(emailService).sendCertificationUploaded(PROVIDER_EMAIL, PROD_SPEC_ID, CRED_ID);
    }

    @Test
    void handleInitialAction_deliverySucceedsWith202Html_sendsHtmlEmail() {
        when(m2mTokenService.getM2MToken()).thenReturn(Mono.just(M2M_TOKEN));
        when(credentialDeliveryService.deliverLabelToResponseUri(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.just(ResponseUriDeliveryResult.acceptedWithHtml("<html>docs</html>")));
        when(emailService.sendResponseUriAcceptedWithHtml(anyString(), anyString(), anyString()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.handleInitialAction(CREDENTIAL_UUID, ActionType.UPLOAD_LABEL_TO_RESPONSE_URI, buildPayload()))
                .verifyComplete();

        verify(emailService).sendResponseUriAcceptedWithHtml(PROVIDER_EMAIL, CRED_ID, "<html>docs</html>");
    }

    @Test
    void handleInitialAction_deliveryFails_createsRetryRecordAndNotifiesCertifierAndMarketplace() {
        when(m2mTokenService.getM2MToken()).thenReturn(Mono.just(M2M_TOKEN));
        when(credentialDeliveryService.deliverLabelToResponseUri(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.error(new ResponseUriDeliveryException("bad request", 400, RESPONSE_URI, CRED_ID)));
        when(procedureRetryRepository.upsert(any())).thenReturn(Mono.just(1));
        when(appConfig.getLabelUploadCertifierEmail()).thenReturn("certifier@example.com");
        when(appConfig.getLabelUploadMarketplaceEmail()).thenReturn("marketplace@example.com");
        when(emailService.sendResponseUriFailed(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.handleInitialAction(CREDENTIAL_UUID, ActionType.UPLOAD_LABEL_TO_RESPONSE_URI, buildPayload()))
                .verifyComplete();

        verify(procedureRetryRepository).upsert(argThat(r -> r.getCredentialId().equals(CREDENTIAL_UUID)));
        verify(emailService, times(2)).sendResponseUriFailed(anyString(), eq(PROD_SPEC_ID), eq(CRED_ID), eq(PROVIDER_EMAIL));
    }

    @Test
    void handleInitialAction_invalidPayloadType_throwsRetryPayloadException() {
        assertThrows(RetryPayloadException.class,
                () -> service.handleInitialAction(
                        CREDENTIAL_UUID,
                        ActionType.UPLOAD_LABEL_TO_RESPONSE_URI,
                        "wrong-type"
                )
        );
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private LabelCredentialDeliveryPayload buildPayload() {
        return LabelCredentialDeliveryPayload.builder()
                .responseUri(RESPONSE_URI)
                .credentialId(CRED_ID)
                .productSpecificationId(PROD_SPEC_ID)
                .email(PROVIDER_EMAIL)
                .signedCredential(SIGNED_VC)
                .issuedBy("did:example:issuer-test")
                .build();
    }

    private String buildPayloadJson() {
        return """
                {
                    "responseUri": "%s",
                    "credentialId": "%s",
                    "productSpecificationId": "%s",
                    "email": "%s",
                    "signedCredential": "%s"
                }
                """.formatted(RESPONSE_URI, CRED_ID, PROD_SPEC_ID, PROVIDER_EMAIL, SIGNED_VC);
    }

    private ProcedureRetry buildRetryRecord(RetryStatus status, int attemptCount, String payloadJson) {
        return ProcedureRetry.builder()
                .id(UUID.randomUUID())
                .credentialId(CREDENTIAL_UUID)
                .actionType(ActionType.UPLOAD_LABEL_TO_RESPONSE_URI)
                .status(status)
                .attemptCount(attemptCount)
                .firstFailureAt(Instant.now().minus(Duration.ofDays(30)))
                .payload(payloadJson)
                .build();
    }
}
