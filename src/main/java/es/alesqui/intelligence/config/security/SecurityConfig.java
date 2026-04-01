package es.alesqui.intelligence.config.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import es.alesqui.intelligence.config.properties.CorsProperties;
import es.alesqui.intelligence.security.JwtAuthenticationWebFilter;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * Main security configuration
 *
 * This class configures Spring Security for a reactive environment. It defines
 * the security filter chain, which URLs are public vs. private, and disables
 * session creation for a stateless, token-based authentication mechanism.
 */
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationWebFilter jwtAuthFilter;
    private final CorsProperties corsProperties;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
            // Disable CSRF protection, as it's not relevant for stateless APIs
            .csrf(ServerHttpSecurity.CsrfSpec::disable)

            // CORS Configuration
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            
            // Disable default login mechanisms not used in a stateless JWT setup
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)

            // Use a stateless security context repository (no session management)
            .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())

            // Handle authentication errors by returning a clean 401 Unauthorized or 403 Forbidden  
            .exceptionHandling(exceptionHandling ->
	            exceptionHandling.authenticationEntryPoint((exchange, ex) ->
	                Mono.fromRunnable(() -> exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED))
	            )
	            .accessDeniedHandler((exchange, denied) ->
	                Mono.fromRunnable(() -> exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN))
	            )
	        )

            // Configure authorization rules
            .authorizeExchange(exchanges -> exchanges
                // Logout requires authentication (must come before /api/auth/**)
                .pathMatchers("/api/auth/logout").authenticated()
                // Other auth endpoints are public
                .pathMatchers("/api/auth/**").permitAll()
                .pathMatchers("/api/public/**").permitAll() // Public trial registration
                // Allow health for platform health checks (Docker/Render); all other actuator endpoints require SUPERADMIN
                .pathMatchers("/actuator/health").permitAll()
                .pathMatchers("/actuator/**").hasRole("SUPERADMIN")
                .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                
                .anyExchange().authenticated() // All other requests require authentication; fine-grained roles enforced via @PreAuthorize
            )

            // Add our custom JWT filter BEFORE the AUTHENTICATION filter
            .addFilterBefore(jwtAuthFilter, SecurityWebFiltersOrder.AUTHENTICATION)            
            
            .build();
    }

    /**
     * CORS Configuration Source
     * 
     * Reads configuration from CorsProperties which is populated from application.properties
     * and .env file. Users only need to set FRONTEND_URL in .env for their deployment.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration = new CorsConfiguration();

        // Allowed origins from properties (includes FRONTEND_URL from .env)
        configuration.setAllowedOrigins(corsProperties.getAllowedOrigins());

        // Allowed methods
        configuration.setAllowedMethods(corsProperties.getAllowedMethods());

        // Allowed headers
        configuration.setAllowedHeaders(corsProperties.getAllowedHeaders());

        // Allow credentials
        configuration.setAllowCredentials(corsProperties.getAllowCredentials());
        
        // Max age
        configuration.setMaxAge(corsProperties.getMaxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}