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
import java.util.List;

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
                        return response.bodyToMono(IssuerHttpResponse.class).map(this::toIssuanceResponse);
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

    // Translates the Issuer's per-channel envelope (EUD-167 D-5/D-6) into the adapter's own flat
    // IssuanceResponse. That shape is not just an internal deserialization target: LegacyIssuanceController
    // serializes it verbatim as the adapter's v2.x contract towards DOME, so it cannot change to mirror
    // responses[] -- only this translation step may know the envelope exists (EUD-167/spec-deltas.md D-7).
    private IssuanceResponse toIssuanceResponse(IssuerHttpResponse httpResponse) {
        List<IssuerChannelResponse> channels = httpResponse.responses();
        if (channels == null) {
            return IssuanceResponse.builder().build();
        }

        String signedCredential = channels.stream()
                .filter(c -> "direct".equals(c.channel()) && c.body() != null)
                .map(c -> c.body().signedCredential())
                .findFirst()
                .orElse(null);

        String credentialOfferUri = channels.stream()
                .filter(c -> c.body() != null && c.body().credentialOfferUri() != null)
                .map(c -> c.body().credentialOfferUri())
                .findFirst()
                .orElse(null);

        return IssuanceResponse.builder()
                .signedCredential(signedCredential)
                .credentialOfferUri(credentialOfferUri)
                .build();
    }
}