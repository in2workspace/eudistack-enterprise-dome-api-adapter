package es.altia.domeadapter.backend.shared.infrastructure.security;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.SignedJWT;
import es.altia.domeadapter.backend.shared.domain.service.JWTService;
import es.altia.domeadapter.backend.shared.domain.service.VerifierService;
import es.altia.domeadapter.backend.shared.infrastructure.config.AppConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomAuthenticationManagerTest {

    private static final String VERIFIER_URL = "https://verifier.example.com";
    private static final String EXTERNAL_ISSUER_URL = "https://issuer.example.com";

    @Mock
    private VerifierService verifierService;

    @Mock
    private AppConfig appConfig;

    @Mock
    private JWTService jwtService;

    private CustomAuthenticationManager authenticationManager;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();

        authenticationManager = new CustomAuthenticationManager(
                verifierService,
                objectMapper,
                appConfig,
                jwtService
        );

        lenient().when(appConfig.getVerifierUrl()).thenReturn(VERIFIER_URL);
        lenient().when(appConfig.getIssuerUrl()).thenReturn(EXTERNAL_ISSUER_URL);
    }

    @Test
    void authenticateShouldReturnJwtAuthenticationTokenWhenVerifierTokenIsValid() {
        String principal = "did:example:machine-1";
        String accessToken = buildJwt(Map.of(
                "iss", VERIFIER_URL,
                "sub", principal,
                "iat", Instant.now().minusSeconds(60).getEpochSecond(),
                "exp", Instant.now().plusSeconds(3600).getEpochSecond(),
                "vc", Map.of(
                        "type", new String[]{
                                "VerifiableCredential",
                                "LEARCredentialMachine"
                        }
                )
        ));

        Authentication authentication = new TestingAuthenticationToken("ignored", accessToken);

        when(verifierService.verifyToken(accessToken)).thenReturn(Mono.empty());
        when(jwtService.resolvePrincipal(any(Jwt.class))).thenReturn(principal);

        Mono<Authentication> result = authenticationManager.authenticate(authentication);

        StepVerifier.create(result)
                .assertNext(auth -> {
                    assertThat(auth).isInstanceOf(JwtAuthenticationToken.class);
                    assertThat(auth.getName()).isEqualTo(principal);
                    assertThat(auth.getAuthorities()).isEmpty();

                    Jwt jwt = ((JwtAuthenticationToken) auth).getToken();

                    assertThat(jwt.getTokenValue()).isEqualTo(accessToken);
                    assertThat(jwt.getIssuer().toString()).isEqualTo(VERIFIER_URL);
                    assertThat(jwt.getClaimAsString("sub")).isEqualTo(principal);
                    assertThat(jwt.getClaims()).containsKey("vc");
                })
                .verifyComplete();

        verify(verifierService).verifyToken(accessToken);
        verify(jwtService).resolvePrincipal(any(Jwt.class));
    }

    @Test
    void authenticateShouldAcceptVerifierTokenWhenVcTypeStartsWithLearCredentialMachineW3c() {
        String principal = "did:example:machine-1";
        String accessToken = buildJwt(Map.of(
                "iss", VERIFIER_URL,
                "sub", principal,
                "vc", Map.of(
                        "type", new String[]{
                                "VerifiableCredential",
                                "learcredential.machine.w3c.1"
                        }
                )
        ));

        Authentication authentication = new TestingAuthenticationToken("ignored", accessToken);

        when(verifierService.verifyToken(accessToken)).thenReturn(Mono.empty());
        when(jwtService.resolvePrincipal(any(Jwt.class))).thenReturn(principal);

        Mono<Authentication> result = authenticationManager.authenticate(authentication);

        StepVerifier.create(result)
                .assertNext(auth -> assertThat(auth.getName()).isEqualTo(principal))
                .verifyComplete();
    }

    @Test
    void authenticateShouldFailWhenVerifierTokenHasNeitherVcNorCredentialType() {
        String accessToken = buildJwt(Map.of(
                "iss", VERIFIER_URL,
                "sub", "did:example:machine-1"
        ));

        Authentication authentication = new TestingAuthenticationToken("ignored", accessToken);

        when(verifierService.verifyToken(accessToken)).thenReturn(Mono.empty());

        Mono<Authentication> result = authenticationManager.authenticate(authentication);

        StepVerifier.create(result)
                .expectErrorSatisfies(error -> {
                    assertThat(error)
                            .isInstanceOf(BadCredentialsException.class)
                            .hasMessage("Credential type required: LEARCredentialMachine or learcredential.machine.w3c.");
                })
                .verify();
    }

    @Test
    void authenticateShouldAcceptVerifierTokenWithCredentialTypeLearCredentialMachine() {
        String principal = "did:example:machine-2";
        String accessToken = buildJwt(Map.of(
                "iss", VERIFIER_URL,
                "sub", principal,
                "credential_type", "LEARCredentialMachine"
        ));

        Authentication authentication = new TestingAuthenticationToken("ignored", accessToken);

        when(verifierService.verifyToken(accessToken)).thenReturn(Mono.empty());
        when(jwtService.resolvePrincipal(any(Jwt.class))).thenReturn(principal);

        StepVerifier.create(authenticationManager.authenticate(authentication))
                .assertNext(auth -> assertThat(auth.getName()).isEqualTo(principal))
                .verifyComplete();
    }

    @Test
    void authenticateShouldAcceptVerifierTokenWithVersionedLearCredentialMachineW3cType() {
        String principal = "did:example:machine-3";
        String accessToken = buildJwt(Map.of(
                "iss", VERIFIER_URL,
                "sub", principal,
                "credential_type", "learcredential.machine.w3c.3"
        ));

        Authentication authentication = new TestingAuthenticationToken("ignored", accessToken);

        when(verifierService.verifyToken(accessToken)).thenReturn(Mono.empty());
        when(jwtService.resolvePrincipal(any(Jwt.class))).thenReturn(principal);

        StepVerifier.create(authenticationManager.authenticate(authentication))
                .assertNext(auth -> assertThat(auth.getName()).isEqualTo(principal))
                .verifyComplete();
    }

    @Test
    void authenticateShouldFailWhenVerifierTokenHasUnrecognizedCredentialType() {
        String accessToken = buildJwt(Map.of(
                "iss", VERIFIER_URL,
                "sub", "did:example:machine-1",
                "credential_type", "UnknownCredentialType"
        ));

        Authentication authentication = new TestingAuthenticationToken("ignored", accessToken);

        when(verifierService.verifyToken(accessToken)).thenReturn(Mono.empty());

        StepVerifier.create(authenticationManager.authenticate(authentication))
                .expectErrorSatisfies(error -> {
                    assertThat(error)
                            .isInstanceOf(BadCredentialsException.class)
                            .hasMessage("Credential type required: LEARCredentialMachine or learcredential.machine.w3c.");
                })
                .verify();
    }

    @Test
    void authenticateShouldFailWhenVerifierTokenContainsInvalidVcType() {
        String accessToken = buildJwt(Map.of(
                "iss", VERIFIER_URL,
                "sub", "did:example:machine-1",
                "vc", Map.of(
                        "type", new String[]{
                                "VerifiableCredential",
                                "OtherCredential"
                        }
                )
        ));

        Authentication authentication = new TestingAuthenticationToken("ignored", accessToken);

        when(verifierService.verifyToken(accessToken)).thenReturn(Mono.empty());

        Mono<Authentication> result = authenticationManager.authenticate(authentication);

        StepVerifier.create(result)
                .expectErrorSatisfies(error -> {
                    assertThat(error)
                            .isInstanceOf(BadCredentialsException.class)
                            .hasMessage("Credential type required: LEARCredentialMachine or learcredential.machine.w3c.*");
                })
                .verify();
    }

    @Test
    void authenticateShouldFailWhenVerifierServiceRejectsToken() {
        String accessToken = buildJwt(Map.of(
                "iss", VERIFIER_URL,
                "sub", "did:example:machine-1",
                "vc", Map.of(
                        "type", new String[]{
                                "LEARCredentialMachine"
                        }
                )
        ));

        Authentication authentication = new TestingAuthenticationToken("ignored", accessToken);

        when(verifierService.verifyToken(accessToken))
                .thenReturn(Mono.error(new BadCredentialsException("Verifier rejected token")));

        Mono<Authentication> result = authenticationManager.authenticate(authentication);

        StepVerifier.create(result)
                .expectErrorSatisfies(error -> {
                    assertThat(error)
                            .isInstanceOf(BadCredentialsException.class)
                            .hasMessage("Verifier rejected token");
                })
                .verify();
    }

    @Test
    void authenticateShouldReturnJwtAuthenticationTokenWhenExternalIssuerTokenHasValidSignature() {
        String principal = "user@example.com";
        String accessToken = buildJwt(Map.of(
                "iss", EXTERNAL_ISSUER_URL,
                "sub", principal,
                "iat", Instant.now().minusSeconds(60).getEpochSecond(),
                "exp", Instant.now().plusSeconds(3600).getEpochSecond()
        ));

        Authentication authentication = new TestingAuthenticationToken("ignored", accessToken);

        when(jwtService.validateJwtSignatureReactive(any(SignedJWT.class))).thenReturn(Mono.just(true));
        when(jwtService.resolvePrincipal(any(Jwt.class))).thenReturn(principal);

        Mono<Authentication> result = authenticationManager.authenticate(authentication);

        StepVerifier.create(result)
                .assertNext(auth -> {
                    assertThat(auth).isInstanceOf(JwtAuthenticationToken.class);
                    assertThat(auth.getName()).isEqualTo(principal);

                    Jwt jwt = ((JwtAuthenticationToken) auth).getToken();

                    assertThat(jwt.getTokenValue()).isEqualTo(accessToken);
                    assertThat(jwt.getIssuer().toString()).isEqualTo(EXTERNAL_ISSUER_URL);
                    assertThat(jwt.getClaimAsString("sub")).isEqualTo(principal);
                })
                .verifyComplete();

        verify(jwtService).validateJwtSignatureReactive(any(SignedJWT.class));
        verify(jwtService).resolvePrincipal(any(Jwt.class));
    }

    @Test
    void authenticateShouldFailWhenExternalIssuerTokenHasInvalidSignature() {
        String accessToken = buildJwt(Map.of(
                "iss", EXTERNAL_ISSUER_URL,
                "sub", "user@example.com"
        ));

        Authentication authentication = new TestingAuthenticationToken("ignored", accessToken);

        when(jwtService.validateJwtSignatureReactive(any(SignedJWT.class))).thenReturn(Mono.just(false));

        Mono<Authentication> result = authenticationManager.authenticate(authentication);

        StepVerifier.create(result)
                .expectErrorSatisfies(error -> {
                    assertThat(error)
                            .isInstanceOf(BadCredentialsException.class)
                            .hasMessage("Invalid JWT signature");
                })
                .verify();
    }

    @Test
    void authenticateShouldFailWhenIssuerIsUnknown() {
        String accessToken = buildJwt(Map.of(
                "iss", "https://unknown-issuer.example.com",
                "sub", "user@example.com"
        ));

        Authentication authentication = new TestingAuthenticationToken("ignored", accessToken);

        Mono<Authentication> result = authenticationManager.authenticate(authentication);

        StepVerifier.create(result)
                .expectErrorSatisfies(error -> {
                    assertThat(error)
                            .isInstanceOf(BadCredentialsException.class)
                            .hasMessage("Unknown token issuer: https://unknown-issuer.example.com");
                })
                .verify();
    }

    @Test
    void authenticateShouldFailWhenIssuerClaimIsMissing() {
        String accessToken = buildJwt(Map.of(
                "sub", "user@example.com"
        ));

        Authentication authentication = new TestingAuthenticationToken("ignored", accessToken);

        Mono<Authentication> result = authenticationManager.authenticate(authentication);

        StepVerifier.create(result)
                .expectErrorSatisfies(error -> {
                    assertThat(error)
                            .isInstanceOf(BadCredentialsException.class)
                            .hasMessage("Missing issuer (iss) claim");
                })
                .verify();
    }

    @Test
    void authenticateShouldFailWhenJwtFormatIsInvalid() {
        Authentication authentication = new TestingAuthenticationToken("ignored", "invalid-token");

        Mono<Authentication> result = authenticationManager.authenticate(authentication);

        StepVerifier.create(result)
                .expectErrorSatisfies(error -> {
                    assertThat(error)
                            .isInstanceOf(BadCredentialsException.class)
                            .hasMessage("Invalid JWT token format");
                })
                .verify();
    }

    private static String buildJwt(Map<String, Object> claims) {
        Map<String, Object> header = Map.of(
                "alg", "RS256",
                "typ", "JWT"
        );

        try {
            ObjectMapper objectMapper = new ObjectMapper();

            return base64UrlEncode(objectMapper.writeValueAsString(header))
                    + "."
                    + base64UrlEncode(objectMapper.writeValueAsString(claims))
                    + "."
                    + base64UrlEncode("signature");
        } catch (Exception e) {
            throw new IllegalStateException("Error building test JWT", e);
        }
    }

    private static String base64UrlEncode(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
