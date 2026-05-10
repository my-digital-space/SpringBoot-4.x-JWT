package com.my.jwt.config;

import com.my.jwt.security.JwtAuthenticationFilter; // Custom JWT filter added before the default auth filter
import lombok.RequiredArgsConstructor; // Lombok: constructor injection
import org.springframework.context.annotation.Bean; // Produces Spring-managed beans
import org.springframework.context.annotation.Configuration; // Marks as a configuration class
import org.springframework.http.HttpMethod; // Used to allow specific HTTP methods on public paths
import org.springframework.security.authentication.AuthenticationManager; // Manages the auth process
import org.springframework.security.authentication.AuthenticationProvider; // Provides authentication logic
import org.springframework.security.authentication.dao.DaoAuthenticationProvider; // DB-backed auth provider
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration; // Exposes AuthenticationManager
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity; // Enables @PreAuthorize
import org.springframework.security.config.annotation.web.builders.HttpSecurity; // Configures HTTP security rules
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity; // Activates Spring Security
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer; // Helper to disable CSRF etc.
import org.springframework.security.config.http.SessionCreationPolicy; // Controls session creation
import org.springframework.security.core.userdetails.UserDetailsService; // Loads users from the database
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder; // BCrypt password hashing
import org.springframework.security.crypto.password.PasswordEncoder; // Password encoder contract
import org.springframework.security.web.SecurityFilterChain; // The HTTP security filter chain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter; // Standard auth filter replaced by JWT

/**
 * Spring Security configuration for the JWT-authenticated REST API.
 *
 * <p>Key decisions:</p>
 * <ul>
 *   <li>Session management is {@code STATELESS} — no server-side sessions.</li>
 *   <li>CSRF is disabled — not needed for stateless token-based APIs.</li>
 *   <li>{@link JwtAuthenticationFilter} runs before the default username/password filter.</li>
 *   <li>Method-level security ({@code @PreAuthorize}) is enabled for role checks.</li>
 * </ul>
 */
@Configuration // Tells Spring this class declares @Bean methods
@EnableWebSecurity // Activates Spring Security's web security support
@EnableMethodSecurity // Enables @PreAuthorize / @PostAuthorize on controller methods
@RequiredArgsConstructor // Lombok: injects final fields via constructor
public class SecurityConfig {

    /** Custom JWT filter; intercepts every request to extract and validate the bearer token. */
    private final JwtAuthenticationFilter jwtAuthFilter; // Injected by Spring

    /** Loads user details (including password hash and roles) from the database. */
    private final UserDetailsService userDetailsService; // Implemented by UserDetailsServiceImpl

    // -------------------------------------------------------
    // Public paths that require no authentication
    // -------------------------------------------------------

    /** Auth endpoints that must be accessible without a valid JWT. */
    private static final String[] PUBLIC_POST_PATHS = {
            "/api/v1/auth/register", // User registration
            "/api/v1/auth/login",    // Login — issues JWT + refresh cookie
            "/api/v1/auth/refresh",  // Refresh — exchanges refresh cookie for new JWT
            "/api/v1/auth/logout"    // Logout — revokes refresh token (cookie sent by browser)
    };

    /** Swagger / OpenAPI UI paths that should be accessible without authentication. */
    private static final String[] SWAGGER_PATHS = {
            "/swagger-ui/**",        // Swagger UI static assets
            "/swagger-ui.html",      // Swagger UI entry point
            "/v3/api-docs/**",       // OpenAPI JSON/YAML descriptor
            "/v3/api-docs.yaml"      // OpenAPI YAML download
    };

    /**
     * Configures the main HTTP security filter chain.
     *
     * @param http the {@link HttpSecurity} builder provided by Spring Security
     * @return the configured {@link SecurityFilterChain}
     * @throws Exception if any security configuration step fails
     */
    @Bean // Registers the SecurityFilterChain as a Spring bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF protection — not needed for stateless JWT APIs
                // (no cookies holding session tokens)
            .csrf(AbstractHttpConfigurer::disable)

            // Define which requests need authentication
            .authorizeHttpRequests(auth -> auth
                // Allow Swagger UI and OpenAPI docs without a JWT
                .requestMatchers(SWAGGER_PATHS).permitAll()

                // Allow auth endpoints (register, login, refresh, logout) without a JWT
                .requestMatchers(HttpMethod.POST, PUBLIC_POST_PATHS).permitAll()

                // All other requests must carry a valid JWT
                .anyRequest().authenticated()
            )

            // Use stateless session management — no HttpSession created or used
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS) // No server-side sessions
            )

            // Register the DB-backed authentication provider (BCrypt + UserDetailsService)
            .authenticationProvider(authenticationProvider())

            // Insert the JWT filter before Spring's default UsernamePasswordAuthenticationFilter
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build(); // Build and return the configured chain
    }

    /**
     * Configures a {@link DaoAuthenticationProvider} that loads users from the database
     * and verifies passwords using BCrypt.
     *
     * @return the configured {@link AuthenticationProvider}
     */
    @Bean // Exposed as a bean so it can be injected elsewhere if needed
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService); // Constructor now requires UserDetailsService in Spring Security 6.2+
        provider.setPasswordEncoder(passwordEncoder()); // Tells the provider how to verify passwords
        return provider; // Returned and registered with the filter chain above
    }

    /**
     * Exposes the {@link AuthenticationManager} so it can be injected into
     * {@link com.my.jwt.service.AuthService} for programmatic authentication.
     *
     * @param config Spring's {@link AuthenticationConfiguration}
     * @return the application's {@link AuthenticationManager}
     * @throws Exception if the manager cannot be built
     */
    @Bean // Required by AuthService to authenticate login credentials
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager(); // Delegates to Spring's auto-configured manager
    }

    /**
     * BCrypt password encoder with the default strength factor (10 rounds).
     *
     * @return a {@link PasswordEncoder} bean shared across the application
     */
    @Bean // Shared bean — used in AuthService for hashing and DaoAuthenticationProvider for verifying
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(); // BCrypt with cost factor 10 (default); ~100 ms per hash
    }
}
