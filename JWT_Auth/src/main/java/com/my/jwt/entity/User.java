package com.my.jwt.entity;

import com.my.jwt.enums.Role; // Application role enum (ADMIN, MANAGER, USER)
import jakarta.persistence.*; // JPA annotations: @Entity, @Table, @Id, etc.
import lombok.*; // Lombok: @Getter, @Setter, @Builder, etc.
import org.springframework.security.core.GrantedAuthority; // Spring Security authority interface
import org.springframework.security.core.authority.SimpleGrantedAuthority; // Converts role to authority string
import org.springframework.security.core.userdetails.UserDetails; // Spring Security user contract

import java.util.Collection; // Return type for getAuthorities()
import java.util.List; // Wraps the single authority in a list

/**
 * JPA entity representing an application user.
 *
 * <p>Implements {@link UserDetails} so Spring Security can use instances of this
 * class directly without a separate adapter.</p>
 */
@Entity // Marks this class as a JPA-managed database table
@Table(name = "users") // Maps to the "users" table (avoids conflict with reserved word "user")
@Getter // Lombok: generates all getters
@Setter // Lombok: generates all setters
@NoArgsConstructor // Lombok: generates no-args constructor required by JPA
@AllArgsConstructor // Lombok: generates all-args constructor for use with @Builder
@Builder // Lombok: enables fluent builder pattern
public class User implements UserDetails { // UserDetails contract lets Spring Security load this directly

    /** Primary key; auto-incremented by the database. */
    @Id // Marks this field as the primary key
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Auto-increment strategy
    private Long id; // Unique identifier for the user

    /** User's first name. */
    @Column(nullable = false) // NOT NULL constraint in the database
    private String firstName; // Stored as a VARCHAR column

    /** User's last name. */
    @Column(nullable = false) // NOT NULL constraint
    private String lastName; // Stored as a VARCHAR column

    /** Unique email address used as the login username. */
    @Column(nullable = false, unique = true) // NOT NULL + UNIQUE constraint
    private String email; // Login identifier; also used as the JWT subject claim

    /** BCrypt-hashed password; never stored in plain text. */
    @Column(nullable = false) // NOT NULL constraint
    private String password; // Stored as a BCrypt hash (60-character string)

    /** Application role that drives Spring Security authorization decisions. */
    @Enumerated(EnumType.STRING) // Persists the enum constant name ("ADMIN", "MANAGER", "USER")
    @Column(nullable = false) // NOT NULL constraint
    private Role role; // Determines what resources this user can access

    // -------------------------------------------------------
    // UserDetails contract — Spring Security uses these methods
    // -------------------------------------------------------

    /**
     * Returns the single authority derived from this user's {@link Role}.
     * The prefix {@code ROLE_} is required by Spring Security's role-based checks.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Wrap the role name with "ROLE_" prefix so hasRole("ADMIN") works in security config
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    /** Returns the BCrypt-hashed password used for authentication. */
    @Override
    public String getPassword() {
        return password; // Spring Security reads this during authentication
    }

    /** Returns the email as the username (login identifier). */
    @Override
    public String getUsername() {
        return email; // Email is used as the unique login identifier
    }

    /** Account is always non-expired in this implementation. */
    @Override
    public boolean isAccountNonExpired() {
        return true; // Extend here to support account expiry logic
    }

    /** Account is always non-locked in this implementation. */
    @Override
    public boolean isAccountNonLocked() {
        return true; // Extend here to support account lock-out logic
    }

    /** Credentials are always non-expired in this implementation. */
    @Override
    public boolean isCredentialsNonExpired() {
        return true; // Extend here to support password rotation enforcement
    }

    /** Account is always enabled in this implementation. */
    @Override
    public boolean isEnabled() {
        return true; // Extend here to support email-verification or soft-delete
    }
}
