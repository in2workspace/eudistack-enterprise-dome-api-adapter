package es.altia.domeadapter.backend.shared.domain.model.dto.credential.lear.employee;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import es.altia.domeadapter.backend.shared.domain.model.dto.credential.lear.Power;
import lombok.Builder;

import java.util.List;

@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record LEARCredentialEmployee(
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
                @JsonProperty("mandatee") Mandatee mandatee,
                @JsonProperty("mandator") Mandator mandator,
                @JsonProperty("power") List<Power> power
        ) {
            @Builder
            @JsonIgnoreProperties(ignoreUnknown = true)
            public record Mandatee(
                    @JsonProperty("id") String id,
                    @JsonProperty("employeeId") String employeeId,
                    @JsonProperty("email") String email,
                    @JsonProperty("firstName") @JsonAlias("first_name") String firstName,
                    @JsonProperty("lastName") @JsonAlias("last_name") String lastName
            ) {}

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
        }
    }
}
