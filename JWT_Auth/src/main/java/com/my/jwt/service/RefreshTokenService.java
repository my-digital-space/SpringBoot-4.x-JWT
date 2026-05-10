package com.my.jwt.service;

import com.my.jwt.config.ApplicationProperties; // Reads refresh token TTL from application.yaml
import com.my.jwt.entity.RefreshToken; // Refresh token JPA entity
import com.my.jwt.entity.User; // User JPA entity
import com.my.jwt.exception.ApiException; // Application-level exception with HTTP status
import com.my.jwt.repository.RefreshTokenRepository; // CRUD operations on refresh_tokens table
import lombok.RequiredArgsConstructor; // Lombok: constructor injection
import org.springframework.http.HttpStatus; // HTTP status constants
import org.springframework.stereotype.Service; // Spring service bean
import org.springframework.transaction.annotation.Transactional; // Wraps methods in a DB transaction

import java.time.Instant; // UTC timestamp for expiry calculation
import java.util.UUID; // Source of the random token string

/**
 * Service that manages the lifecycle of refresh tokens:
 * <ol>
 *   <li>Issue a new token after login or rotation</li>
 *   <li>Validate a token on each refresh request</li>
 *   <li>Rotate (replace) the token after a successful refresh</li>
 *   <li>Revoke all tokens for a user on logout</li>
 * </ol>
 */
@Service // Spring-managed bean
@RequiredArgsConstructor // Lombok: inject final fields
public class RefreshTokenService {

    /** Performs CRUD operations on the refresh_tokens table. */
    private final RefreshTokenRepository refreshTokenRepository; // Injected repository

    /** Reads the configured refresh token TTL from application.yaml. */
    private final ApplicationProperties props; // Injected config

    /**
     * Creates and persists a new refresh token for the given user.
     *
     * @param user the authenticated user
     * @return the persisted {@link RefreshToken} entity (its {@code token} field goes into the cookie)
     */
    @Transactional // Wraps the save in a transaction
    public RefreshToken createRefreshToken(User user) {
        RefreshToken refreshToken = RefreshToken.builder()
                .token(UUID.randomUUID().toString()) // Random UUID as the opaque token string
                .user(user) // Associate with the requesting user
                .expiresAt(Instant.now().plusMillis(props.getRefreshTokenExpiration())) // Now + 7 days
                .revoked(false) // Mark as active
                .build();
        return refreshTokenRepository.save(refreshToken); // Persist and return with generated id
    }

    /**
     * Verifies that a refresh token string is valid (exists, not revoked, not expired).
     * Throws {@link ApiException} with 401 if any check fails.
     *
     * @param tokenString the raw token string extracted from the HttpOnly cookie
     * @return the validated {@link RefreshToken} entity
     */
    @Transactional(readOnly = true) // Read-only; no DB writes in this method
    public RefreshToken verifyRefreshToken(String tokenString) {
        // Look up by token string; throw 401 if not found (could be reuse after rotation)
        RefreshToken token = refreshTokenRepository.findByToken(tokenString)
                .orElseThrow(() -> new ApiException("Refresh token not found", HttpStatus.UNAUTHORIZED));

        // Reject revoked tokens (already used or invalidated by logout)
        if (token.isRevoked()) {
            throw new ApiException("Refresh token has been revoked", HttpStatus.UNAUTHORIZED);
        }

        // Reject expired tokens
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException("Refresh token has expired — please login again", HttpStatus.UNAUTHORIZED);
        }

        return token; // Token is valid; return it so the caller can read the user
    }

    /**
     * Rotates the refresh token: deletes the old one and issues a fresh one.
     * This prevents refresh token reuse attacks.
     *
     * @param oldToken the validated token that was just used
     * @return the newly issued {@link RefreshToken}
     */
    @Transactional // Must be transactional: delete + insert must succeed or fail together
    public RefreshToken rotateRefreshToken(RefreshToken oldToken) {
        refreshTokenRepository.delete(oldToken); // Invalidate the used token (rotation)
        return createRefreshToken(oldToken.getUser()); // Issue a new token for the same user
    }

    /**
     * Deletes all refresh tokens for the given user — called on logout to
     * invalidate all active sessions.
     *
     * @param user the user logging out
     */
    @Transactional // Wraps DELETE in a transaction
    public void revokeAllUserTokens(User user) {
        refreshTokenRepository.deleteByUser(user); // DELETE FROM refresh_tokens WHERE user_id = ?
    }
}
