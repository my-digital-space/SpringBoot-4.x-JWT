package com.my.jwt.exception;

import org.springframework.http.HttpStatus; // Standard HTTP status constants
import org.springframework.http.ProblemDetail; // RFC 7807 problem detail response body
import org.springframework.security.access.AccessDeniedException; // Thrown when authorization fails
import org.springframework.security.core.AuthenticationException; // Thrown when authentication fails
import org.springframework.validation.FieldError; // Represents a single field validation failure
import org.springframework.web.bind.MethodArgumentNotValidException; // Thrown by @Valid on @RequestBody
import org.springframework.web.bind.annotation.ExceptionHandler; // Maps exceptions to handler methods
import org.springframework.web.bind.annotation.RestControllerAdvice; // Global exception handler for REST controllers

import java.net.URI; // Used to set the problem type URI
import java.time.Instant; // UTC timestamp for the error response
import java.util.Map; // Key-value pairs for validation field errors
import java.util.stream.Collectors; // Collects field error stream to a map

/**
 * Centralised exception handler that translates application exceptions into
 * consistent RFC 7807 {@link ProblemDetail} JSON responses.
 *
 * <p>Having one handler keeps error formatting uniform across all controllers.</p>
 */
@RestControllerAdvice // Intercepts exceptions from all @RestController classes
public class GlobalExceptionHandler {

    /** URI prefix used for all problem type identifiers. */
    private static final String TYPE_PREFIX = "https://api.jwt-auth-app.com/errors/"; // Base URI for problem types

    /**
     * Handles application-level exceptions thrown with a specific HTTP status.
     *
     * @param ex the {@link ApiException} thrown by a service or controller
     * @return RFC 7807 problem detail with the appropriate status
     */
    @ExceptionHandler(ApiException.class) // Catches every ApiException in the application
    public ProblemDetail handleApiException(ApiException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage()); // Status + description
        problem.setType(URI.create(TYPE_PREFIX + ex.getStatus().value())); // Problem type URI
        problem.setProperty("timestamp", Instant.now()); // Attach UTC timestamp to the response
        return problem; // Spring serialises this to { "status", "detail", "type", "timestamp" }
    }

    /**
     * Handles bean-validation failures from {@code @Valid} on request bodies.
     *
     * @param ex the validation exception containing per-field error details
     * @return 422 Unprocessable Entity with a map of field → error message
     */
    @ExceptionHandler(MethodArgumentNotValidException.class) // Catches @Valid failures
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        // Collect field-level errors into a readable map: { "email": "must not be blank" }
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,          // Map key: the field name
                        FieldError::getDefaultMessage, // Map value: the validation message
                        (first, second) -> first       // Keep the first message if there are duplicates
                ));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, "Validation failed"); // 422 status
        problem.setType(URI.create(TYPE_PREFIX + "validation")); // Problem type URI
        problem.setProperty("timestamp", Instant.now()); // UTC timestamp
        problem.setProperty("errors", errors); // Embed field-level errors in the response body
        return problem;
    }

    /**
     * Handles Spring Security {@link AuthenticationException} (e.g. bad credentials).
     *
     * @param ex the authentication exception
     * @return 401 Unauthorized
     */
    @ExceptionHandler(AuthenticationException.class) // Catches Spring Security auth failures
    public ProblemDetail handleAuthenticationException(AuthenticationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage()); // 401
        problem.setType(URI.create(TYPE_PREFIX + "unauthorized")); // Problem type URI
        problem.setProperty("timestamp", Instant.now()); // UTC timestamp
        return problem;
    }

    /**
     * Handles Spring Security {@link AccessDeniedException} (e.g. insufficient role).
     *
     * @param ex the access denied exception
     * @return 403 Forbidden
     */
    @ExceptionHandler(AccessDeniedException.class) // Catches authorization failures
    public ProblemDetail handleAccessDeniedException(AccessDeniedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access denied"); // 403
        problem.setType(URI.create(TYPE_PREFIX + "forbidden")); // Problem type URI
        problem.setProperty("timestamp", Instant.now()); // UTC timestamp
        return problem;
    }

    /**
     * Catch-all handler for any unhandled exception.
     *
     * @param ex the unexpected exception
     * @return 500 Internal Server Error with a generic message
     */
    @ExceptionHandler(Exception.class) // Last-resort catch-all
    public ProblemDetail handleGeneral(Exception ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred"); // 500
        problem.setType(URI.create(TYPE_PREFIX + "internal")); // Problem type URI
        problem.setProperty("timestamp", Instant.now()); // UTC timestamp
        return problem;
    }
}
