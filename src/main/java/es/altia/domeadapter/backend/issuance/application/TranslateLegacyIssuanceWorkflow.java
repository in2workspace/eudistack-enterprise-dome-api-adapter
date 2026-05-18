package es.altia.domeadapter.backend.issuance.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.altia.domeadapter.backend.issuance.domain.service.IssuerCoreClientPort;
import es.altia.domeadapter.backend.shared.domain.exception.FormatUnsupportedException;
import es.altia.domeadapter.backend.shared.domain.exception.InvalidCredentialFormatException;
import es.altia.domeadapter.backend.shared.domain.exception.MissingEmailOwnerException;
import es.altia.domeadapter.backend.shared.domain.exception.MissingIdTokenHeaderException;
import es.altia.domeadapter.backend.shared.domain.exception.UnsupportedCredentialSchemaException;
import es.altia.domeadapter.backend.shared.domain.model.dto.IssuanceResponse;
import es.altia.domeadapter.backend.shared.domain.model.dto.IssuerPreSubmittedCredentialDataRequest;
import es.altia.domeadapter.backend.shared.domain.model.dto.PreSubmittedCredentialDataRequest;
import es.altia.domeadapter.backend.shared.domain.model.dto.credential.LabelCredential;
import es.altia.domeadapter.backend.shared.domain.model.dto.credential.lear.employee.LEARCredentialEmployee;
import es.altia.domeadapter.backend.shared.domain.model.dto.credential.lear.machine.LEARCredentialMachine;
import es.altia.domeadapter.backend.shared.domain.model.dto.retry.LabelCredentialDeliveryPayload;
import es.altia.domeadapter.backend.shared.domain.model.enums.ActionType;
import es.altia.domeadapter.backend.shared.domain.service.ProcedureRetryService;
import es.altia.domeadapter.backend.shared.domain.util.JwtUtils;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.naming.OperationNotSupportedException;
import java.util.UUID;

import static es.altia.domeadapter.backend.issuance.domain.Constants.*;
import static es.altia.domeadapter.backend.shared.domain.util.Constants.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranslateLegacyIssuanceWorkflow {

    private final IssuerCoreClientPort issuerCoreClient;
    private final ProcedureRetryService procedureRetryService;
    private final JwtUtils jwtUtils;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public Mono<IssuanceResponse> execute(PreSubmittedCredentialDataRequest request, String bearerToken, String idToken) {
        return validateIssuanceRequest(request, idToken)
                .then(Mono.defer(() -> executeValidatedIssuance(request, bearerToken, idToken)));
    }

    private Mono<Void> validateIssuanceRequest(PreSubmittedCredentialDataRequest request, String idToken) {
        return validateInitialIssuanceRequest(request)
                .then(Mono.defer(() -> validateLabelCredentialIdToken(request, idToken)))
                .then(Mono.defer(() -> validateLabelCredentialEmail(request)))
                .then(Mono.defer(() -> validateCredentialPayload(request)));
    }

    private Mono<Void> validateInitialIssuanceRequest(PreSubmittedCredentialDataRequest request) {
        if (!JWT_VC_JSON.equals(request.format())) {
            return Mono.error(new FormatUnsupportedException("Format: " + request.format() + " is not supported"));
        }

        if (!SYNC.equals(request.operationMode())) {
            return Mono.error(new OperationNotSupportedException(
                    "operation_mode: " + request.operationMode() + " with schema: " + request.schema()));
        }

        if (!isSupportedSchema(request.schema())) {
            return Mono.error(new UnsupportedCredentialSchemaException(
                    "Unsupported credential schema: '" + request.schema() + "'. Supported schemas are: "
                            + LEGACY_LABEL_CREDENTIAL_SCHEMA + ", " + LEGACY_LEAR_CREDENTIAL_EMPLOYEE_SCHEMA + ", " + LEGACY_LEAR_CREDENTIAL_MACHINE_SCHEMA));
        }

        return Mono.empty();
    }

    private Mono<Void> validateLabelCredentialIdToken(PreSubmittedCredentialDataRequest request, String idToken) {
        if (isLabelCredentialSchema(request.schema()) && (idToken == null || idToken.isBlank())) {
            return Mono.error(new MissingIdTokenHeaderException("Missing required ID Token header for Label credential issuance."));
        }

        return Mono.empty();
    }

    private Mono<Void> validateLabelCredentialEmail(PreSubmittedCredentialDataRequest request) {
        if (isLabelCredentialSchema(request.schema()) && !hasText(request.email())) {
            return Mono.error(new MissingEmailOwnerException("Email is required for Label credential issuance."));
        }

        return Mono.empty();
    }

    private Mono<Void> validateCredentialPayload(PreSubmittedCredentialDataRequest request) {
        return Mono.fromRunnable(() -> validatePayload(request));
    }

    private Mono<IssuanceResponse> executeValidatedIssuance(PreSubmittedCredentialDataRequest request, String bearerToken, String idToken) {
        String delivery = resolveDelivery(request);

        return resolveEmailOrError(request)
                .flatMap(email -> {
                    String translatedSchema = translateSchema(request.schema());

                    log.debug(
                            "[ISSUANCE] Forwarding legacy issuance request schema={} translatedSchema={} delivery={} emailPresent={} idTokenPresent={}",
                            request.schema(),
                            translatedSchema,
                            delivery,
                            hasText(email),
                            hasText(idToken)
                    );

                    IssuerPreSubmittedCredentialDataRequest issuerRequest =
                            IssuerPreSubmittedCredentialDataRequest.builder()
                                    .schema(translatedSchema)
                                    .payload(request.payload())
                                    .operationMode(request.operationMode())
                                    .email(email)
                                    .delivery(delivery)
                                    .build();

                    return issuerCoreClient.forward(issuerRequest, bearerToken, idToken)
                            .flatMap(response -> {
                                if (!isLabelCredentialSchema(request.schema())) {
                                    return Mono.just(response);
                                }

                                return handleLabelCredentialResponse(request, response, email);
                            });
                });
    }

    private void validatePayload(PreSubmittedCredentialDataRequest request) {
        if (LEGACY_LABEL_CREDENTIAL_SCHEMA.equals(request.schema())) {
            validateLabelCredentialPayload(request.payload());
        } else if (LEGACY_LEAR_CREDENTIAL_EMPLOYEE_SCHEMA.equals(request.schema())) {
            validateLearCredentialEmployeePayload(request.payload());
        } else if (LEGACY_LEAR_CREDENTIAL_MACHINE_SCHEMA.equals(request.schema())) {
            validateLearCredentialMachinePayload(request.payload());
        }
    }

    private void validateLabelCredentialPayload(JsonNode payload) {
        try {
            LabelCredential credential = objectMapper.convertValue(payload, LabelCredential.class);
            var violations = validator.validate(credential);
            if (!violations.isEmpty()) {
                throw new InvalidCredentialFormatException("Invalid LabelCredential payload");
            }
        } catch (IllegalArgumentException e) {
            log.error("[ISSUANCE] Error mapping LabelCredential payload", e);
            throw new InvalidCredentialFormatException("Invalid LabelCredential payload");
        }
    }

    private void validateLearCredentialEmployeePayload(JsonNode payload) {
        validateMandatorOrganizationIdentifier(payload, "Invalid LEARCredentialEmployee payload: mandator.organizationIdentifier is required");

        try {
            objectMapper.convertValue(payload, LEARCredentialEmployee.CredentialSubject.Mandate.class);
        } catch (IllegalArgumentException e) {
            log.error("[ISSUANCE] Error mapping LEARCredentialEmployee payload", e);
            throw new InvalidCredentialFormatException("Invalid LEARCredentialEmployee payload");
        }
    }

    private void validateLearCredentialMachinePayload(JsonNode payload) {
        validateMandatorOrganizationIdentifier(payload, "Invalid LEARCredentialMachine payload: mandator.organizationIdentifier is required");

        try {
            objectMapper.convertValue(payload, LEARCredentialMachine.CredentialSubject.Mandate.class);
        } catch (IllegalArgumentException e) {
            log.error("[ISSUANCE] Error mapping LEARCredentialMachine payload", e);
            throw new InvalidCredentialFormatException("Invalid LEARCredentialMachine payload");
        }
    }

    private void validateMandatorOrganizationIdentifier(JsonNode payload, String errorMessage) {
        JsonNode organizationIdentifierNode = payload
                .path(MANDATOR_FIELD)
                .path(ORGANIZATION_IDENTIFIER_FIELD);

        if (organizationIdentifierNode.isMissingNode()
                || organizationIdentifierNode.isNull()
                || organizationIdentifierNode.asText().isBlank()) {
            throw new InvalidCredentialFormatException(errorMessage);
        }
    }

    private boolean isSupportedSchema(String schema) {
        return LEGACY_LABEL_CREDENTIAL_SCHEMA.equals(schema)
                || LEGACY_LEAR_CREDENTIAL_EMPLOYEE_SCHEMA.equals(schema)
                || LEGACY_LEAR_CREDENTIAL_MACHINE_SCHEMA.equals(schema);
    }

    private String translateSchema(String schema) {
        return switch (schema) {
            case LEGACY_LABEL_CREDENTIAL_SCHEMA -> ISSUER_LABEL_CREDENTIAL_SCHEMA;
            case LEGACY_LEAR_CREDENTIAL_EMPLOYEE_SCHEMA -> ISSUER_LEAR_CREDENTIAL_EMPLOYEE_SCHEMA;
            case LEGACY_LEAR_CREDENTIAL_MACHINE_SCHEMA -> ISSUER_LEAR_CREDENTIAL_MACHINE_SCHEMA;
            default -> throw new UnsupportedCredentialSchemaException("Unsupported credential schema: " + schema);
        };
    }

    private String resolveDelivery(PreSubmittedCredentialDataRequest request) {
        if (request.delivery() != null && !request.delivery().isBlank()) {
            return request.delivery();
        }

        if (isLabelCredentialSchema(request.schema())) {
            return DEFAULT_LABEL_DELIVERY;
        }

        return DEFAULT_DELIVERY;
    }

    private boolean isLabelCredentialSchema(String schema) {
        return LEGACY_LABEL_CREDENTIAL_SCHEMA.equals(schema) || ISSUER_LABEL_CREDENTIAL_SCHEMA.equals(schema);
    }

    private Mono<String> resolveEmailOrError(PreSubmittedCredentialDataRequest request) {
        try {
            return Mono.just(resolveEmail(request));
        } catch (MissingEmailOwnerException e) {
            return Mono.error(e);
        }
    }

    private String resolveEmail(PreSubmittedCredentialDataRequest request) {
        if (LEGACY_LABEL_CREDENTIAL_SCHEMA.equals(request.schema())) {
            return requireEmail(
                    request.email(),
                    "Email is required for Label credential issuance."
            );
        }

        if (LEGACY_LEAR_CREDENTIAL_MACHINE_SCHEMA.equals(request.schema())) {
            if (hasText(request.email())) {
                return request.email();
            }

            return requireEmailFromPayloadMandator(
                    request.payload().path("mandator").path(EMAIL_FIELD),
                    "Mandator email is required for LEAR Machine credential issuance."
            );
        }

        if (LEGACY_LEAR_CREDENTIAL_EMPLOYEE_SCHEMA.equals(request.schema())) {
            if (hasText(request.email())) {
                return request.email();
            }

            return requireEmailFromPayloadMandator(
                    request.payload().path(MANDATEE_FIELD).path(EMAIL_FIELD),
                    "Mandatee email is required for LEAR Employee credential issuance."
            );
        }

        return requireEmail(
                request.email(),
                "Email is required for credential issuance."
        );
    }

    private String requireEmailFromPayloadMandator(JsonNode emailNode, String errorMessage) {
        if (emailNode == null || emailNode.isMissingNode() || emailNode.isNull()) {
            throw new MissingEmailOwnerException(errorMessage);
        }

        return requireEmail(emailNode.asText(), errorMessage);
    }

    private String requireEmail(String email, String errorMessage) {
        if (!hasText(email)) {
            throw new MissingEmailOwnerException(errorMessage);
        }

        return email;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private Mono<IssuanceResponse> handleLabelCredentialResponse(
            PreSubmittedCredentialDataRequest request,
            IssuanceResponse response,
            String email
    ) {
        String signedCredential = response.signedCredential();

        if (signedCredential == null || signedCredential.isBlank()) {
            return Mono.error(new InvalidCredentialFormatException("Issuer returned empty signed credential"));
        }

        fireLabelCredentialUpload(request, signedCredential, email);

        return Mono.just(response);
    }

    private void fireLabelCredentialUpload(
            PreSubmittedCredentialDataRequest request,
            String signedCredential,
            String email
    ) {
        UUID credentialId = jwtUtils.extractCredentialId(signedCredential);
        String productSpecificationId = jwtUtils.extractCredentialSubjectId(signedCredential);

        LabelCredentialDeliveryPayload payload = LabelCredentialDeliveryPayload.builder()
                .responseUri(request.responseUri())
                .credentialId(credentialId.toString())
                .productSpecificationId(productSpecificationId)
                .email(email)
                .signedCredential(signedCredential)
                .build();

        procedureRetryService
                .handleInitialAction(credentialId, ActionType.UPLOAD_LABEL_TO_RESPONSE_URI, payload)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        null,
                        e -> log.error(
                                "[ISSUANCE] Error during delivery pipeline for credentialId={}",
                                credentialId,
                                e
                        )
                );
    }
}