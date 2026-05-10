package com.my.jwt.dto;

/**
 * Response body returned after a successful login or token refresh.
 *
 * <p>Only the access token is included in the body.
 * The refresh token is delivered via a separate {@code Set-Cookie} header
 * using an HttpOnly cookie, so it is never accessible to JavaScript.</p>
 *
 * @param accessToken  short-lived JWT access token (10-minute expiry)
 * @param tokenType    token scheme; always {@code "Bearer"}
 * @param expiresIn    access token lifetime in seconds (600 = 10 minutes)
 */
public record AuthResponse( // Immutable response DTO

        String accessToken, // JWT string that the client includes in Authorization: Bearer <token>

        String tokenType, // Always "Bearer" — follows the OAuth 2.0 Bearer Token convention

        long expiresIn // Seconds until the access token expires; helps clients schedule a refresh
) {
    /**
     * Convenience factory that pre-fills {@code tokenType} and {@code expiresIn}.
     *
     * @param accessToken the newly issued JWT
     * @param expiresInMs access token expiry in milliseconds (from config)
     * @return a fully populated {@link AuthResponse}
     */
    public static AuthResponse of(String accessToken, long expiresInMs) {
        // Convert milliseconds to seconds for the expiresIn field (standard OAuth2 convention)
        return new AuthResponse(accessToken, "Bearer", expiresInMs / 1000);
    }
}
