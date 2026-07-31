package es.altia.domeadapter.backend.shared.domain.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class IssuerUrlMatcherTest {

    private static final String CONFIGURED_URL = "https://verifier.dome-marketplace-sbx.org";

    @ParameterizedTest
    @ValueSource(strings = {
            "https://verifier.dome-marketplace-sbx.org",
            "https://verifier.dome-marketplace-sbx.org/",
            "https://verifier.dome-marketplace-sbx.org/verifier",
            "https://verifier.dome-marketplace-sbx.org/verifier/",
            "https://verifier.dome-marketplace-sbx.org/verifier/oidc",
            "https://verifier.dome-marketplace-sbx.org:443/verifier",
            "https://VERIFIER.DOME-MARKETPLACE-SBX.ORG/verifier",
            "  https://verifier.dome-marketplace-sbx.org/verifier  ",
            "https://verifier.dome-marketplace-sbx.org/verifier?foo=bar",
            "https://verifier.dome-marketplace-sbx.org/verifier#frag"
    })
    void shouldMatchWhenOriginIsTheSame(String issuer) {
        assertThat(IssuerUrlMatcher.matchesOrigin(issuer, CONFIGURED_URL)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://verifier.dome-marketplace-prd.org/verifier",
            "https://attacker.org/verifier.dome-marketplace-sbx.org",
            "https://verifier.dome-marketplace-sbx.org.attacker.org",
            "http://verifier.dome-marketplace-sbx.org/verifier",
            "https://verifier.dome-marketplace-sbx.org:8443/verifier",
            "did:elsi:VATES-B12345678",
            "not a url",
            ""
    })
    void shouldNotMatchWhenOriginDiffers(String issuer) {
        assertThat(IssuerUrlMatcher.matchesOrigin(issuer, CONFIGURED_URL)).isFalse();
    }

    @ParameterizedTest
    @NullSource
    void shouldNotMatchWhenIssuerIsNull(String issuer) {
        assertThat(IssuerUrlMatcher.matchesOrigin(issuer, CONFIGURED_URL)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "https://verifier.example.org, ''",
            "https://verifier.example.org, '   '"
    })
    void shouldNotMatchWhenConfiguredUrlIsNotSet(String issuer, String configuredUrl) {
        assertThat(IssuerUrlMatcher.matchesOrigin(issuer, configuredUrl)).isFalse();
    }

    @Test
    void shouldNotMatchWhenConfiguredUrlIsNull() {
        assertThat(IssuerUrlMatcher.matchesOrigin("https://verifier.example.org", null)).isFalse();
    }

    @Test
    void shouldMatchWhenConfiguredUrlAlsoCarriesAPath() {
        assertThat(IssuerUrlMatcher.matchesOrigin(
                "https://verifier.dome-marketplace-sbx.org",
                "https://verifier.dome-marketplace-sbx.org/verifier"))
                .isTrue();
    }

    @Test
    void shouldMatchWhenBothSidesCarryDifferentPaths() {
        assertThat(IssuerUrlMatcher.matchesOrigin(
                "https://verifier.dome-marketplace-sbx.org/verifier",
                "https://verifier.dome-marketplace-sbx.org/oidc"))
                .isTrue();
    }
}
