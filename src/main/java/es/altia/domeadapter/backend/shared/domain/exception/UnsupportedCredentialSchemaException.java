package es.altia.domeadapter.backend.shared.domain.exception;

public class UnsupportedCredentialSchemaException extends RuntimeException {
    public UnsupportedCredentialSchemaException(String message) {
        super(message);
    }
}
