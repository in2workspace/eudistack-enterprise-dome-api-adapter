package es.altia.domeadapter.backend.shared.domain.model.dto.retry;

import lombok.Builder;

@Builder
public record LabelCredentialDeliveryPayload(
        String responseUri,
        String credentialId,
        String productSpecificationId,
        String email,
        String signedCredential,
        String issuedBy
) {}
