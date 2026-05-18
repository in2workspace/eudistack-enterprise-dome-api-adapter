package es.altia.domeadapter.backend.shared.infrastructure.config;

import es.altia.domeadapter.backend.shared.infrastructure.config.adapter.ConfigAdapter;
import es.altia.domeadapter.backend.shared.infrastructure.config.adapter.factory.ConfigAdapterFactory;
import es.altia.domeadapter.backend.shared.infrastructure.config.properties.AppProperties;
import es.altia.domeadapter.backend.shared.infrastructure.config.properties.CorsProperties;
import es.altia.domeadapter.backend.shared.infrastructure.config.properties.AdapterIdentityProperties;
import es.altia.domeadapter.backend.shared.infrastructure.config.properties.RetryProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class AppConfig {

    private final ConfigAdapter configAdapter;
    private final AppProperties appProperties;
    private final AdapterIdentityProperties adapterIdentityProperties;
    private final RetryProperties retryProperties;
    private final CorsProperties corsProperties;

    public AppConfig(
            ConfigAdapterFactory configAdapterFactory,
            AppProperties appProperties,
            AdapterIdentityProperties adapterIdentityProperties,
            RetryProperties retryProperties,
            CorsProperties corsProperties
    ) {
        this.configAdapter              = configAdapterFactory.getAdapter();
        this.appProperties              = appProperties;
        this.adapterIdentityProperties   = adapterIdentityProperties;
        this.retryProperties            = retryProperties;
        this.corsProperties             = corsProperties;
    }

    public String getVerifierUrl() {
        return configAdapter.getConfiguration(appProperties.verifierUrl());
    }

    public String getMailFrom() {
        return configAdapter.getConfiguration(appProperties.mail().from());
    }

    /** URL of the external issuer — used to forward requests and to validate issuer-signed tokens. */
    public String getIssuerUrl() {
        return configAdapter.getConfiguration(appProperties.issuerUrl());
    }

    public String getDefaultLang() {
        return configAdapter.getConfiguration(appProperties.defaultLang());
    }

    public String getConfigSource() {
        return configAdapter.getConfiguration(appProperties.configSource());
    }

    public String getCredentialSubjectDidKey() {
        return adapterIdentityProperties.credentialSubjectDidKey();
    }

    public String getJwtCredential() {
        return adapterIdentityProperties.jwtCredential();
    }

    public String getCryptoPrivateKey() {
        return adapterIdentityProperties.crypto().privateKey();
    }

    public String getLabelUploadCertifierEmail() {
        return retryProperties.labelUpload().certifierEmail();
    }

    public String getLabelUploadMarketplaceEmail() {
        return retryProperties.labelUpload().marketplaceEmail();
    }

    public boolean isIssuerDomeAdapterEnabled() {
        return appProperties.issuerDomeAdapterEnabled();
    }

    public List<String> getExternalCorsAllowedOrigins() {
        return corsProperties.externalAllowedOrigins();
    }
}
