package es.altia.domeadapter.backend.shared.domain.util;

public class EndpointsConstants {

    private EndpointsConstants() {
        throw new IllegalStateException("Utility class");
    }

    public static final String WELL_KNOWN_BASE_PATH                             = "/.well-known";
    public static final String AUTHORIZATION_SERVER_METADATA_WELL_KNOWN_PATH    = WELL_KNOWN_BASE_PATH + "/openid-configuration";

    public static final String TRANSLATE_LEGACY_PATH       = "/vci/v1/issuances";
    public static final String ISSUANCES_PATH       = "/api/v1/issuances";
    public static final String HEALTH_PATH          = "/health";
    public static final String PROMETHEUS_PATH      = "/prometheus";
    public static final String SPRINGDOC_BASE_PATH  = "/springdoc";
    public static final String SPRINGDOC_PATH       = SPRINGDOC_BASE_PATH + "/**";
}
