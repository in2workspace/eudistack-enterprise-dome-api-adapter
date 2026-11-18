package es.altia.domeadapter.backend.issuance.domain;

public final class Constants {

    private Constants() {
        // Prevent instantiation
    }

    public static final String LEGACY_LABEL_CREDENTIAL_SCHEMA = "gx:LabelCredential";
    public static final String LEGACY_LEAR_CREDENTIAL_EMPLOYEE_SCHEMA = "LEARCredentialEmployee";
    public static final String LEGACY_LEAR_CREDENTIAL_MACHINE_SCHEMA = "LEARCredentialMachine";

    public static final String ISSUER_LABEL_CREDENTIAL_SCHEMA = "gx.labelcredential.w3c.2";
    public static final String ISSUER_LEAR_CREDENTIAL_EMPLOYEE_SCHEMA = "learcredential.employee.w3c.4";
    public static final String ISSUER_LEAR_CREDENTIAL_MACHINE_SCHEMA = "learcredential.machine.w3c.3";

    // The Issuer takes a single delivery mode (EUD-268). A label credential is always "direct": the
    // adapter needs the signed credential in the response to upload it to DOME's response_uri.
    public static final String LABEL_DELIVERY = "direct";
    public static final String DEFAULT_DELIVERY = "email";
}