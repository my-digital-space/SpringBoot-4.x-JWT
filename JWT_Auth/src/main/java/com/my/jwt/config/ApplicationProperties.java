package com.my.jwt.config;

import lombok.Getter; // Generates getters for all fields
import lombok.Setter; // Generates setters for all fields
import org.springframework.boot.context.properties.ConfigurationProperties; // Binds YAML prefix to this class
import org.springframework.stereotype.Component; // Registers as a Spring bean

/**
 * Strongly-typed binding for all {@code application.security.jwt.*} properties
 * defined in {@code application.yaml}.
 *
 * <p>Injecting this class instead of using {@code @Value} keeps configuration
 * centralised and refactor-safe.</p>
 */
@Getter // Lombok: generates getters so Spring can bind nested properties
@Setter // Lombok: generates setters required by Spring's property binder
@Component // Makes this class available for dependency injection
@ConfigurationProperties(prefix = "application.security.jwt") // Binds properties under this YAML prefix
public class ApplicationProperties {

    /** 256-bit hex-encoded HMAC-SHA256 signing key loaded from {@code JWT_SECRET_KEY} env var. */
    private String secretKey; // maps to application.security.jwt.secret-key

    /** Access token lifetime in milliseconds (default 600 000 ms = 10 minutes). */
    private long accessTokenExpiration; // maps to application.security.jwt.access-token-expiration

    /** Refresh token lifetime in milliseconds (default 604 800 000 ms = 7 days). */
    private long refreshTokenExpiration; // maps to application.security.jwt.refresh-token-expiration

    /** Name of the HttpOnly cookie used to transport the refresh token. */
    private String refreshTokenCookieName; // maps to application.security.jwt.refresh-token-cookie-name

    /** Value placed in the JWT {@code iss} (issuer) claim. */
    private String issuer; // maps to application.security.jwt.issuer
}
