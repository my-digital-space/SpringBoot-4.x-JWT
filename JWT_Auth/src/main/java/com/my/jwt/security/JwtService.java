package com.my.jwt.security;

import com.my.jwt.config.ApplicationProperties; // Reads JWT config from application.yaml
import io.jsonwebtoken.Claims; // Represents all claims inside a JWT
import io.jsonwebtoken.Jwts; // JJWT fluent API entry point
import io.jsonwebtoken.security.Keys; // Utility to derive a signing key from raw bytes
import lombok.RequiredArgsConstructor; // Lombok: generates constructor for final fields
import org.springframework.security.core.userdetails.UserDetails; // Spring Security user contract
import org.springframework.stereotype.Service; // Marks as a Spring service bean

import javax.crypto.SecretKey; // Symmetric HMAC signing key
import java.util.Date; // JWT exp / iat claims use java.util.Date
import java.util.HexFormat; // Decodes the hex-encoded secret key string
import java.util.Map; // Extra claims map
import java.util.function.Function; // Generic claims extractor

/**
 * Service responsible for generating, signing, and validating JWT access tokens.
 *
 * <p>This class is <em>stateless</em>: it never reads from the database.
 * Refresh token lifecycle (storage, rotation, revocation) is handled by
 * {@link com.my.jwt.service.RefreshTokenService}.</p>
 */
@Service // Spring bean
@RequiredArgsConstructor // Lombok: injects ApplicationProperties via constructor
public class JwtService {

    /** Loaded from {@code application.security.jwt.*} YAML properties. */
    private final ApplicationProperties props; // Injected configuration

    // -------------------------------------------------------
    // Token generation
    // -------------------------------------------------------

    /**
     * Generates a signed access token for the given user.
     *
     * @param userDetails Spring Security user loaded from the database
     * @return compact, URL-safe JWT string (header.payload.signature)
     */
    public String generateAccessToken(UserDetails userDetails) {
        // Delegate to the overload with an empty extra-claims map
        return generateAccessToken(Map.of(), userDetails);
    }

    /**
     * Generates a signed access token with additional custom claims.
     *
     * @param extraClaims map of additional claims to embed in the payload
     * @param userDetails Spring Security user
     * @return compact JWT string
     */
    public String generateAccessToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        long nowMs = System.currentTimeMillis(); // Current epoch milliseconds
        return Jwts.builder() // Start building the JWT
                .claims(extraClaims) // Embed any caller-supplied extra claims
                .subject(userDetails.getUsername()) // sub claim = user's email
                .issuer(props.getIssuer()) // iss claim from application.yaml
                .issuedAt(new Date(nowMs)) // iat claim = now
                .expiration(new Date(nowMs + props.getAccessTokenExpiration())) // exp = now + 10 min
                .signWith(signingKey()) // Sign with HMAC-SHA256
                .compact(); // Produce the final header.payload.signature string
    }

    // -------------------------------------------------------
    // Token validation
    // -------------------------------------------------------

    /**
     * Validates a token against the given user details.
     *
     * @param token       JWT string from the Authorization header
     * @param userDetails user loaded from the database
     * @return {@code true} if the token is genuine, non-expired, and belongs to this user
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        String subject = extractSubject(token); // Extract the sub (email) claim
        // Token is valid if the subject matches AND the token is not expired
        return subject.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    /**
     * Returns {@code true} if the token's expiry timestamp is in the past.
     *
     * @param token JWT string
     * @return whether the token has expired
     */
    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date()); // Compare exp claim to current time
    }

    // -------------------------------------------------------
    // Claims extraction helpers
    // -------------------------------------------------------

    /**
     * Extracts the subject (email / username) from the token.
     *
     * @param token JWT string
     * @return the {@code sub} claim value
     */
    public String extractSubject(String token) {
        return extractClaim(token, Claims::getSubject); // Read sub claim
    }

    /**
     * Generic single-claim extractor.
     *
     * @param <T>            expected claim value type
     * @param token          JWT string
     * @param claimsResolver function that reads one claim from the {@link Claims} object
     * @return the extracted claim value
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims = extractAllClaims(token); // Parse and verify signature + expiry
        return claimsResolver.apply(claims); // Apply the caller-supplied extractor function
    }

    // -------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------

    /** Parses the token, verifies the signature, and returns all claims. */
    private Claims extractAllClaims(String token) {
        return Jwts.parser() // Create a JWT parser
                .verifyWith(signingKey()) // Set the verification key (same as signing key for HMAC)
                .build() // Build the parser
                .parseSignedClaims(token) // Parse and verify; throws on invalid/expired token
                .getPayload(); // Return just the claims object
    }

    /** Extracts the expiration date from the token. */
    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration); // Read exp claim
    }

    /**
     * Decodes the hex-encoded secret key from {@code application.yaml} and
     * derives an HMAC-SHA256 {@link SecretKey}.
     */
    private SecretKey signingKey() {
        byte[] keyBytes = HexFormat.of().parseHex(props.getSecretKey()); // Decode hex string to bytes
        return Keys.hmacShaKeyFor(keyBytes); // Derive a SecretKey suitable for HMAC-SHA256
    }
}
