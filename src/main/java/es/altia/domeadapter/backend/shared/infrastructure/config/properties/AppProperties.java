package es.altia.domeadapter.backend.shared.infrastructure.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app")
@Validated
public record AppProperties(
        @NotBlank @URL String verifierUrl,
        @NotBlank @URL String issuerUrl,
        @NotBlank String defaultLang,
        @NotBlank String configSource,
        boolean issuerDomeAdapterEnabled,
        @Valid Mail mail
) {
    public record Mail(
            @NotBlank @Email String from
    ) {
    }
}