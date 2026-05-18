package es.altia.domeadapter.backend.shared.domain.exception;

public class InvalidCredentialFormatException extends RuntimeException {
    public InvalidCredentialFormatException(String message) {
        super(message);
    }
}
