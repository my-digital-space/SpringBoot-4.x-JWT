package com.my.jwt.repository;

import com.my.jwt.entity.RefreshToken; // RefreshToken entity
import com.my.jwt.entity.User; // User entity (used in deleteByUser query)
import org.springframework.data.jpa.repository.JpaRepository; // Base CRUD repository
import org.springframework.data.jpa.repository.Modifying; // Marks a @Query as a write operation
import org.springframework.stereotype.Repository; // Spring Data repository marker

import java.util.Optional; // Nullable result wrapper

/**
 * Spring Data JPA repository for {@link RefreshToken} entities.
 *
 * <p>Used to issue, validate, rotate, and revoke refresh tokens.</p>
 */
@Repository // Enables Spring Data proxy and persistence exception translation
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Finds an active refresh token record by its token string.
     *
     * @param token the raw token string stored in the HttpOnly cookie
     * @return an {@link Optional} with the matching token, or empty if not found / already rotated
     */
    Optional<RefreshToken> findByToken(String token); // SELECT * FROM refresh_tokens WHERE token = ?

    /**
     * Removes all refresh tokens belonging to a user — called on logout to invalidate all sessions.
     *
     * @param user the user whose tokens should be purged
     */
    @Modifying // Required for DELETE/UPDATE derived queries
    void deleteByUser(User user); // DELETE FROM refresh_tokens WHERE user_id = ?
}
