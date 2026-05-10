package com.my.jwt.controller;

import com.my.jwt.dto.AuthResponse; // Response DTO containing the JWT access token
import com.my.jwt.dto.LoginRequest; // Request body for POST /login
import com.my.jwt.dto.RegisterRequest; // Request body for POST /register
import com.my.jwt.service.AuthService; // Authentication business logic
import io.swagger.v3.oas.annotations.Operation; // Swagger: documents an API operation
import io.swagger.v3.oas.annotations.media.Content; // Swagger: describes response body content
import io.swagger.v3.oas.annotations.media.Schema; // Swagger: links the content type to a DTO schema
import io.swagger.v3.oas.annotations.responses.ApiResponse; // Swagger: documents a response status
import io.swagger.v3.oas.annotations.responses.ApiResponses; // Swagger: groups multiple ApiResponse entries
import io.swagger.v3.oas.annotations.security.SecurityRequirement; // Swagger: marks endpoint as protected
import io.swagger.v3.oas.annotations.tags.Tag; // Swagger: groups endpoints under a named tag
import jakarta.servlet.http.HttpServletRequest; // Used to read the refresh token cookie on refresh/logout
import jakarta.servlet.http.HttpServletResponse; // Used to write/clear the refresh token cookie
import jakarta.validation.Valid; // Triggers Bean Validation on the @RequestBody
import lombok.RequiredArgsConstructor; // Lombok: constructor injection
import org.springframework.http.HttpStatus; // HTTP status constants
import org.springframework.http.ResponseEntity; // Wraps the response with a status code
import org.springframework.web.bind.annotation.*; // REST annotations: @RestController, @PostMapping, etc.

/**
 * REST controller exposing the four authentication endpoints:
 * <ul>
 *   <li>{@code POST /api/v1/auth/register} – register a new user</li>
 *   <li>{@code POST /api/v1/auth/login}    – login and receive JWT + refresh cookie</li>
 *   <li>{@code POST /api/v1/auth/refresh}  – exchange refresh cookie for a new JWT</li>
 *   <li>{@code POST /api/v1/auth/logout}   – revoke refresh token and clear cookie</li>
 * </ul>
 *
 * <p>All four paths are publicly accessible (no JWT required) as configured in
 * {@link com.my.jwt.config.SecurityConfig}.</p>
 *
 * Swagger UI: <a href="http://localhost:8081/swagger-ui/index.html">...</a>
 */
@RestController // Marks this class as a REST controller; @ResponseBody is implied on all methods
@RequestMapping("/api/v1/auth") // Base path shared by all methods in this controller
@RequiredArgsConstructor // Lombok: inject AuthService via constructor
@Tag(name = "Authentication", description = "Endpoints for user registration, " +
        "login, token refresh, and logout") // Swagger group
public class AuthController {

    /** Handles all authentication business logic (register, login, refresh, logout). */
    private final AuthService authService; // Injected service

    // -------------------------------------------------------
    // Register
    // -------------------------------------------------------

    /**
     * Registers a new user account with the USER role.
     *
     * @param request  validated registration data from the request body
     * @param response HTTP response used to attach the refresh token HttpOnly cookie
     * @return 201 Created with an {@link AuthResponse} containing the JWT access token
     */
    @PostMapping("/register") // POST /api/v1/auth/register
    @ResponseStatus(HttpStatus.CREATED) // Default status code for this endpoint
    @Operation(
            summary = "Register a new user",
            description = "Creates a new account with the USER role. " +
                    "Returns a JWT access token in the body and sets a refresh " +
                    "token HttpOnly cookie.",
            security = {} // No JWT required for this public endpoint
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User registered successfully",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "409", description = "Email already registered",
                    content = @Content),
            @ApiResponse(responseCode = "422", description = "Validation failed",
                    content = @Content)
    })
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request, // Validate incoming JSON; 422 on failure
            HttpServletResponse response // Injected by Spring; used to write the Set-Cookie header
    ) {
        AuthResponse authResponse = authService.register(request, response); // Delegate to service
        return ResponseEntity.status(HttpStatus.CREATED).body(authResponse); // 201 + body
    }

    // -------------------------------------------------------
    // Login
    // -------------------------------------------------------

    /**
     * Authenticates a user with email and password.
     *
     * @param request  validated login credentials
     * @param response HTTP response used to attach the refresh token HttpOnly cookie
     * @return 200 OK with an {@link AuthResponse} containing the JWT access token
     */
    @PostMapping("/login") // POST /api/v1/auth/login
    @Operation(
            summary = "Login",
            description = "Authenticates with email/password. Returns a JWT access token " +
                    "in the body " +
                    "and sets a refresh token HttpOnly cookie (7-day expiry, " +
                    "rotated on each use).",
            security = {} // No JWT required for this public endpoint
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials",
                    content = @Content),
            @ApiResponse(responseCode = "422", description = "Validation failed",
                    content = @Content)
    })
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request, // Validate incoming JSON
            HttpServletResponse response // Write the refresh token cookie
    ) {
        return ResponseEntity.ok(authService.login(request, response)); // 200 + body
    }

    // -------------------------------------------------------
    // Refresh
    // -------------------------------------------------------

    /**
     * Exchanges a valid refresh token cookie for a new JWT access token.
     * The refresh token is rotated (old one deleted, new one issued as a cookie).
     *
     * @param request  HTTP request; the browser automatically sends the HttpOnly cookie
     * @param response HTTP response; the new refresh token cookie is written here
     * @return 200 OK with a new {@link AuthResponse}
     */
    @PostMapping("/refresh") // POST /api/v1/auth/refresh
    @Operation(
            summary = "Refresh access token",
            description = "Uses the HttpOnly refresh token cookie sent automatically " +
                    "by the browser " +
                    "to issue a new JWT access token and rotate the refresh token.",
            security = {} // Cookie is sent by the browser; no Bearer header required
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token refreshed successfully",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Refresh token missing, expired, " +
                    "or revoked",
                    content = @Content)
    })
    public ResponseEntity<AuthResponse> refresh(
            HttpServletRequest request, // Read the refresh token from the cookie jar
            HttpServletResponse response // Write the new refresh token cookie
    ) {
        return ResponseEntity.ok(authService.refresh(request, response)); // 200 + body
    }

    // -------------------------------------------------------
    // Logout
    // -------------------------------------------------------

    /**
     * Revokes the user's refresh tokens and clears the HttpOnly cookie.
     *
     * @param request  HTTP request; refresh token cookie read to identify the session
     * @param response HTTP response; cookie is cleared with max-age=0
     * @return 204 No Content on success
     */
    @PostMapping("/logout") // POST /api/v1/auth/logout
    @SecurityRequirement(name = "bearerAuth") // JWT required to identify who is logging out
    @Operation(
            summary = "Logout",
            description = "Revokes all refresh tokens for the authenticated user and clears the " +
                    "refresh token cookie. The client should discard the access token."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Logged out successfully"),
            @ApiResponse(responseCode = "401", description = "Refresh token missing or " +
                    "already revoked",
                    content = @Content)
    })
    public ResponseEntity<Void> logout(
            HttpServletRequest request, // Read refresh cookie to identify the session
            HttpServletResponse response // Clear the cookie
    ) {
        authService.logout(request, response); // Revoke tokens + clear cookie
        return ResponseEntity.noContent().build(); // 204 No Content
    }
}
