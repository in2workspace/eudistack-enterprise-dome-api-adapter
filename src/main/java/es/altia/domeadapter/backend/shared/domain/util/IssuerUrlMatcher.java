package es.altia.domeadapter.backend.shared.domain.util;

import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Matches a token's {@code iss} claim against a configured base URL comparing only
 * the origin — scheme, host and port. A trailing slash or extra path segments on
 * either side are not a mismatch, so
 * {@code https://verifier.dome-marketplace-sbx.org} and
 * {@code https://verifier.dome-marketplace-sbx.org/verifier} are equivalent.
 *
 * <p>The scheme must still match: an {@code http} issuer never matches an
 * {@code https} configured URL. Default ports are normalised, so {@code https://host}
 * and {@code https://host:443} are equivalent.
 */
@Slf4j
public final class IssuerUrlMatcher {

    private static final int HTTPS_DEFAULT_PORT = 443;
    private static final int HTTP_DEFAULT_PORT = 80;

    private IssuerUrlMatcher() {
    }

    /**
     * @return {@code true} when both URLs share the same origin, ignoring path,
     *         query, fragment and trailing slashes.
     */
    public static boolean matchesOrigin(String issuer, String configuredUrl) {
        if (issuer == null || issuer.isBlank() || configuredUrl == null || configuredUrl.isBlank()) {
            return false;
        }

        URI issuerUri = parse(issuer);
        URI configuredUri = parse(configuredUrl);

        if (issuerUri == null || configuredUri == null) {
            return false;
        }

        String issuerHost = host(issuerUri);
        String configuredHost = host(configuredUri);

        if (issuerHost == null || configuredHost == null) {
            log.debug("Cannot compare issuer origin, missing host. iss={}, configured={}", issuer, configuredUrl);
            return false;
        }

        return issuerHost.equals(configuredHost)
                && scheme(issuerUri).equals(scheme(configuredUri))
                && effectivePort(issuerUri) == effectivePort(configuredUri);
    }

    private static URI parse(String value) {
        try {
            return new URI(value.trim());
        } catch (URISyntaxException e) {
            log.debug("Not a valid URI: {}", value);
            return null;
        }
    }

    private static String host(URI uri) {
        String host = uri.getHost();
        return host == null ? null : host.toLowerCase(Locale.ROOT);
    }

    private static String scheme(URI uri) {
        String scheme = uri.getScheme();
        return scheme == null ? "" : scheme.toLowerCase(Locale.ROOT);
    }

    private static int effectivePort(URI uri) {
        int port = uri.getPort();
        if (port != -1) {
            return port;
        }
        return switch (scheme(uri)) {
            case "https" -> HTTPS_DEFAULT_PORT;
            case "http" -> HTTP_DEFAULT_PORT;
            default -> -1;
        };
    }
}
