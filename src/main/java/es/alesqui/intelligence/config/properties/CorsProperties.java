package es.alesqui.intelligence.config.properties;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * CORS Configuration Properties
 * 
 * This class reads CORS configuration from application.properties and .env file.
 * Users only need to modify .env file to configure CORS for their deployment.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    /**
     * Allowed origins for CORS requests.
     * Typically includes frontend.url from .env and local development URLs.
     * Example: https://intelligence.alesqui.com,http://localhost:3000
     */
    private List<String> allowedOrigins;

    /**
     * Allowed HTTP methods for CORS requests.
     * Default: GET,POST,PUT,DELETE,OPTIONS,PATCH
     */
    private List<String> allowedMethods;

    /**
     * Allowed headers for CORS requests.
     * Use "*" to allow all headers.
     */
    private List<String> allowedHeaders;

    /**
     * Whether to allow credentials (cookies, authorization headers) in CORS requests.
     * Default: true
     */
    private Boolean allowCredentials = true;

    /**
     * Maximum age (in seconds) that the response to a preflight request can be cached.
     * Default: 3600 (1 hour)
     */
    private Long maxAge = 3600L;
}
