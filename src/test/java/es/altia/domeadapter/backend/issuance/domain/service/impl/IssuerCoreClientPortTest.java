package es.altia.domeadapter.backend.issuance.domain.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
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
        service = new IssuerCoreClient(webClient, new ObjectMapper());
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
    void forward_bothChannelsSucceed_mapsSignedCredentialAndOfferUri() {
        // gx:LabelCredential's default delivery is "email,direct" (Constants.DEFAULT_LABEL_DELIVERY) --
        // the Issuer returns 200 with one responses[] item per channel when both complete.
        ClientResponse cr = successResponse(envelope(
                successChannel("email", null, "openid-credential-offer://issuer"),
                successChannel("direct", "signed.credential.jwt", null)
        ));
        when(exchangeFunction.exchange(any())).thenReturn(Mono.just(cr));

        StepVerifier.create(service.forward(buildRequest(), "token", "id-token"))
                .assertNext(r -> {
                    assertThat(r.signedCredential()).isEqualTo("signed.credential.jwt");
                    assertThat(r.credentialOfferUri()).isEqualTo("openid-credential-offer://issuer");
                })
                .verifyComplete();
    }

    @Test
    void forward_directSucceedsEmailFails_returns207WithSignedCredentialOnly() {
        // Mixed outcome (EUD-167 D-5): 207 is 2xx-range, so the adapter still parses it as a body.
        ClientResponse cr = successResponse(envelope(
                successChannel("direct", "signed.credential.jwt", null),
                failedChannel("email", 503)
        ));
        when(exchangeFunction.exchange(any())).thenReturn(Mono.just(cr));

        StepVerifier.create(service.forward(buildRequest(), "token", "id-token"))
                .assertNext(r -> {
                    assertThat(r.signedCredential()).isEqualTo("signed.credential.jwt");
                    assertThat(r.credentialOfferUri()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void forward_directFailsEmailSucceeds_returnsNullSignedCredential() {
        // Direct itself failed inside a 207 mixed outcome: no signed credential to report, even though
        // the sibling channel completed. TranslateLegacyIssuanceWorkflow.handleLabelCredentialResponse
        // already treats a null/blank signedCredential as failure -- unchanged by this fix.
        ClientResponse cr = successResponse(envelope(
                failedChannel("direct", 503),
                successChannel("email", null, "openid-credential-offer://issuer")
        ));
        when(exchangeFunction.exchange(any())).thenReturn(Mono.just(cr));

        StepVerifier.create(service.forward(buildRequest(), "token", "id-token"))
                .assertNext(r -> {
                    assertThat(r.signedCredential()).isNull();
                    assertThat(r.credentialOfferUri()).isEqualTo("openid-credential-offer://issuer");
                })
                .verifyComplete();
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

    private IssuerChannelResponse failedChannel(String channel, int status) {
        return new IssuerChannelResponse(channel, status, null,
                new IssuerChannelError("about:blank", "Delivery failed", status, "Delivery failed for channel '" + channel + "'"));
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
                .schema("gx.labelcredential.w3c.1")
                .payload(JsonNodeFactory.instance.objectNode())
                .email("test@example.com")
                .delivery("email,direct")
                .build();
    }
}
