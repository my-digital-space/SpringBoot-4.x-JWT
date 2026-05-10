package com.my.jwt.service;

import com.my.jwt.config.ApplicationProperties; // Reads JWT config (expiry, cookie name)
import com.my.jwt.dto.AuthResponse; // Response DTO returned to the client
import com.my.jwt.dto.LoginRequest; // Login request DTO
import com.my.jwt.dto.RegisterRequest; // Registration request DTO
import com.my.jwt.entity.RefreshToken; // Refresh token entity
import com.my.jwt.entity.User; // User entity
import com.my.jwt.enums.Role; // Role enum (ADMIN, MANAGER, USER)
import com.my.jwt.exception.ApiException; // Application exception with HTTP status
import com.my.jwt.repository.UserRepository; // User CRUD operations
import com.my.jwt.security.JwtService; // Generates and validates JWT access tokens
import jakarta.servlet.http.Cookie; // HttpOnly cookie for the refresh token
import jakarta.servlet.http.HttpServletRequest; // Read cookies from incoming request
import jakarta.servlet.http.HttpServletResponse; // Write cookies to outgoing response
import lombok.RequiredArgsConstructor; // Lombok: constructor injection
import org.springframework.http.HttpStatus; // HTTP status constants
import org.springframework.security.authentication.AuthenticationManager; // Authenticates login credentials
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken; // Credential wrapper
import org.springframework.security.crypto.password.PasswordEncoder; // BCrypt password hashing
import org.springframework.stereotype.Service; // Spring service bean
import org.springframework.transaction.annotation.Transactional; // DB transaction management

import java.util.Arrays; // Used to scan the cookie array from the request

/**
 * Service containing all authentication business logic:
 * <ul>
 *   <li>Register new users</li>
 *   <li>Login (authenticate + issue JWT + issue refresh cookie)</li>
 *   <li>Refresh (rotate refresh token + issue new JWT)</li>
 *   <li>Logout (revoke all refresh tokens)</li>
 * </ul>
 */
@Service // Spring-managed bean
@RequiredArgsConstructor // Lombok: injects all final fields
public class AuthService {

    /** CRUD operations for User entities. */
    private final UserRepository userRepository;

    /** Hashes new passwords and verifies existing ones. */
    private final PasswordEncoder passwordEncoder;

    /** Generates and validates JWT access tokens. */
    private final JwtService jwtService;

    /** Issues, validates, rotates, and revokes refresh tokens. */
    private final RefreshTokenService refreshTokenService;

    /** Authenticates username/password credentials during login. */
    private final AuthenticationManager authenticationManager;

    /** JWT configuration values from application.yaml. */
    private final ApplicationProperties props;

    // -------------------------------------------------------
    // Register
    // -------------------------------------------------------

    /**
     * Registers a new user with the {@link Role#USER} role.
     *
     * @param request validated registration data
     * @param response HTTP response used to attach the refresh token cookie
     * @return an {@link AuthResponse} containing the new JWT access token
     */
    @Transactional // Wraps the DB write in a transaction
    public AuthResponse register(RegisterRequest request, HttpServletResponse response) {
        // Reject duplicate emails before attempting to insert
        if (userRepository.existsByEmail(request.email())) {
            throw new ApiException("Email is already registered", HttpStatus.CONFLICT); // 409 Conflict
        }

        // Build and persist the new user — password is hashed before storage
        User user = User.builder()
                .firstName(request.firstName()) // From request DTO
                .lastName(request.lastName()) // From request DTO
                .email(request.email()) // Used as the login identifier
                .password(passwordEncoder.encode(request.password())) // BCrypt hash; never plain text
                .role(Role.USER) // New registrations always start as USER role
                .build();
        userRepository.save(user); // INSERT INTO users ...

        // Issue tokens for the newly registered user (auto-login after registration)
        return issueTokens(user, response); // Generate JWT + refresh cookie
    }

    // -------------------------------------------------------
    // Login
    // -------------------------------------------------------

    /**
     * Authenticates a user with email/password and issues a JWT + refresh cookie.
     *
     * @param request  validated login credentials
     * @param response HTTP response used to attach the refresh token cookie
     * @return an {@link AuthResponse} containing the JWT access token
     */
    @Transactional // Wraps DB reads/writes in a transaction
    public AuthResponse login(LoginRequest request, HttpServletResponse response) {
        // Delegate credential verification to Spring Security's AuthenticationManager
        // This triggers DaoAuthenticationProvider → UserDetailsServiceImpl → BCrypt compare
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        ); // Throws AuthenticationException if credentials are wrong

        // Credentials are valid — load the full user record
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ApiException("User not found", HttpStatus.NOT_FOUND)); // Should never reach here

        // Revoke any existing refresh tokens before issuing new ones (single-session enforcement)
        refreshTokenService.revokeAllUserTokens(user); // DELETE old tokens

        return issueTokens(user, response); // Issue new JWT + refresh cookie
    }

    // -------------------------------------------------------
    // Refresh
    // -------------------------------------------------------

    /**
     * Exchanges a valid refresh token (from the HttpOnly cookie) for a new
     * JWT access token and rotates the refresh token.
     *
     * @param request  HTTP request; refresh token is read from the cookie jar
     * @param response HTTP response; new refresh cookie is written here
     * @return an {@link AuthResponse} containing the new JWT access token
     */
    @Transactional // Wraps token validation + rotation in a single transaction
    public AuthResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        // Extract the refresh token string from the HttpOnly cookie
        String tokenString = extractRefreshCookie(request); // Throws 401 if cookie is missing

        // Validate the token (not expired, not revoked, exists in DB)
        RefreshToken oldToken = refreshTokenService.verifyRefreshToken(tokenString); // Throws 401 if invalid

        // Rotate: delete old token, persist new token (prevents replay attacks)
        RefreshToken newToken = refreshTokenService.rotateRefreshToken(oldToken); // Returns new token entity

        // Set the new refresh token as an HttpOnly cookie
        setRefreshTokenCookie(response, newToken.getToken()); // Replaces the old cookie

        // Generate and return a new JWT access token for the same user
        String accessToken = jwtService.generateAccessToken(oldToken.getUser()); // New JWT
        return AuthResponse.of(accessToken, props.getAccessTokenExpiration()); // Wrap in response DTO
    }

    // -------------------------------------------------------
    // Logout
    // -------------------------------------------------------

    /**
     * Revokes the user's refresh tokens and clears the HttpOnly cookie.
     *
     * @param request  HTTP request; refresh token read from cookie to identify the user
     * @param response HTTP response; cookie is cleared
     */
    @Transactional // Wraps DB delete in a transaction
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String tokenString = extractRefreshCookie(request); // Read the cookie value

        // Validate the token and use it to identify the owning user
        RefreshToken token = refreshTokenService.verifyRefreshToken(tokenString); // Throws 401 if invalid
        refreshTokenService.revokeAllUserTokens(token.getUser()); // Delete all tokens for this user

        clearRefreshTokenCookie(response); // Set an expired cookie to delete it from the browser
    }

    // -------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------

    /**
     * Generates a JWT access token and a refresh token, sets the refresh token
     * as an HttpOnly cookie, and returns the {@link AuthResponse}.
     */
    private AuthResponse issueTokens(User user, HttpServletResponse response) {
        String accessToken = jwtService.generateAccessToken(user); // Sign the JWT for this user
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user); // Persist new token
        setRefreshTokenCookie(response, refreshToken.getToken()); // Deliver via HttpOnly cookie
        return AuthResponse.of(accessToken, props.getAccessTokenExpiration()); // Return to controller
    }

    /**
     * Writes the refresh token value into an HttpOnly, Secure, SameSite=Strict cookie.
     *
     * @param response    the HTTP response to add the cookie to
     * @param tokenValue  the raw refresh token string
     */
    private void setRefreshTokenCookie(HttpServletResponse response, String tokenValue) {
        Cookie cookie = new Cookie(props.getRefreshTokenCookieName(), tokenValue); // Cookie name from config
        cookie.setHttpOnly(true); // Prevents JavaScript access — key XSS protection
        cookie.setSecure(true); // Only sent over HTTPS; set to false for local HTTP dev if needed
        cookie.setPath("/api/v1/auth"); // Scope the cookie so it is only sent to auth endpoints
        cookie.setMaxAge((int) (props.getRefreshTokenExpiration() / 1000)); // Cookie TTL in seconds
        response.addCookie(cookie); // Attach to the response via Set-Cookie header
    }

    /**
     * Clears the refresh token cookie by setting its max age to 0.
     *
     * @param response the HTTP response to clear the cookie on
     */
    private void clearRefreshTokenCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(props.getRefreshTokenCookieName(), ""); // Empty value
        cookie.setHttpOnly(true); // Same flags as the original cookie
        cookie.setSecure(true); // Must match the original cookie attributes
        cookie.setPath("/api/v1/auth"); // Must match the original path
        cookie.setMaxAge(0); // Age of 0 tells the browser to delete the cookie immediately
        response.addCookie(cookie); // Replace the existing cookie with the expired one
    }

    /**
     * Reads the refresh token string from the incoming request's cookie jar.
     *
     * @param request the HTTP request
     * @return the raw token string
     * @throws ApiException with 401 if the cookie is absent
     */
    private String extractRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies(); // Get all cookies from the request
        if (cookies == null) {
            throw new ApiException("Refresh token cookie is missing", HttpStatus.UNAUTHORIZED); // No cookies at all
        }
        // Find the cookie with the configured name and return its value
        return Arrays.stream(cookies) // Stream over all cookies
                .filter(c -> props.getRefreshTokenCookieName().equals(c.getName())) // Match by name
                .map(Cookie::getValue) // Extract the value
                .findFirst() // Take the first match
                .orElseThrow(() -> new ApiException("Refresh token cookie is missing", HttpStatus.UNAUTHORIZED)); // Not found
    }
}
