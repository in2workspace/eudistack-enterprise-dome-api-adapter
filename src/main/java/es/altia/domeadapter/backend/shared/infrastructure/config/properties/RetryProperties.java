package es.altia.domeadapter.backend.shared.infrastructure.config.properties;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "retry")
@Validated
public record RetryProperties(
        @NotNull LabelUpload labelUpload
) {
    @ConstructorBinding
    public RetryProperties(LabelUpload labelUpload) {
        this.labelUpload   = labelUpload;
    }

    @Validated
    public record LabelUpload(
            @NotBlank @Email String certifierEmail,
            @NotBlank @Email String marketplaceEmail
    ) {
    }
}
