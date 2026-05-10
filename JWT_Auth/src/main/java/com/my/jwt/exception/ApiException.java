package com.my.jwt.exception;

import org.springframework.http.HttpStatus; // HTTP status code carried by the exception

/**
 * Generic application exception that carries an {@link HttpStatus} code.
 *
 * <p>Caught by {@link GlobalExceptionHandler} and serialised to a uniform error
 * JSON body instead of Spring's default white-label error page.</p>
 */
public class ApiException extends RuntimeException { // Unchecked so callers don't need try/catch

    /** HTTP status code to return to the client. */
    private final HttpStatus status; // Stored so the handler can set the response status

    /**
     * Creates a new {@link ApiException}.
     *
     * @param message human-readable error description
     * @param status  HTTP status code to respond with
     */
    public ApiException(String message, HttpStatus status) {
        super(message); // Pass the message to RuntimeException for .getMessage()
        this.status = status; // Store the HTTP status
    }

    /**
     * Returns the HTTP status associated with this exception.
     *
     * @return HTTP status code
     */
    public HttpStatus getStatus() {
        return status; // Read by GlobalExceptionHandler to set the response status
    }
}
