package es.altia.domeadapter.backend.issuance.domain.service;

import es.altia.domeadapter.backend.shared.domain.model.dto.IssuerPreSubmittedCredentialDataRequest;
import es.altia.domeadapter.backend.shared.domain.model.dto.IssuanceResponse;
import reactor.core.publisher.Mono;

public interface IssuerCoreClientPort {

    Mono<IssuanceResponse> forward(IssuerPreSubmittedCredentialDataRequest request, String bearerToken, String idToken);
}