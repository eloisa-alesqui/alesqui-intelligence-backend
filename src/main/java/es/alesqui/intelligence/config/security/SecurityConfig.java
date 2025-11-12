package es.alesqui.intelligence.config.security;

import java.util.List;

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
                .pathMatchers("/api/auth/**").permitAll() // Public auth endpoints
                .pathMatchers("/api/test/**").permitAll()
                // Allow health/info for platform health checks (Render)
                .pathMatchers("/actuator/health", "/actuator/info").permitAll()
                .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                
                .pathMatchers(HttpMethod.GET, "/api/swagger/**").hasAnyRole("IT", "SUPERADMIN") 
                .pathMatchers(HttpMethod.POST, "/api/swagger/**").hasAnyRole("IT", "SUPERADMIN") 
                .pathMatchers(HttpMethod.DELETE, "/api/swagger/**").hasAnyRole("IT", "SUPERADMIN") 
                
                .pathMatchers(HttpMethod.GET, "/api/postman/**").hasAnyRole("IT", "SUPERADMIN") 
                .pathMatchers(HttpMethod.POST, "/api/postman/**").hasAnyRole("IT", "SUPERADMIN") 
                .pathMatchers(HttpMethod.DELETE, "/api/postman/**").hasAnyRole("IT", "SUPERADMIN") 
                
                .pathMatchers(HttpMethod.POST, "/api/unification/unify").hasAnyRole("IT", "SUPERADMIN") 
                .pathMatchers(HttpMethod.PUT, "/api/unification/*/configuration").hasAnyRole("IT", "SUPERADMIN") 
                .pathMatchers(HttpMethod.DELETE, "/api/unification/**").hasAnyRole("IT", "SUPERADMIN") 
                
                .pathMatchers(HttpMethod.GET, "/api/diagnostics/**").hasAnyRole("IT", "SUPERADMIN") 
                .pathMatchers(HttpMethod.POST, "/api/diagnostics/**").hasAnyRole("IT", "SUPERADMIN") 
                .pathMatchers(HttpMethod.PUT, "/api/diagnostics/**").hasAnyRole("IT", "SUPERADMIN") 
                
                // Administrative endpoints restricted to SUPERADMIN role
                .pathMatchers("/api/admin/**").hasRole("SUPERADMIN")
                
                .anyExchange().authenticated() // All other requests require authentication
            )

            // Add our custom JWT filter BEFORE the AUTHENTICATION filter
            .addFilterBefore(jwtAuthFilter, SecurityWebFiltersOrder.AUTHENTICATION)            
            
            .build();
    }

    /**
     * CORS Configuration Source
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration = new CorsConfiguration();

        // Allowed origins patterns
        configuration.setAllowedOriginPatterns(List.of("http://localhost:*", "https://localhost:*",
                "https://*.alesqui.es", "https://*.vercel.app"));

        // Allowed methods
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // Allowed headers
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin",
                "Access-Control-Request-Method", "Access-Control-Request-Headers", "X-Request-ID", "X-Correlation-ID"));

        // Allow credentials
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}