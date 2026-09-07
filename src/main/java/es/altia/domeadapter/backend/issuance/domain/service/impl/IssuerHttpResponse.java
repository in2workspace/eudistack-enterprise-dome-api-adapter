package es.altia.domeadapter.backend.issuance.domain.service.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Wire shape of the Issuer's {@code POST /api/v1/issuances} response since EUD-167 D-5/D-6: one
 * {@link IssuerChannelResponse} per requested delivery channel. Internal to {@link IssuerCoreClient} --
 * not the adapter's own legacy v2.x contract, which stays {@code IssuanceResponse} (flat).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IssuerHttpResponse(
        @JsonProperty("responses") List<IssuerChannelResponse> responses
) {
}
