package es.altia.domeadapter.backend.issuance.domain.service.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Success payload of one {@link IssuerChannelResponse} (EUD-167 D-6): {@code signed_credential} for the
 * {@code direct} channel, {@code credential_offer_uri} for {@code ui}/{@code email}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IssuerChannelBody(
        @JsonProperty("signed_credential") String signedCredential,
        @JsonProperty("credential_offer_uri") String credentialOfferUri
) {
}
