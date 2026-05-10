package com.my.jwt.dto;

import jakarta.validation.constraints.Email; // Validates email format
import jakarta.validation.constraints.NotBlank; // Rejects null and blank strings
import jakarta.validation.constraints.Size; // Enforces minimum/maximum length

/**
 * Request body for {@code POST /api/v1/auth/register}.
 *
 * @param firstName user's first name
 * @param lastName  user's last name
 * @param email     unique email address used as the login username
 * @param password  plain-text password (will be BCrypt-hashed before storage)
 */
public record RegisterRequest( // Java record: immutable DTO with compact syntax

        @NotBlank(message = "First name is required") // Rejects null / whitespace-only values
        String firstName, // Mapped to User.firstName

        @NotBlank(message = "Last name is required") // Rejects null / whitespace-only values
        String lastName, // Mapped to User.lastName

        @NotBlank(message = "Email is required") // Rejects null / whitespace-only values
        @Email(message = "Email must be a valid address") // Validates RFC-5322 email format
        String email, // Used as the JWT subject and login identifier

        @NotBlank(message = "Password is required") // Rejects null / whitespace-only values
        @Size(min = 8, message = "Password must be at least 8 characters") // Minimum length check
        String password // Plain-text password; hashed by AuthService before persisting
) {}
