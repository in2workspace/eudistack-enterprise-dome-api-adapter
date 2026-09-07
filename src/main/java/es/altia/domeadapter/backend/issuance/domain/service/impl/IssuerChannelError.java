package es.altia.domeadapter.backend.issuance.domain.service.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * RFC 9457 Problem Details for one failed {@link IssuerChannelResponse} (EUD-167 D-5/D-6).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IssuerChannelError(
        @JsonProperty("type") String type,
        @JsonProperty("title") String title,
        @JsonProperty("status") int status,
        @JsonProperty("detail") String detail
) {
}
