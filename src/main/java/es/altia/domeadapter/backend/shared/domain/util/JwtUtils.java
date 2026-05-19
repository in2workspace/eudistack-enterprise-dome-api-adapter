package es.altia.domeadapter.backend.shared.domain.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtils {

    private final ObjectMapper objectMapper;

    public String decodePayload(String jwt) {
        String[] parts = jwt.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid JWT format");
        }
        byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
        return new String(decoded, StandardCharsets.UTF_8);
    }

    public UUID extractCredentialId(String jwt) {
        try {
            JsonNode claims = objectMapper.readTree(decodePayload(jwt));

            UUID idFromJti = parseUuidNode(claims.get("jti"));
            if (idFromJti != null) {
                return idFromJti;
            }

            UUID idFromRoot = parseUuidNode(claims.get("id"));
            if (idFromRoot != null) {
                return idFromRoot;
            }

            JsonNode vcNode = claims.get("vc");
            if (vcNode != null && !vcNode.isNull()) {
                UUID idFromVc = parseUuidNode(vcNode.get("id"));
                if (idFromVc != null) {
                    return idFromVc;
                }
            }

            throw new IllegalArgumentException("Could not extract credential ID from JWT claims");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Error extracting credential ID from JWT", e);
        }
    }

    private UUID parseUuidNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }

        String value = node.asText();
        if (value == null || value.isBlank()) {
            return null;
        }

        if (value.startsWith("urn:uuid:")) {
            value = value.substring("urn:uuid:".length());
        }

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public String extractCredentialSubjectId(String jwt) {
        try {
            JsonNode claims = objectMapper.readTree(decodePayload(jwt));
            log.info("[JWT] Extracting credentialSubject.id from claims: {}", claims);

            String rootCredentialSubjectId = extractCredentialSubjectIdFromNode(claims);
            if (!rootCredentialSubjectId.isBlank()) {
                return rootCredentialSubjectId;
            }

            JsonNode vcNode = claims.get("vc");
            if (vcNode != null && !vcNode.isNull()) {
                String vcCredentialSubjectId = extractCredentialSubjectIdFromNode(vcNode);
                if (!vcCredentialSubjectId.isBlank()) {
                    return vcCredentialSubjectId;
                }
            }

            log.warn("[JWT] Could not extract credentialSubject.id");
            return "";
        } catch (Exception e) {
            log.error("[JWT] Error extracting credentialSubject.id: {}", e.getMessage(), e);
            return "";
        }
    }

    public String extractSubject(String jwt) {
        try {
            JsonNode claims = objectMapper.readTree(decodePayload(jwt));
            JsonNode subNode = claims.get("sub");
            if (subNode == null || subNode.isNull() || subNode.asText().isBlank()) {
                log.warn("[JWT] ID token does not contain a 'sub' claim");
                return null;
            }
            return subNode.asText();
        } catch (Exception e) {
            log.warn("[JWT] Could not extract 'sub' from ID token: {}", e.getMessage());
            return null;
        }
    }

    private String extractCredentialSubjectIdFromNode(JsonNode node) {
        JsonNode csNode = node.get("credentialSubject");
        if (csNode == null || csNode.isNull()) {
            return "";
        }

        JsonNode idNode = csNode.get("id");
        if (idNode == null || idNode.isNull()) {
            return "";
        }

        return idNode.asText();
    }
}
