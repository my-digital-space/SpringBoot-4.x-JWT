package com.my.jwt.config;

import io.swagger.v3.oas.models.Components; // Holds reusable OpenAPI components (security schemes)
import io.swagger.v3.oas.models.OpenAPI; // Root OpenAPI object
import io.swagger.v3.oas.models.info.Contact; // API contact information
import io.swagger.v3.oas.models.info.Info; // API metadata (title, version, description)
import io.swagger.v3.oas.models.info.License; // API licence information
import io.swagger.v3.oas.models.security.SecurityRequirement; // Applies a security scheme globally
import io.swagger.v3.oas.models.security.SecurityScheme; // Defines the Bearer JWT scheme
import org.springframework.context.annotation.Bean; // Produces a Spring-managed bean
import org.springframework.context.annotation.Configuration; // Marks as a configuration class

/**
 * Configures the SpringDoc OpenAPI bean that powers the Swagger UI
 * available at {@code /swagger-ui.html} and the raw descriptor at
 * {@code /v3/api-docs}.
 *
 * <p>A global {@code Bearer} security scheme is registered so that the
 * "Authorize" button in Swagger UI works across all protected endpoints.</p>
 */
@Configuration // Tells Spring to process the @Bean methods in this class
public class SwaggerConfig {

    /** Name of the security scheme referenced in {@link SecurityRequirement}. */
    private static final String SECURITY_SCHEME_NAME = "bearerAuth"; // Identifier used in @SecurityRequirement annotations

    /**
     * Builds and returns the customised {@link OpenAPI} descriptor.
     *
     * @return the OpenAPI bean picked up by SpringDoc
     */
    @Bean // SpringDoc auto-detects this bean and uses it to build the API docs
    public OpenAPI openAPI() {
        return new OpenAPI()
                // -------------------------------------------------------
                // API metadata shown in Swagger UI header
                // -------------------------------------------------------
                .info(new Info()
                        .title("JWT Auth — E-Commerce API") // Swagger UI page title
                        .description("Production-grade Spring Boot 4 REST API with JWT authentication, " +
                                "refresh token rotation, and role-based access control.") // Short description
                        .version("v1.0.0") // API version displayed in the UI
                        .contact(new Contact()
                                .name("My Digital Space") // Developer / team name
                                .email("admin@jwt-auth-app.com") // Contact email
                        )
                        .license(new License()
                                .name("MIT License") // Licence name
                                .url("https://opensource.org/licenses/MIT") // Licence URL
                        )
                )
                // -------------------------------------------------------
                // Global Bearer JWT security scheme
                // -------------------------------------------------------
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME) // Internal scheme name
                                .type(SecurityScheme.Type.HTTP) // HTTP-based scheme (not API-key / OAuth2)
                                .scheme("bearer") // Bearer token scheme
                                .bearerFormat("JWT") // Informs Swagger UI this is a JWT bearer token
                                .description("Paste your JWT access token here. " +
                                        "Obtain one via POST /api/v1/auth/login.") // Hint shown in the UI
                        )
                )
                // Apply the Bearer scheme globally so every operation shows the lock icon
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME)); // Global security requirement
    }
}
