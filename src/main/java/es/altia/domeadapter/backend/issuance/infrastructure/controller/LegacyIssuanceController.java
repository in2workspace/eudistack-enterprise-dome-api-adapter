package es.altia.domeadapter.backend.issuance.infrastructure.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.altia.domeadapter.backend.issuance.application.TranslateLegacyIssuanceWorkflow;
import es.altia.domeadapter.backend.shared.domain.model.dto.PreSubmittedCredentialDataRequest;
import es.altia.domeadapter.backend.shared.infrastructure.config.AppConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import static es.altia.domeadapter.backend.shared.domain.util.EndpointsConstants.TRANSLATE_LEGACY_PATH;

@Slf4j
@RestController
@RequestMapping
@RequiredArgsConstructor
public class LegacyIssuanceController {

    private final TranslateLegacyIssuanceWorkflow translateLegacyIssuanceWorkflow;
    private final AppConfig appConfig;
    private final ObjectMapper objectMapper;

    @PostMapping(
            value = TRANSLATE_LEGACY_PATH,
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public Mono<ResponseEntity<byte[]>> issueCredential(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken,
            @RequestHeader(value = "X-ID-Token", required = false) String idToken,
            @RequestBody PreSubmittedCredentialDataRequest request
    ) {
        if (!appConfig.isIssuerDomeAdapterEnabled()) {
            return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"message\":\"Endpoint not found\"}".getBytes()));
        }

        String token = bearerToken.startsWith("Bearer ") ? bearerToken.substring(7) : bearerToken;
        log.info("[ISSUANCE] Received issuance request, schema={} format={}", request.schema(), request.format());

        return translateLegacyIssuanceWorkflow.execute(request, token, idToken)
                .flatMap(response -> Mono.fromCallable(() -> ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(objectMapper.writeValueAsBytes(response))))
                .onErrorResume(WebClientResponseException.class, ex -> {
                    log.warn("[ISSUANCE] Issuer returned error {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
                    // Propagate the issuer's status code and body verbatim so the caller
                    // receives the same error the issuer produced, not a generic 500.
                    return Mono.just(ResponseEntity
                            .status(ex.getStatusCode())
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(ex.getResponseBodyAsByteArray()));
                });
    }
}
