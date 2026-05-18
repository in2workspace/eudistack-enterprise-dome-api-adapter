package es.altia.domeadapter.backend.issuance.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import es.altia.domeadapter.backend.issuance.domain.service.impl.IssuerCoreClient;
import es.altia.domeadapter.backend.shared.domain.model.dto.IssuerPreSubmittedCredentialDataRequest;
import es.altia.domeadapter.backend.shared.domain.model.dto.IssuanceResponse;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalIssuanceServiceTest {

    @Mock
    private ExchangeFunction exchangeFunction;

    private IssuerCoreClient service;

    @BeforeEach
    void setUp() {
        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
        service = new IssuerCoreClient(webClient, new ObjectMapper());
    }

    @Test
    void forward_2xxResponse_returnsIssuanceResponse() {
        IssuanceResponse expected = IssuanceResponse.builder().signedCredential("signed.credential.jwt").build();
        ClientResponse cr = successResponse(expected);
        when(exchangeFunction.exchange(any())).thenReturn(Mono.just(cr));

        StepVerifier.create(service.forward(buildRequest(), "token", "id-token"))
                .assertNext(r -> assertThat(r.signedCredential()).isEqualTo("signed.credential.jwt"))
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
        ClientResponse cr = errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "");
        when(exchangeFunction.exchange(any())).thenReturn(Mono.just(cr));

        StepVerifier.create(service.forward(buildRequest(), "token", "id-token"))
                .expectError(WebClientResponseException.class)
                .verify();
    }

    @Test
    void forward_sendsAuthorizationAndIdTokenHeaders() {
        ArgumentCaptor<ClientRequest> captor = ArgumentCaptor.forClass(ClientRequest.class);
        ClientResponse cr = successResponse(IssuanceResponse.builder().build());
        when(exchangeFunction.exchange(captor.capture())).thenReturn(Mono.just(cr));

        service.forward(buildRequest(), "my-bearer-token", "my-id-token").block();

        ClientRequest req = captor.getValue();
        assertThat(req.headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer my-bearer-token");
        assertThat(req.headers().getFirst("X-ID-Token")).isEqualTo("my-id-token");
    }

    @Test
    void forward_postsToIssuancesPath() {
        ArgumentCaptor<ClientRequest> captor = ArgumentCaptor.forClass(ClientRequest.class);
        ClientResponse cr = successResponse(IssuanceResponse.builder().build());
        when(exchangeFunction.exchange(captor.capture())).thenReturn(Mono.just(cr));

        service.forward(buildRequest(), "token", "id-token").block();

        assertThat(captor.getValue().url().getPath()).isEqualTo("/api/v1/issuances");
    }

    private ClientResponse successResponse(IssuanceResponse body) {
        ClientResponse cr = mock(ClientResponse.class);
        when(cr.statusCode()).thenReturn(HttpStatus.OK);
        when(cr.bodyToMono(IssuanceResponse.class)).thenReturn(Mono.just(body));
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
