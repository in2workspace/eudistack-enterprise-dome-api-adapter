package es.altia.domeadapter.backend.shared.infrastructure.controller.error;

import es.altia.domeadapter.backend.shared.domain.exception.InvalidIssuerResponseException;
import es.altia.domeadapter.backend.shared.domain.exception.JWTParsingException;
import es.altia.domeadapter.backend.shared.domain.exception.JWTVerificationException;
import es.altia.domeadapter.backend.shared.domain.exception.ProofValidationException;
import es.altia.domeadapter.backend.shared.domain.model.dto.GlobalErrorMessage;
import es.altia.domeadapter.backend.shared.domain.util.GlobalErrorTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.naming.OperationNotSupportedException;
import java.text.ParseException;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private ErrorResponseFactory errors;
    private ServerHttpRequest request;
    private GlobalExceptionHandler handler;
    private GlobalErrorMessage errorMessage;

    @BeforeEach
    void setUp() {
        errors = mock(ErrorResponseFactory.class);
        request = mock(ServerHttpRequest.class);
        handler = new GlobalExceptionHandler(errors);
        errorMessage = mock(GlobalErrorMessage.class);
    }

    @Test
    void shouldHandleNoSuchElementException() {
        NoSuchElementException exception = new NoSuchElementException("Not found");

        when(errors.handleWith(
                exception,
                request,
                GlobalErrorTypes.NO_SUCH_ELEMENT.getCode(),
                "Resource not found",
                HttpStatus.NOT_FOUND,
                "The requested resource was not found"
        )).thenReturn(Mono.just(errorMessage));

        Mono<GlobalErrorMessage> result = handler.handleNoSuchElementException(exception, request);

        StepVerifier.create(result)
                .assertNext(response -> assertThat(response).isSameAs(errorMessage))
                .verifyComplete();

        verify(errors).handleWith(
                exception,
                request,
                GlobalErrorTypes.NO_SUCH_ELEMENT.getCode(),
                "Resource not found",
                HttpStatus.NOT_FOUND,
                "The requested resource was not found"
        );
    }

    @Test
    void shouldHandleParseException() {
        ParseException exception = new ParseException("Parse error", 0);

        when(errors.handleWith(
                exception,
                request,
                GlobalErrorTypes.PARSE_ERROR.getCode(),
                "Parse error",
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An internal parsing error occurred."
        )).thenReturn(Mono.just(errorMessage));

        Mono<GlobalErrorMessage> result = handler.handleParseException(exception, request);

        StepVerifier.create(result)
                .assertNext(response -> assertThat(response).isSameAs(errorMessage))
                .verifyComplete();

        verify(errors).handleWith(
                exception,
                request,
                GlobalErrorTypes.PARSE_ERROR.getCode(),
                "Parse error",
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An internal parsing error occurred."
        );
    }

    @Test
    void shouldHandleProofValidationException() {
        ProofValidationException exception = mock(ProofValidationException.class);

        when(errors.handleWith(
                exception,
                request,
                GlobalErrorTypes.PROOF_VALIDATION_ERROR.getCode(),
                "Proof validation error",
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An internal proof validation error occurred."
        )).thenReturn(Mono.just(errorMessage));

        Mono<GlobalErrorMessage> result = handler.handleProofValidationException(exception, request);

        StepVerifier.create(result)
                .assertNext(response -> assertThat(response).isSameAs(errorMessage))
                .verifyComplete();

        verify(errors).handleWith(
                exception,
                request,
                GlobalErrorTypes.PROOF_VALIDATION_ERROR.getCode(),
                "Proof validation error",
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An internal proof validation error occurred."
        );
    }

    @Test
    void shouldHandleOperationNotSupportedException() {
        OperationNotSupportedException exception = new OperationNotSupportedException("Not supported");

        when(errors.handleWith(
                exception,
                request,
                GlobalErrorTypes.OPERATION_NOT_SUPPORTED.getCode(),
                "Operation not supported",
                HttpStatus.BAD_REQUEST,
                "The given operation is not supported"
        )).thenReturn(Mono.just(errorMessage));

        Mono<GlobalErrorMessage> result = handler.handleOperationNotSupportedException(exception, request);

        StepVerifier.create(result)
                .assertNext(response -> assertThat(response).isSameAs(errorMessage))
                .verifyComplete();

        verify(errors).handleWith(
                exception,
                request,
                GlobalErrorTypes.OPERATION_NOT_SUPPORTED.getCode(),
                "Operation not supported",
                HttpStatus.BAD_REQUEST,
                "The given operation is not supported"
        );
    }

    @Test
    void shouldHandleJWTVerificationException() {
        JWTVerificationException exception = mock(JWTVerificationException.class);

        when(errors.handleWith(
                exception,
                request,
                GlobalErrorTypes.JWT_VERIFICATION.getCode(),
                "JWT verification failed",
                HttpStatus.UNAUTHORIZED,
                "JWT verification failed."
        )).thenReturn(Mono.just(errorMessage));

        Mono<GlobalErrorMessage> result = handler.handleJWTVerificationException(exception, request);

        StepVerifier.create(result)
                .assertNext(response -> assertThat(response).isSameAs(errorMessage))
                .verifyComplete();

        verify(errors).handleWith(
                exception,
                request,
                GlobalErrorTypes.JWT_VERIFICATION.getCode(),
                "JWT verification failed",
                HttpStatus.UNAUTHORIZED,
                "JWT verification failed."
        );
    }

    @Test
    void shouldHandleJWTParsingException() {
        JWTParsingException exception = mock(JWTParsingException.class);

        when(errors.handleWith(
                exception,
                request,
                GlobalErrorTypes.INVALID_JWT.getCode(),
                "JWT parsing error",
                HttpStatus.INTERNAL_SERVER_ERROR,
                "The provided JWT is invalid or can't be parsed."
        )).thenReturn(Mono.just(errorMessage));

        Mono<GlobalErrorMessage> result = handler.handleJWTParsingException(exception, request);

        StepVerifier.create(result)
                .assertNext(response -> assertThat(response).isSameAs(errorMessage))
                .verifyComplete();

        verify(errors).handleWith(
                exception,
                request,
                GlobalErrorTypes.INVALID_JWT.getCode(),
                "JWT parsing error",
                HttpStatus.INTERNAL_SERVER_ERROR,
                "The provided JWT is invalid or can't be parsed."
        );
    }

    @Test
    void shouldHandleInvalidIssuerResponseException() {
        InvalidIssuerResponseException exception = new InvalidIssuerResponseException("Issuer returned empty signed credential");

        when(errors.handleWith(
                exception,
                request,
                GlobalErrorTypes.INVALID_ISSUER_RESPONSE.getCode(),
                "Invalid issuer response",
                HttpStatus.BAD_GATEWAY,
                exception.getMessage()
        )).thenReturn(Mono.just(errorMessage));

        Mono<GlobalErrorMessage> result = handler.handleInvalidIssuerResponseException(exception, request);

        StepVerifier.create(result)
                .assertNext(response -> assertThat(response).isSameAs(errorMessage))
                .verifyComplete();

        verify(errors).handleWith(
                exception,
                request,
                GlobalErrorTypes.INVALID_ISSUER_RESPONSE.getCode(),
                "Invalid issuer response",
                HttpStatus.BAD_GATEWAY,
                exception.getMessage()
        );
    }
}
