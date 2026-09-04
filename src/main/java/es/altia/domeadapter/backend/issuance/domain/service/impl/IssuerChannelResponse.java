package es.altia.domeadapter.backend.issuance.domain.service.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One item of the Issuer's {@code responses[]} (EUD-167 D-5/D-6): the requested channel and either
 * {@code body} (success) or {@code error} (failure, RFC 9457) -- never both.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IssuerChannelResponse(
        @JsonProperty("channel") String channel,
        @JsonProperty("status") int status,
        @JsonProperty("body") IssuerChannelBody body,
        @JsonProperty("error") IssuerChannelError error
) {
}
