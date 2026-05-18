package es.altia.domeadapter.backend.shared.domain.util;

public class Constants {

    private Constants() {
        throw new IllegalStateException("Utility class");
    }

    public static final String JWT_VC_JSON = "jwt_vc_json";
    public static final String SYNC = "S";
    public static final String DID_KEY                          = "did:key:";
    public static final String CONTENT_TYPE                     = "Content-Type";
    public static final String CONTENT_TYPE_APPLICATION_JSON     = "application/json";
    public static final String CONTENT_TYPE_URL_ENCODED_FORM    = "application/x-www-form-urlencoded";
    public static final String AUTHENTICATION_FAILED            = "Authentication failed";

    public static final String CLIENT_CREDENTIALS_GRANT_TYPE_VALUE = "client_credentials";
    public static final String CLIENT_ASSERTION_TYPE_VALUE         = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";
    public static final Integer CLIENT_ASSERTION_EXPIRATION_TIME   = 2;
    public static final String CLIENT_ASSERTION_EXPIRATION_TIME_UNIT = "MINUTES";

    public static final String PRODUCT_SPECIFICATION_ID = "productSpecificationId";
    public static final String CREDENTIAL_ID             = "credentialId";
    public static final String MAIL_ERROR_COMMUNICATION_EXCEPTION_MESSAGE = "Error during communication with the mail server";
    public static final String UTF_8                     = "UTF-8";

    public static final String MANDATOR_FIELD = "mandator";
    public static final String MANDATEE_FIELD = "mandatee";
    public static final String EMAIL_FIELD = "email";
    public static final String ORGANIZATION_IDENTIFIER_FIELD = "organizationIdentifier";
}
