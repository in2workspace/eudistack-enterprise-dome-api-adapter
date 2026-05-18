package es.altia.domeadapter.backend.shared.domain.exception;

public class InvalidIssuerResponseException extends RuntimeException {

    public InvalidIssuerResponseException(String message) {
        super(message);
    }
}
