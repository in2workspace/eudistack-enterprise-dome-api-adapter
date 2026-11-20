package es.altia.domeadapter.backend.issuance.domain.service.impl;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import es.altia.domeadapter.backend.shared.domain.model.dto.IssuerPreSubmittedCredentialDataRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IssuerCoreClientTest {

    @Mock
    private ExchangeFunction exchangeFunction;

    private IssuerCoreClient service;

    @BeforeEach
    void setUp() {
        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
        service = new IssuerCoreClient(webClient);
    }

    @Test
    void forward_directChannelSucceeds_returnsSignedCredential() {
        ClientResponse cr = successResponse(envelope(successChannel("direct", "signed.credential.jwt", null)));
        when(exchangeFunction.exchange(any())).thenReturn(Mono.just(cr));

        StepVerifier.create(service.forward(buildRequest(), "token", "id-token"))
                .assertNext(r -> assertThat(r.signedCredential()).isEqualTo("signed.credential.jwt"))
                .verifyComplete();
    }

    @Test
    void forward_emailChannelSucceeds_mapsOfferUri() {
        // Since EUD-268 the Issuer answers 200 with a single responses[] item: the requested mode.
        ClientResponse cr = successResponse(envelope(successChannel("email", null, "openid-credential-offer://issuer")));
        when(exchangeFunction.exchange(any())).thenReturn(Mono.just(cr));

        StepVerifier.create(service.forward(buildRequest(), "token", "id-token"))
                .assertNext(r -> {
                    assertThat(r.signedCredential()).isNull();
                    assertThat(r.credentialOfferUri()).isEqualTo("openid-credential-offer://issuer");
                })
                .verifyComplete();
    }

    @Test
    void forward_503DeliveryFailure_throwsWebClientResponseException() {
        // A failed delivery is a problem+json error (503 + Retry-After, or 500), never a 2xx body (EUD-268).
        ClientResponse cr = errorResponse(HttpStatus.SERVICE_UNAVAILABLE,
                "{\"type\":\"signing_failed\",\"title\":\"Signing failed\",\"status\":503}");
        when(exchangeFunction.exchange(any())).thenReturn(Mono.just(cr));

        StepVerifier.create(service.forward(buildRequest(), "token", "id-token"))
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(WebClientResponseException.class);
                    assertThat(((WebClientResponseException) e).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                })
                .verify();
    }

    @Test
    void forward_4xxResponse_throwsWebClientResponseException() {
        ClientResponse cr = errorResponse(HttpStatus.BAD_REQUEST, "{\"error\":\"bad request\"}");
        when(exchangeFunction.exchange(any())).thenReturn(Mono.just(cr));

        StepVerifier.create(service.forward(buildRequest(), "token", "id-token"))
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(WebClientResponseException.class);
                    assertThat(((WebClientResponseException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                })
                .verify();
    }

    @Test
    void forward_5xxResponse_throwsWebClientResponseException() {
        // All requested channels failing never reaches responses[] -- the Issuer re-raises the
        // original error as a plain (non-enveloped) problem response (EUD-167/spec-deltas.md D-7),
        // so this path is unaffected by the envelope change.
        ClientResponse cr = errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "");
        when(exchangeFunction.exchange(any())).thenReturn(Mono.just(cr));

        StepVerifier.create(service.forward(buildRequest(), "token", "id-token"))
                .expectError(WebClientResponseException.class)
                .verify();
    }

    @Test
    void forward_sendsAuthorizationAndIdTokenHeaders() {
        ArgumentCaptor<ClientRequest> captor = ArgumentCaptor.forClass(ClientRequest.class);
        ClientResponse cr = successResponse(envelope());
        when(exchangeFunction.exchange(captor.capture())).thenReturn(Mono.just(cr));

        service.forward(buildRequest(), "my-bearer-token", "my-id-token").block();

        ClientRequest req = captor.getValue();
        assertThat(req.headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer my-bearer-token");
        assertThat(req.headers().getFirst("X-ID-Token")).isEqualTo("my-id-token");
    }

    @Test
    void forward_postsToIssuancesPath() {
        ArgumentCaptor<ClientRequest> captor = ArgumentCaptor.forClass(ClientRequest.class);
        ClientResponse cr = successResponse(envelope());
        when(exchangeFunction.exchange(captor.capture())).thenReturn(Mono.just(cr));

        service.forward(buildRequest(), "token", "id-token").block();

        assertThat(captor.getValue().url().getPath()).isEqualTo("/api/v1/issuances");
    }

    private IssuerHttpResponse envelope(IssuerChannelResponse... channels) {
        return new IssuerHttpResponse(List.of(channels));
    }

    private IssuerChannelResponse successChannel(String channel, String signedCredential, String credentialOfferUri) {
        return new IssuerChannelResponse(channel, 200, new IssuerChannelBody(signedCredential, credentialOfferUri), null);
    }

    private ClientResponse successResponse(IssuerHttpResponse body) {
        ClientResponse cr = mock(ClientResponse.class);
        when(cr.statusCode()).thenReturn(HttpStatus.OK);
        when(cr.bodyToMono(IssuerHttpResponse.class)).thenReturn(Mono.just(body));
        when(cr.releaseBody()).thenReturn(Mono.empty());
        return cr;
    }

    private ClientResponse errorResponse(HttpStatus status, String body) {
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.setContentType(MediaType.APPLICATION_JSON);
        ClientResponse.Headers headers = mock(ClientResponse.Headers.class);
        when(headers.asHttpHeaders()).thenReturn(responseHeaders);

        ClientResponse cr = mock(ClientResponse.class);
        when(cr.statusCode()).thenReturn(status);
        when(cr.bodyToMono(byte[].class))
                .thenReturn(body.isEmpty() ? Mono.empty() : Mono.just(body.getBytes()));
        when(cr.headers()).thenReturn(headers);
        when(cr.releaseBody()).thenReturn(Mono.empty());
        return cr;
    }

    private IssuerPreSubmittedCredentialDataRequest buildRequest() {
        return IssuerPreSubmittedCredentialDataRequest.builder()
                .schema("gx.labelcredential.w3c.2")
                .payload(JsonNodeFactory.instance.objectNode())
                .email("test@example.com")
                .delivery("direct")
                .build();
    }
}
