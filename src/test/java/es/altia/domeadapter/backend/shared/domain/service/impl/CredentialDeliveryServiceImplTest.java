package es.altia.domeadapter.backend.shared.domain.service.impl;

import es.altia.domeadapter.backend.shared.domain.exception.ResponseUriDeliveryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CredentialDeliveryServiceImplTest {

    private ExchangeFunction exchangeFunction;
    private CredentialDeliveryServiceImpl service;

    private static final String RESPONSE_URI = "http://example.com/endpoint";
    private static final String ENC_VC = "encoded-vc";
    private static final String CRED_ID = "cred-123";
    private static final String BEARER = "token-xyz";

    @BeforeEach
    void setup() {
        exchangeFunction = mock(ExchangeFunction.class);
        WebClient webClient = WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build();
        service = new CredentialDeliveryServiceImpl(webClient);
    }

    @Test
    void whenAccepted202_thenReturnAcceptedWithHtml() {
        ClientResponse clientResponse = mock(ClientResponse.class);
        when(clientResponse.statusCode()).thenReturn(HttpStatus.ACCEPTED);
        when(clientResponse.bodyToMono(String.class))
                .thenReturn(Mono.just("<html>missing docs</html>"));
        when(clientResponse.releaseBody()).thenReturn(Mono.empty());
        when(exchangeFunction.exchange(any()))
                .thenReturn(Mono.just(clientResponse));

        StepVerifier.create(service.deliverLabelToResponseUri(RESPONSE_URI, ENC_VC, CRED_ID, BEARER))
                .assertNext(result -> {
                    assertTrue(result.acceptedWithHtml());
                    assertEquals("<html>missing docs</html>", result.html());
                })
                .verifyComplete();
    }

    @Test
    void whenOk200_thenReturnSuccess() {
        ClientResponse clientResponse = ClientResponse
                .create(HttpStatus.OK)
                .build();
        when(exchangeFunction.exchange(any()))
                .thenReturn(Mono.just(clientResponse));

        StepVerifier.create(service.deliverLabelToResponseUri(RESPONSE_URI, ENC_VC, CRED_ID, BEARER))
                .assertNext(result -> {
                    assertFalse(result.acceptedWithHtml());
                    assertNull(result.html());
                })
                .verifyComplete();
    }

    @Test
    void whenErrorStatus_thenPropagateResponseUriDeliveryException() {
        ClientResponse clientResponse = mock(ClientResponse.class);
        when(clientResponse.statusCode()).thenReturn(HttpStatus.BAD_REQUEST);
        when(clientResponse.releaseBody()).thenReturn(Mono.empty());
        when(exchangeFunction.exchange(any()))
                .thenReturn(Mono.just(clientResponse));

        StepVerifier.create(service.deliverLabelToResponseUri(RESPONSE_URI, ENC_VC, CRED_ID, BEARER))
                .expectErrorSatisfies(error -> {
                    assertInstanceOf(ResponseUriDeliveryException.class, error);
                    ResponseUriDeliveryException ex = (ResponseUriDeliveryException) error;
                    assertEquals(400, ex.getHttpStatusCode());
                    assertEquals(RESPONSE_URI, ex.getResponseUri());
                    assertEquals(CRED_ID, ex.getCredentialId());
                })
                .verify();
    }

    @Test
    void whenAccepted202WithEmptyBody_thenReturnSuccess() {
        ClientResponse clientResponse = mock(ClientResponse.class);
        when(clientResponse.statusCode()).thenReturn(HttpStatus.ACCEPTED);
        when(clientResponse.bodyToMono(String.class)).thenReturn(Mono.empty());
        when(clientResponse.releaseBody()).thenReturn(Mono.empty());
        when(exchangeFunction.exchange(any())).thenReturn(Mono.just(clientResponse));

        StepVerifier.create(service.deliverLabelToResponseUri(RESPONSE_URI, ENC_VC, CRED_ID, BEARER))
                .assertNext(result -> {
                    assertThat(result.acceptedWithHtml()).isFalse();
                    assertThat(result.html()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void whenNetworkError_thenPropagateException() {
        when(exchangeFunction.exchange(any()))
                .thenReturn(Mono.error(
                        new WebClientRequestException(
                                new IOException("network error"),
                                HttpMethod.PATCH,
                                URI.create(RESPONSE_URI),
                                new HttpHeaders()
                        )
                ));

        StepVerifier.create(service.deliverLabelToResponseUri(RESPONSE_URI, ENC_VC, CRED_ID, BEARER))
                .expectError(WebClientRequestException.class)
                .verify();
    }
}