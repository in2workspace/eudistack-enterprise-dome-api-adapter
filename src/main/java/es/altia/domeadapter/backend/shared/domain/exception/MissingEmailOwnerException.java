package es.altia.domeadapter.backend.shared.domain.exception;

public class MissingEmailOwnerException extends RuntimeException {
    public MissingEmailOwnerException(String message) {
        super(message);
    }
}
