package es.altia.domeadapter.backend.issuance.domain.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.altia.domeadapter.backend.issuance.domain.service.IssuerCoreClientPort;
import es.altia.domeadapter.backend.shared.domain.model.dto.IssuerPreSubmittedCredentialDataRequest;
import es.altia.domeadapter.backend.shared.domain.model.dto.IssuanceResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

import static es.altia.domeadapter.backend.shared.domain.util.EndpointsConstants.ISSUANCES_PATH;

@Slf4j
@Service
@RequiredArgsConstructor
public class IssuerCoreClient implements IssuerCoreClientPort {

    private final WebClient issuerWebClient;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<IssuanceResponse> forward(IssuerPreSubmittedCredentialDataRequest request, String bearerToken, String idToken) {
        log.debug("[ISSUANCE] Sending issuance request. schema={}, delivery={}, email={}",
                request.schema(),
                request.delivery(),
                request.email());

        return issuerWebClient
                .post()
                .uri(ISSUANCES_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .header("X-ID-Token", idToken)
                .bodyValue(request)
                .exchangeToMono(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        return response.bodyToMono(IssuanceResponse.class);
                    }

                    return response.bodyToMono(byte[].class)
                            .defaultIfEmpty(new byte[0])
                            .flatMap(body -> Mono.error(
                                    WebClientResponseException.create(
                                            response.statusCode().value(),
                                            response.statusCode().toString(),
                                            response.headers().asHttpHeaders(),
                                            body,
                                            StandardCharsets.UTF_8
                                    )
                            ));
                });
    }
}