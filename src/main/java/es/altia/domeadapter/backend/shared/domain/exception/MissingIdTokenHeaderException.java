package es.altia.domeadapter.backend.shared.domain.exception;

public class MissingIdTokenHeaderException extends RuntimeException {
    public MissingIdTokenHeaderException(String message) {
        super(message);
    }
}
