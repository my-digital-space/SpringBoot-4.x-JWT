package com.my.jwt.security;

import jakarta.servlet.FilterChain; // Passes the request to the next filter
import jakarta.servlet.ServletException; // Checked exception from the servlet API
import jakarta.servlet.http.HttpServletRequest; // Incoming HTTP request
import jakarta.servlet.http.HttpServletResponse; // Outgoing HTTP response
import lombok.RequiredArgsConstructor; // Lombok: constructor injection for final fields
import org.springframework.lang.NonNull; // Documents non-null parameters
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken; // Authentication object
import org.springframework.security.core.context.SecurityContextHolder; // Thread-local security context
import org.springframework.security.core.userdetails.UserDetails; // Spring Security user contract
import org.springframework.security.core.userdetails.UserDetailsService; // Loads user from DB
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource; // Builds request details
import org.springframework.stereotype.Component; // Registers as a Spring bean
import org.springframework.web.filter.OncePerRequestFilter; // Guarantees one execution per request

import java.io.IOException; // Checked exception from I/O operations

/**
 * Servlet filter that intercepts every request, extracts the JWT from the
 * {@code Authorization: Bearer <token>} header, validates it, and populates
 * the {@link SecurityContextHolder} so downstream filters and controllers know
 * who the authenticated user is.
 *
 * <p>Extending {@link OncePerRequestFilter} guarantees the filter runs exactly
 * once per request, even in the presence of internal forwards.</p>
 */
@Component // Registers this filter as a Spring-managed bean
@RequiredArgsConstructor // Lombok: generates a constructor for the two final fields
public class JwtAuthenticationFilter extends OncePerRequestFilter { // One execution per HTTP request

    /** Extracts and validates JWT tokens. */
    private final JwtService jwtService; // Stateless JWT utility

    /** Loads the full user record from the database by email. */
    private final UserDetailsService userDetailsService; // Provided by Spring Security

    /**
     * Core filter logic: validates the JWT and authenticates the user in the
     * security context for the duration of this request.
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,   // Incoming HTTP request
            @NonNull HttpServletResponse response, // Outgoing HTTP response
            @NonNull FilterChain filterChain        // Next filter in the chain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization"); // Read the Authorization header

        // Skip JWT processing if the header is missing or doesn't start with "Bearer "
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response); // Pass to the next filter unchanged
            return; // Stop processing this filter
        }

        String jwt = authHeader.substring(7); // Strip "Bearer " prefix to get the raw token
        String userEmail = jwtService.extractSubject(jwt); // Extract sub (email) claim from token

        // Only authenticate if we extracted a subject AND there is no authentication in the context yet
        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            // Load the full user record so Spring Security can check roles and enabled status
            UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);

            // Validate the token against this user (checks signature, expiry, and subject match)
            if (jwtService.isTokenValid(jwt, userDetails)) {

                // Build an authenticated token with authorities from the user record
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,          // Principal (the UserDetails object)
                                null,                 // Credentials — null because JWT is already validated
                                userDetails.getAuthorities() // Roles/authorities from User.getAuthorities()
                        );

                // Attach request details (remote IP, session ID) to the authentication object
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // Store the authentication in the thread-local security context for this request
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        filterChain.doFilter(request, response); // Continue to the next filter / controller
    }
}
