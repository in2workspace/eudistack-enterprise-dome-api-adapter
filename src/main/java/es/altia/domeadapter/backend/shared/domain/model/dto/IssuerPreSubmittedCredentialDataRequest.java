package es.altia.domeadapter.backend.shared.domain.model.dto;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record IssuerPreSubmittedCredentialDataRequest(
        @JsonProperty(value = "schema", required = true) String schema,
        @JsonProperty(value = "payload", required = true) JsonNode payload,
        @JsonProperty("operation_mode") String operationMode,
        @NotBlank(message = "email is required")
        @JsonProperty("email") String email,
        @JsonProperty("delivery") String delivery
) {
        public IssuerPreSubmittedCredentialDataRequest {
                if (delivery == null || delivery.isBlank()) {
                        delivery = "email,direct";
                }
        }
}

