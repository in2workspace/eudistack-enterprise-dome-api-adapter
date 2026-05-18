package es.altia.domeadapter.backend.shared.domain.util;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GlobalErrorTypes {

    INVALID_JWT("invalid_jwt"),
    UNSUPPORTED_CREDENTIAL_TYPE("unsupported_credential_type"),
    OPERATION_NOT_SUPPORTED("operation_not_supported"),
    FORMAT_IS_NOT_SUPPORTED("format_is_not_supported"),
    MISSING_HEADER("missing_header"),
    NO_SUCH_ELEMENT("no_such_element"),
    PARSE_ERROR("parse_error"),
    PROOF_VALIDATION_ERROR("proof_validation_error"),
    JWT_VERIFICATION("jwt_verification_error"),
    EMAIL_COMMUNICATION("email_communication_error"),
    INVALID_CREDENTIAL_FORMAT("invalid_credential_format"),
    INVALID_ISSUER_RESPONSE("invalid_issuer_response");

    private final String code;

}
