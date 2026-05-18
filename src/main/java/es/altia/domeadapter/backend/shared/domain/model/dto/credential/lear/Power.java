package es.altia.domeadapter.backend.shared.domain.model.dto.credential.lear;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;

@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record Power(
        @JsonProperty("type") String type,
        @JsonProperty("domain") String domain,
        @JsonProperty("function") String function,
        @JsonProperty("action") JsonNode action
) {}
