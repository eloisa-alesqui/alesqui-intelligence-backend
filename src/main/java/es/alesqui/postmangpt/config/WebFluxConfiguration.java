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
 * This configuration class sets up CORS policies, multipart handling,
 * and other WebFlux-specific settings for the PostmanGPT application.
 */
@Configuration
@EnableWebFlux
@RequiredArgsConstructor
public class WebFluxConfiguration implements WebFluxConfigurer {

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:8080}")
    private String[] allowedOrigins;

    @Value("${app.cors.allowed-methods:GET,POST,PUT,DELETE,OPTIONS,PATCH}")
    private String[] allowedMethods;

    @Value("${app.cors.allowed-headers:*}")
    private String[] allowedHeaders;

    @Value("${app.cors.allow-credentials:true}")
    private boolean allowCredentials;

    @Value("${app.cors.max-age:3600}")
    private long maxAge;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins(allowedOrigins)
            .allowedMethods(allowedMethods)
            .allowedHeaders(allowedHeaders)
            .allowCredentials(allowCredentials)
            .maxAge(maxAge);
        
        // Also allow CORS for actuator endpoints
        registry.addMapping("/actuator/**")
            .allowedOrigins(allowedOrigins)
            .allowedMethods("GET", "POST", "OPTIONS")
            .allowedHeaders(allowedHeaders)
            .allowCredentials(allowCredentials)
            .maxAge(maxAge);
    }
}