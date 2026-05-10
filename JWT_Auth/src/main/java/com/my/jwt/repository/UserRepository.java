package com.my.jwt.repository;

import com.my.jwt.entity.User; // User entity
import org.springframework.data.jpa.repository.JpaRepository; // Base CRUD + paging repository
import org.springframework.stereotype.Repository; // Marks as Spring Data repository bean

import java.util.Optional; // Wraps the nullable result of the lookup

/**
 * Spring Data JPA repository for {@link User} entities.
 *
 * <p>Spring generates the implementation at startup; no manual SQL required.</p>
 */
@Repository // Triggers Spring Data repository proxy creation and exception translation
public interface UserRepository extends JpaRepository<User, Long> { // Long is the primary key type

    /**
     * Looks up a user by their email address (used as the login username).
     *
     * @param email the email address to search for
     * @return an {@link Optional} containing the matching user, or empty if not found
     */
    Optional<User> findByEmail(String email); // Spring derives SELECT * FROM users WHERE email = ? automatically

    /**
     * Checks whether a user with the given email already exists (used during registration).
     *
     * @param email the email address to check
     * @return {@code true} if a user with this email exists, {@code false} otherwise
     */
    boolean existsByEmail(String email); // Spring derives SELECT COUNT(*) > 0 FROM users WHERE email = ?
}
