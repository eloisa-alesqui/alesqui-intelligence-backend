package es.alesqui.postmangpt.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * WebFlux configuration for CORS and other web-related settings.
 * 
 * This configuration class sets up CORS (Cross-Origin Resource Sharing) policies,
 * multipart handling, and other WebFlux-specific settings for the PostmanGPT application.
 * 
 * The configuration enables cross-origin requests from specified origins with
 * customizable methods, headers, and credentials handling. It provides separate
 * CORS configurations for API endpoints and actuator endpoints with different
 * security considerations.
 * 
 * Key features:
 * - Configurable allowed origins, methods, and headers
 * - Support for credentials in CORS requests
 * - Separate configuration for API and actuator endpoints
 * - Customizable cache duration for preflight requests
 */
@Configuration
@EnableWebFlux
@RequiredArgsConstructor
public class WebFluxConfiguration implements WebFluxConfigurer {

    /**
     * List of allowed origins for CORS requests.
     * 
     * Specifies which domains are permitted to make cross-origin requests
     * to this application. Multiple origins can be specified separated by commas.
     * 
     * Default value includes common development origins:
     * - http://localhost:3000 (typical React development server)
     * - http://localhost:8080 (typical Spring Boot development server)
     * 
     * Expected property: app.cors.allowed-origins
     */
    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:8080}")
    private String[] allowedOrigins;

    /**
     * List of allowed HTTP methods for CORS requests.
     * 
     * Defines which HTTP methods are permitted for cross-origin requests.
     * This includes standard REST operations and the OPTIONS method for
     * preflight requests.
     * 
     * Default methods: GET, POST, PUT, DELETE, OPTIONS, PATCH
     * 
     * Expected property: app.cors.allowed-methods
     */
    @Value("${app.cors.allowed-methods:GET,POST,PUT,DELETE,OPTIONS,PATCH}")
    private String[] allowedMethods;

    /**
     * List of allowed headers for CORS requests.
     * 
     * Specifies which headers can be used during cross-origin requests.
     * The wildcard "*" allows all headers, which is convenient for development
     * but should be restricted in production environments for security.
     * 
     * Default value: * (all headers allowed)
     * 
     * Expected property: app.cors.allowed-headers
     */
    @Value("${app.cors.allowed-headers:*}")
    private String[] allowedHeaders;

    /**
     * Flag to enable or disable credentials in CORS requests.
     * 
     * When set to true, allows the browser to include credentials (cookies,
     * authorization headers, or TLS client certificates) in cross-origin requests.
     * This is essential for applications that require authentication across origins.
     * 
     * Note: When allowCredentials is true, allowedOrigins cannot be "*" for security reasons.
     * 
     * Default value: true
     * 
     * Expected property: app.cors.allow-credentials
     */
    @Value("${app.cors.allow-credentials:true}")
    private boolean allowCredentials;

    /**
     * Maximum age in seconds for preflight request caching.
     * 
     * Specifies how long the browser can cache the results of a preflight request
     * (OPTIONS request) before sending another preflight request for the same
     * cross-origin request configuration.
     * 
     * A longer maxAge reduces the number of preflight requests, improving performance,
     * but may delay the application of CORS configuration changes.
     * 
     * Default value: 3600 seconds (1 hour)
     * 
     * Expected property: app.cors.max-age
     */
    @Value("${app.cors.max-age:3600}")
    private long maxAge;

    /**
     * Configures CORS mappings for the application endpoints.
     * 
     * This method sets up two distinct CORS configurations:
     * 
     * 1. API Endpoints (/api/**):
     *    - Full CORS configuration with all specified methods and headers
     *    - Supports all configured HTTP methods including REST operations
     *    - Uses all configurable CORS settings from application properties
     * 
     * 2. Actuator Endpoints (/actuator/**):
     *    - Restricted CORS configuration for monitoring endpoints
     *    - Limited to GET, POST, and OPTIONS methods for security
     *    - Uses the same origins and headers as API endpoints
     *    - Maintains credentials support for authenticated monitoring
     * 
     * The separation allows for different security policies between business
     * logic endpoints and operational monitoring endpoints.
     * 
     * @param registry the CORS registry to configure mappings on
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Configure CORS for main API endpoints
        registry.addMapping("/api/**")
            .allowedOrigins(allowedOrigins)
            .allowedMethods(allowedMethods)
            .allowedHeaders(allowedHeaders)
            .allowCredentials(allowCredentials)
            .maxAge(maxAge);
        
        // Configure CORS for actuator endpoints with restricted methods
        // Only allowing GET, POST, and OPTIONS for security reasons
        registry.addMapping("/actuator/**")
            .allowedOrigins(allowedOrigins)
            .allowedMethods("GET", "POST", "OPTIONS")
            .allowedHeaders(allowedHeaders)
            .allowCredentials(allowCredentials)
            .maxAge(maxAge);
    }
}