package es.altia.domeadapter.backend.issuance.infrastructure.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.altia.domeadapter.backend.issuance.application.TranslateLegacyIssuanceWorkflow;
import es.altia.domeadapter.backend.shared.domain.model.dto.IssuanceResponse;
import es.altia.domeadapter.backend.shared.domain.model.dto.PreSubmittedCredentialDataRequest;
import es.altia.domeadapter.backend.shared.infrastructure.config.AppConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacyIssuanceControllerTest {

    private TranslateLegacyIssuanceWorkflow translateLegacyIssuanceWorkflow;
    private WebTestClient webTestClient;
    private AppConfig appConfig;

    @BeforeEach
    void setUp() {
        translateLegacyIssuanceWorkflow = mock(TranslateLegacyIssuanceWorkflow.class);
        appConfig = mock(AppConfig.class);
        when(appConfig.isIssuerDomeAdapterEnabled()).thenReturn(true);
        webTestClient = WebTestClient.bindToController(
                new LegacyIssuanceController(translateLegacyIssuanceWorkflow, appConfig, new ObjectMapper())
        ).build();
    }

    @Test
    void issueCredential_returns200_whenWorkflowSucceeds() {
        when(translateLegacyIssuanceWorkflow.execute(any(PreSubmittedCredentialDataRequest.class), anyString(), anyString()))
                .thenReturn(Mono.just(IssuanceResponse.builder().build()));

        webTestClient.post()
                .uri("/vci/v1/issuances")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer test-token")
                .header("X-ID-Token", "id-token")
                .bodyValue(validRequestBody())
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void issueCredential_propagatesExternalError_withOriginalStatusCode() {
        WebClientResponseException ex = WebClientResponseException.create(
                422, "Unprocessable Entity",
                new org.springframework.http.HttpHeaders(),
                "{\"error\":\"invalid schema\"}".getBytes(),
                null
        );
        when(translateLegacyIssuanceWorkflow.execute(any(), anyString(), anyString())).thenReturn(Mono.error(ex));

        webTestClient.post()
                .uri("/vci/v1/issuances")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer test-token")
                .header("X-ID-Token", "id-token")
                .bodyValue(validRequestBody())
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void issueCredential_stripsBearerPrefix_beforeForwardingToken() {
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        when(translateLegacyIssuanceWorkflow.execute(any(), tokenCaptor.capture(), anyString())).thenReturn(Mono.empty());

        webTestClient.post()
                .uri("/vci/v1/issuances")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer actual-token-value")
                .header("X-ID-Token", "id-token")
                .bodyValue(validRequestBody())
                .exchange()
                .expectStatus().isOk();

        assertThat(tokenCaptor.getValue()).isEqualTo("actual-token-value");
    }

    @Test
    void issueCredential_forwardsIdToken() {
        ArgumentCaptor<String> idTokenCaptor = ArgumentCaptor.forClass(String.class);
        when(translateLegacyIssuanceWorkflow.execute(any(), anyString(), idTokenCaptor.capture())).thenReturn(Mono.empty());

        webTestClient.post()
                .uri("/vci/v1/issuances")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer token")
                .header("X-ID-Token", "my-id-token")
                .bodyValue(validRequestBody())
                .exchange()
                .expectStatus().isOk();

        assertThat(idTokenCaptor.getValue()).isEqualTo("my-id-token");
    }

    @Test
    void issueCredential_deserializesRequestBody() {
        ArgumentCaptor<PreSubmittedCredentialDataRequest> requestCaptor =
                ArgumentCaptor.forClass(PreSubmittedCredentialDataRequest.class);
        when(translateLegacyIssuanceWorkflow.execute(requestCaptor.capture(), anyString(), anyString())).thenReturn(Mono.empty());

        webTestClient.post()
                .uri("/vci/v1/issuances")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer token")
                .header("X-ID-Token", "id-token")
                .bodyValue(validRequestBody())
                .exchange()
                .expectStatus().isOk();

        PreSubmittedCredentialDataRequest captured = requestCaptor.getValue();
        assertThat(captured.schema()).isEqualTo("LEARCredentialEmployee");
        assertThat(captured.format()).isEqualTo("jwt_vc");
        assertThat(captured.email()).isEqualTo("test@example.com");
    }

    private String validRequestBody() {
        return """
                {
                    "schema": "LEARCredentialEmployee",
                    "format": "jwt_vc",
                    "payload": {},
                    "email": "test@example.com"
                }
                """;
    }
}
