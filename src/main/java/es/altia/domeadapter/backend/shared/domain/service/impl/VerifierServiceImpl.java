package es.altia.domeadapter.backend.shared.domain.service.impl;

import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import es.altia.domeadapter.backend.shared.domain.exception.JWTParsingException;
import es.altia.domeadapter.backend.shared.domain.exception.JWTVerificationException;
import es.altia.domeadapter.backend.shared.domain.exception.TokenFetchException;
import es.altia.domeadapter.backend.shared.domain.exception.WellKnownInfoFetchException;
import es.altia.domeadapter.backend.shared.domain.model.dto.OpenIDProviderMetadata;
import es.altia.domeadapter.backend.shared.domain.model.dto.VerifierOauth2AccessToken;
import es.altia.domeadapter.backend.shared.domain.service.VerifierService;
import es.altia.domeadapter.backend.shared.infrastructure.config.AppConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.text.ParseException;
import java.util.Date;

import static es.altia.domeadapter.backend.shared.domain.util.Constants.CONTENT_TYPE;
import static es.altia.domeadapter.backend.shared.domain.util.Constants.CONTENT_TYPE_URL_ENCODED_FORM;
import static es.altia.domeadapter.backend.shared.domain.util.EndpointsConstants.AUTHORIZATION_SERVER_METADATA_WELL_KNOWN_PATH;

@Service
@Slf4j
@RequiredArgsConstructor
public class VerifierServiceImpl implements VerifierService {

    private final AppConfig appConfig;
    private final WebClient oauth2VerifierWebClient;

    private JWKSet cachedJWKSet;
    private final Object jwkLock = new Object();

    @Override
    public Mono<Void> verifyToken(String accessToken) {
        return parseAndValidateJwt(accessToken, true)
                .doOnSuccess(unused -> log.info("The verification of the token is valid"))
                .onErrorResume(e -> {
                    log.error("Error while verifying token", e);
                    return Mono.error(e);
                });
    }

    @Override
    public Mono<Void> verifyTokenWithoutExpiration(String accessToken) {
        return parseAndValidateJwt(accessToken, false)
                .doOnSuccess(unused -> log.info("The verification of the token without expiration is valid"))
                .onErrorResume(e -> {
                    log.error("Error while verifying token (without expiration)", e);
                    return Mono.error(e);
                });
    }

    private Mono<Void> parseAndValidateJwt(String accessToken, boolean checkExpiration) {
        return getWellKnownInfo()
                .flatMap(metadata -> fetchJWKSet(metadata.jwksUri()))
                .flatMap(jwkSet -> {
                    SignedJWT signedJWT;
                    JWTClaimsSet claims;

                    try {
                        signedJWT = SignedJWT.parse(accessToken);
                        claims = signedJWT.getJWTClaimsSet();
                    } catch (ParseException e) {
                        log.error("Error parsing JWT.", e);
                        return Mono.error(new JWTParsingException("Error parsing JWT"));
                    }

                    // Iss is validated upstream by CustomAuthenticationManager, which
                    // matches it against the configured verifier URL by origin. We only
                    // check expiration (when requested) and signature here.

                    if (checkExpiration && (claims.getExpirationTime() == null
                            || new Date().after(claims.getExpirationTime()))) {
                        log.error("JWT validation failed: token has expired. expirationTime={}",
                                claims.getExpirationTime());

                        return Mono.error(new JWTVerificationException("Token has expired"));
                    }

                    try {
                        JWSVerifier verifier = getJWSVerifier(signedJWT, jwkSet);

                        if (!signedJWT.verify(verifier)) {
                            log.error("JWT validation failed: invalid token signature.");
                            return Mono.error(new JWTVerificationException("Invalid token signature"));
                        }

                        return Mono.empty(); // Valid token
                    } catch (JOSEException e) {
                        log.error("Error verifying JWT signature.", e);
                        return Mono.error(new JWTVerificationException("Error verifying JWT signature"));
                    }
                });
    }

    private JWSVerifier getJWSVerifier(SignedJWT signedJWT, JWKSet jwkSet) throws JOSEException {
        String keyId = signedJWT.getHeader().getKeyID();
        JWK jwk = jwkSet.getKeyByKeyId(keyId);
        if (jwk == null) {
            throw new JOSEException("No matching JWK found for Key ID: " + keyId);
        }
        return switch (jwk.getKeyType().toString()) {
            case "RSA" -> new RSASSAVerifier(((RSAKey) jwk).toRSAPublicKey());
            case "EC" -> new ECDSAVerifier(((ECKey) jwk).toECPublicKey());
            case "oct" -> new MACVerifier(((OctetSequenceKey) jwk).toByteArray());
            default -> throw new JOSEException("Unsupported JWK type: " + jwk.getKeyType());
        };
    }

    private Mono<JWKSet> fetchJWKSet(String jwksUri) {
        if (cachedJWKSet != null) {
            log.info("Using cached JWK Set. Original JWKS URI: {}", jwksUri);
            return Mono.just(cachedJWKSet);
        }

        return Mono.defer(() -> {
            synchronized (jwkLock) {
                if (cachedJWKSet != null) {
                    log.info("Using cached JWK Set. Original JWKS URI: {}", jwksUri);
                    return Mono.just(cachedJWKSet);
                }

                log.info("Fetching JWK Set from URL: {}", jwksUri);

                return oauth2VerifierWebClient.get()
                        .uri(jwksUri)
                        .retrieve()
                        .bodyToMono(String.class)
                        .<JWKSet>handle((jwks, sink) -> {
                            try {
                                cachedJWKSet = JWKSet.parse(jwks);
                                sink.next(cachedJWKSet);
                            } catch (ParseException e) {
                                sink.error(new JWTVerificationException("Error parsing the JWK Set"));
                            }
                        })
                        .onErrorMap(e -> new JWTVerificationException("Error fetching the JWK Set"));
            }
        });
    }

    @Override
    public Mono<OpenIDProviderMetadata> getWellKnownInfo() {
        String wellKnownInfoEndpoint = appConfig.getVerifierUrl() + AUTHORIZATION_SERVER_METADATA_WELL_KNOWN_PATH;

        log.info("Fetching OpenID Provider Metadata from URL: {}", wellKnownInfoEndpoint);

        return oauth2VerifierWebClient.get()
                .uri(wellKnownInfoEndpoint)
                .retrieve()
                .bodyToMono(OpenIDProviderMetadata.class)
                .onErrorMap(e -> new WellKnownInfoFetchException("Error fetching OpenID Provider Metadata", e));
    }

    @Override
    public Mono<VerifierOauth2AccessToken> performTokenRequest(String body) {
        log.info("Performing token request...");

        return getWellKnownInfo()
                .flatMap(metadata -> {
                    log.info("Performing token request to URL: {}", metadata.tokenEndpoint());

                    return oauth2VerifierWebClient.post()
                            .uri(metadata.tokenEndpoint())
                            .header(CONTENT_TYPE, CONTENT_TYPE_URL_ENCODED_FORM)
                            .bodyValue(body)
                            .retrieve()
                            .bodyToMono(VerifierOauth2AccessToken.class)
                            .onErrorMap(e -> new TokenFetchException("Error fetching the token", e));
                });
    }
}