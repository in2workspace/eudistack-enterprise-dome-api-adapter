package es.altia.domeadapter.backend.issuance.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import es.altia.domeadapter.backend.issuance.domain.service.IssuerCoreClientPort;
import es.altia.domeadapter.backend.shared.domain.exception.InvalidCredentialFormatException;
import es.altia.domeadapter.backend.shared.domain.model.dto.IssuerPreSubmittedCredentialDataRequest;
import es.altia.domeadapter.backend.shared.domain.model.dto.IssuanceResponse;
import es.altia.domeadapter.backend.shared.domain.model.dto.PreSubmittedCredentialDataRequest;
import es.altia.domeadapter.backend.shared.domain.exception.UnsupportedCredentialSchemaException;
import es.altia.domeadapter.backend.shared.domain.model.dto.retry.LabelCredentialDeliveryPayload;
import es.altia.domeadapter.backend.shared.domain.model.enums.ActionType;
import es.altia.domeadapter.backend.shared.domain.service.M2MTokenService;
import es.altia.domeadapter.backend.shared.domain.service.ProcedureRetryService;
import es.altia.domeadapter.backend.shared.domain.util.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import jakarta.validation.Validator;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TranslateLegacyIssuanceWorkflowTest {

    @Mock
    private IssuerCoreClientPort issuerCorClientPort;
    @Mock
    private ProcedureRetryService procedureRetryService;
    @Mock
    private JwtUtils jwtUtils;
    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private Validator validator;

    @Mock
    private M2MTokenService m2mTokenService;

    private TranslateLegacyIssuanceWorkflow workflow;

    @BeforeEach
    void setUp() {
        when(m2mTokenService.getM2MToken()).thenReturn(Mono.empty());
        workflow = new TranslateLegacyIssuanceWorkflow(
                issuerCorClientPort,
                procedureRetryService,
                jwtUtils,
                objectMapper,
                validator
        );
    }

    @Test
    void execute_nonLabelCredential_forwardsAndReturnsEmpty() {
        when(issuerCorClientPort.forward(any(), anyString(), anyString()))
                .thenReturn(Mono.just(IssuanceResponse.builder().credentialOfferUri("https://issuer/offer").build()));

        StepVerifier.create(workflow.execute(buildRequest("LEARCredentialEmployee", null), "token", "idToken"))
                .expectNextCount(1)
                .verifyComplete();

        verifyNoInteractions(procedureRetryService, jwtUtils);
    }

    @Test
    void execute_labelCredential_withSignedCredential_triggersDeliveryPipeline() {
        UUID credentialId = UUID.randomUUID();
        String productSpecId = "https://example.com/product/123";
        String signedCredential = "header.payload.sig";

        when(issuerCorClientPort.forward(any(), anyString(), anyString()))
                .thenReturn(Mono.just(IssuanceResponse.builder().signedCredential(signedCredential).build()));
        when(jwtUtils.extractCredentialId(signedCredential)).thenReturn(credentialId);
        when(jwtUtils.extractCredentialSubjectId(signedCredential)).thenReturn(productSpecId);
        when(jwtUtils.extractSubject("idToken")).thenReturn("did:example:issuer-123");
        when(procedureRetryService.handleInitialAction(any(), any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(workflow.execute(buildRequest("gx:LabelCredential", "https://response.uri"), "token", "idToken"))
                .expectNextCount(1)
                .verifyComplete();

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(procedureRetryService).handleInitialAction(
                eq(credentialId),
                eq(ActionType.UPLOAD_LABEL_TO_RESPONSE_URI),
                payloadCaptor.capture()
        );

        LabelCredentialDeliveryPayload payload = (LabelCredentialDeliveryPayload) payloadCaptor.getValue();
        assertThat(payload.credentialId()).isEqualTo(credentialId.toString());
        assertThat(payload.productSpecificationId()).isEqualTo(productSpecId);
        assertThat(payload.signedCredential()).isEqualTo(signedCredential);
        assertThat(payload.responseUri()).isEqualTo("https://response.uri");
        assertThat(payload.email()).isEqualTo("test@example.com");
        assertThat(payload.issuedBy()).isEqualTo("did:example:issuer-123");
    }

    @Test
    void execute_delivery_labelCredential_noExplicit_usesDefaultLabelDelivery() {
        String signedCredential = "header.payload.sig";
        UUID credentialId = UUID.randomUUID();

        ArgumentCaptor<IssuerPreSubmittedCredentialDataRequest> captor =
                ArgumentCaptor.forClass(IssuerPreSubmittedCredentialDataRequest.class);

        when(issuerCorClientPort.forward(captor.capture(), anyString(), anyString()))
                .thenReturn(Mono.just(IssuanceResponse.builder().signedCredential(signedCredential).build()));
        when(jwtUtils.extractCredentialId(signedCredential)).thenReturn(credentialId);
        when(jwtUtils.extractCredentialSubjectId(signedCredential)).thenReturn("https://example.com/product/123");
        when(procedureRetryService.handleInitialAction(any(), any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(workflow.execute(buildRequest("gx:LabelCredential", null), "token", "idToken"))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(captor.getValue().delivery()).isEqualTo("email,direct");
    }

    @Test
    void execute_schemaMapping_labelCredential_mapsToExternal() {
        String signedCredential = "header.payload.sig";
        UUID credentialId = UUID.randomUUID();

        ArgumentCaptor<IssuerPreSubmittedCredentialDataRequest> captor =
                ArgumentCaptor.forClass(IssuerPreSubmittedCredentialDataRequest.class);

        when(issuerCorClientPort.forward(captor.capture(), anyString(), anyString()))
                .thenReturn(Mono.just(IssuanceResponse.builder().signedCredential(signedCredential).build()));
        when(jwtUtils.extractCredentialId(signedCredential)).thenReturn(credentialId);
        when(jwtUtils.extractCredentialSubjectId(signedCredential)).thenReturn("https://example.com/product/123");
        when(procedureRetryService.handleInitialAction(any(), any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(workflow.execute(buildRequest("gx:LabelCredential", null), "token", "idToken"))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(captor.getValue().schema()).isEqualTo("gx.labelcredential.w3c.2");
    }

    @Test
    void execute_labelCredential_nullSignedCredential_returnsInvalidCredentialFormatException() {
        when(issuerCorClientPort.forward(any(), anyString(), anyString()))
                .thenReturn(Mono.just(IssuanceResponse.builder().signedCredential(null).build()));

        StepVerifier.create(workflow.execute(buildRequest("gx:LabelCredential", null), "token", "idToken"))
                .verifyError(InvalidCredentialFormatException.class);

        verifyNoInteractions(procedureRetryService, jwtUtils);
    }

    @Test
    void execute_labelCredential_blankSignedCredential_returnsInvalidCredentialFormatException() {
        when(issuerCorClientPort.forward(any(), anyString(), anyString()))
                .thenReturn(Mono.just(IssuanceResponse.builder().signedCredential("  ").build()));

        StepVerifier.create(workflow.execute(buildRequest("gx:LabelCredential", null), "token", "idToken"))
                .verifyError(InvalidCredentialFormatException.class);

        verifyNoInteractions(procedureRetryService, jwtUtils);
    }


    @Test
    void execute_schemaMapping_learCredentialEmployee_mapsToExternal() {
        ArgumentCaptor<IssuerPreSubmittedCredentialDataRequest> captor =
                ArgumentCaptor.forClass(IssuerPreSubmittedCredentialDataRequest.class);
        when(issuerCorClientPort.forward(captor.capture(), anyString(), anyString()))
                .thenReturn(Mono.just(IssuanceResponse.builder().build()));

        StepVerifier.create(workflow.execute(buildRequest("LEARCredentialEmployee", null), "token", "idToken"))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(captor.getValue().schema()).isEqualTo("learcredential.employee.w3c.4");
    }

    @Test
    void execute_schemaMapping_unknownSchema_throwsUnsupportedSchemaException() {
        StepVerifier.create(workflow.execute(buildRequest("customSchema", null), "token", "idToken"))
                .verifyError(UnsupportedCredentialSchemaException.class);
    }

    @Test
    void execute_delivery_nonLabelCredential_noExplicit_usesDefaultDelivery() {
        ArgumentCaptor<IssuerPreSubmittedCredentialDataRequest> captor =
                ArgumentCaptor.forClass(IssuerPreSubmittedCredentialDataRequest.class);
        when(issuerCorClientPort.forward(captor.capture(), anyString(), anyString()))
                .thenReturn(Mono.just(IssuanceResponse.builder().build()));

        StepVerifier.create(workflow.execute(buildRequest("LEARCredentialEmployee", null), "token", "idToken"))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(captor.getValue().delivery()).isEqualTo("email");
    }

    @Test
    void execute_delivery_explicit_usesProvidedDelivery() {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.putObject("mandator").put("organizationIdentifier", "VATES-12345678");

        PreSubmittedCredentialDataRequest request = PreSubmittedCredentialDataRequest.builder()
                .schema("LEARCredentialEmployee")
                .format("jwt_vc_json")
                .operationMode("S")
                .payload(payload)
                .email("test@example.com")
                .delivery("sms")
                .build();
        ArgumentCaptor<IssuerPreSubmittedCredentialDataRequest> captor =
                ArgumentCaptor.forClass(IssuerPreSubmittedCredentialDataRequest.class);
        when(issuerCorClientPort.forward(captor.capture(), anyString(), anyString()))
                .thenReturn(Mono.just(IssuanceResponse.builder().build()));

        StepVerifier.create(workflow.execute(request, "token", "idToken"))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(captor.getValue().delivery()).isEqualTo("sms");
    }

    private PreSubmittedCredentialDataRequest buildRequest(String schema, String responseUri) {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.putObject("mandator").put("organizationIdentifier", "VATES-12345678");

        return PreSubmittedCredentialDataRequest.builder()
                .schema(schema)
                .format("jwt_vc_json")
                .operationMode("S")
                .payload(payload)
                .email("test@example.com")
                .responseUri(responseUri)
                .build();
    }
}
