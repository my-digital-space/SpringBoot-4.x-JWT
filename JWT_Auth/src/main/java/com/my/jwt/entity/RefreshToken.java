package com.my.jwt.entity;

import jakarta.persistence.*; // JPA annotations
import lombok.*; // Lombok annotations

import java.time.Instant; // Represents the token expiry timestamp

/**
 * JPA entity that persists an active refresh token for a {@link User}.
 *
 * <p>Storing refresh tokens in the database allows the server to:</p>
 * <ul>
 *   <li>Revoke tokens on logout (delete the row)</li>
 *   <li>Rotate tokens on every use (replace the row)</li>
 *   <li>Detect refresh token reuse attacks (token not found → force re-login)</li>
 * </ul>
 */
@Entity // Marks this class as a JPA-managed table
@Table(name = "refresh_tokens") // Table name in the database
@Getter // Lombok: generates getters
@Setter // Lombok: generates setters
@NoArgsConstructor // Lombok: no-args constructor required by JPA
@AllArgsConstructor // Lombok: all-args constructor for use with @Builder
@Builder // Lombok: fluent builder pattern
public class RefreshToken {

    /** Primary key; auto-incremented by the database. */
    @Id // Primary key annotation
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Auto-increment
    private Long id; // Unique identifier for this refresh token record

    /**
     * The opaque token string stored in the HttpOnly cookie and verified on
     * every refresh request.
     */
    @Column(nullable = false, unique = true, length = 512) // NOT NULL, UNIQUE, max 512 chars
    private String token; // UUID or SHA-256 hex string stored here

    /** The user this refresh token belongs to. */
    @ManyToOne(fetch = FetchType.LAZY) // Many tokens can belong to one user; lazy-load for performance
    @JoinColumn(name = "user_id", nullable = false) // Foreign key column in refresh_tokens table
    private User user; // Owning user entity

    /** Timestamp after which this token is no longer valid. */
    @Column(nullable = false) // NOT NULL constraint
    private Instant expiresAt; // Compared to Instant.now() on every refresh request

    /** Whether this token has been revoked (e.g. via logout or reuse detection). */
    @Column(nullable = false) // NOT NULL constraint
    @Builder.Default // Ensures the default value is applied when using the builder
    private boolean revoked = false; // Defaults to false on creation; set to true on logout/rotation
}
