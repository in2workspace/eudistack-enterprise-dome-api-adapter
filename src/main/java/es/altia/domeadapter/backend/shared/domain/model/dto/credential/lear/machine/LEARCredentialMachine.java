package es.altia.domeadapter.backend.shared.domain.model.dto.credential.lear.machine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import es.altia.domeadapter.backend.shared.domain.model.dto.credential.lear.Power;
import lombok.Builder;

import java.util.List;

@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record LEARCredentialMachine(
        @JsonProperty("credentialSubject") CredentialSubject credentialSubject
) {
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CredentialSubject(
            @JsonProperty("id") String id,
            @JsonProperty("mandate") Mandate mandate
    ) {
        @Builder
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Mandate(
                @JsonProperty("mandator") Mandator mandator,
                @JsonProperty("mandatee") Mandatee mandatee,
                @JsonProperty("power") List<Power> power
        ) {
            @Builder
            @JsonIgnoreProperties(ignoreUnknown = true)
            public record Mandator(
                    @JsonProperty("id") String id,
                    @JsonProperty("organization") String organization,
                    @JsonProperty("organizationIdentifier") String organizationIdentifier,
                    @JsonProperty("country") String country,
                    @JsonProperty("commonName") String commonName,
                    @JsonProperty("serialNumber") String serialNumber,
                    @JsonProperty("email") String email
            ) {}

            @Builder
            @JsonIgnoreProperties(ignoreUnknown = true)
            public record Mandatee(
                    @JsonProperty("id") String id,
                    @JsonProperty("domain") String domain,
                    @JsonProperty("ipAddress") String ipAddress
            ) {}
        }
    }
}
