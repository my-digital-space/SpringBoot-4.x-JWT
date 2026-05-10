package com.my.jwt.dto;

import jakarta.validation.constraints.Email; // Validates email format
import jakarta.validation.constraints.NotBlank; // Rejects null / blank strings

/**
 * Request body for {@code POST /api/v1/auth/login}.
 *
 * @param email    registered user's email address
 * @param password plain-text password to authenticate with
 */
public record LoginRequest( // Immutable record DTO

        @NotBlank(message = "Email is required") // Rejects null / whitespace
        @Email(message = "Email must be a valid address") // RFC-5322 format check
        String email, // Login identifier

        @NotBlank(message = "Password is required") // Rejects null / whitespace
        String password // Plain-text; compared to the stored BCrypt hash
) {}
