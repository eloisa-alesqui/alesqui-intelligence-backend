package es.alesqui.intelligence.config.security;

import es.alesqui.intelligence.security.JwtAuthenticationWebFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

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
                // Disable CSRF protection, as it's not as relevant for stateless APIs
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                
                // Use a stateless security context repository
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())

                // Configure authorization rules
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/api/auth/**").permitAll() // Public auth endpoints
                        .anyExchange().authenticated() // All other requests require authentication
                )

                // Add our custom JWT filter at the correct position in the chain
                .addFilterAt(jwtAuthFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                
                .build();
    }
}










//package es.alesqui.intelligence.config.security;
//
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.http.HttpMethod;
//import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
//import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
//import org.springframework.security.config.web.server.ServerHttpSecurity;
//import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
//import org.springframework.security.crypto.password.PasswordEncoder;
//import org.springframework.security.web.server.SecurityWebFilterChain;
//import org.springframework.web.cors.CorsConfiguration;
//import org.springframework.web.cors.reactive.CorsConfigurationSource;
//import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
//
//import java.util.List;
//
///**
// * 🔐 Main Security Configuration for Alesqui Intelligence
// * 
// * Features: - JWT-based authentication (to be added) - Stateless session
// * management - CORS configuration - Method-level security - Basic security
// * setup
// */
//@Slf4j
//@Configuration
//@EnableWebFluxSecurity
//@EnableReactiveMethodSecurity
//@RequiredArgsConstructor
//public class SecurityConfig {
//
//	/**
//	 * 🛡️ Main Security Filter Chain Configuration
//	 */
//	@Bean
//	public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
//		log.info("🔧 Configuring Security Filter Chain");
//
//		return http
//				// 🚫 Disable unnecessary features for stateless API
//				.csrf(csrf -> csrf.disable()).formLogin(formLogin -> formLogin.disable())
//				.httpBasic(httpBasic -> httpBasic.disable()).logout(logout -> logout.disable())
//
//				// 🔄 CORS Configuration
//				.cors(cors -> cors.configurationSource(corsConfigurationSource()))
//
//				// 🛣️ Authorization rules
//				.authorizeExchange(exchanges -> exchanges
////						// 🌐 Public endpoints
////						.pathMatchers(HttpMethod.GET, "/api/health", "/api/info").permitAll()
////						.pathMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register").permitAll()
////						.pathMatchers(HttpMethod.POST, "/api/auth/refresh").permitAll()
////						.pathMatchers(HttpMethod.GET, "/api/auth/verify/**").permitAll()
////
////						// 📊 Actuator endpoints (restricted)
////						.pathMatchers("/actuator/health", "/actuator/info").permitAll().pathMatchers("/actuator/**")
////						.hasRole("ADMIN")
////
////						// 📚 API Documentation (development only)
////						.pathMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
////
////						// 🔐 Protected API endpoints
////						.pathMatchers("/api/collections/**").hasAnyRole("USER", "ADMIN")
////						.pathMatchers("/api/requests/**").hasAnyRole("USER", "ADMIN").pathMatchers("/api/ai/**")
////						.hasAnyRole("USER", "ADMIN").pathMatchers("/api/export/**").hasAnyRole("USER", "ADMIN")
////
////						// 👑 Admin-only endpoints
////						.pathMatchers("/api/admin/**").hasRole("ADMIN").pathMatchers(HttpMethod.DELETE, "/api/**")
////						.hasRole("ADMIN")
//
//						// 🔒 All other requests require authentication
//						.anyExchange().permitAll())
//
//				.build();
//	}
//
//	/**
//	 * 🔐 Password Encoder Bean
//	 */
//	@Bean
//	public PasswordEncoder passwordEncoder() {
//		log.info("🔧 Configuring BCrypt Password Encoder");
//		return new BCryptPasswordEncoder(12); // Strong hashing rounds
//	}
//
//	/**
//	 * 🌐 CORS Configuration Source
//	 */
//	@Bean
//	public CorsConfigurationSource corsConfigurationSource() {
//		log.info("🔧 Configuring CORS");
//
//		CorsConfiguration configuration = new CorsConfiguration();
//
//		// 🌍 Allowed origins
//		configuration.setAllowedOriginPatterns(List.of("http://localhost:*", "https://localhost:*",
//				"https://*.alesqui.es", "https://*.vercel.app"));
//
//		// 📝 Allowed methods
//		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
//
//		// 📋 Allowed headers
//		configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin",
//				"Access-Control-Request-Method", "Access-Control-Request-Headers", "X-Request-ID", "X-Correlation-ID"));
//
//		// 🍪 Allow credentials
//		configuration.setAllowCredentials(false); 
//
//		// ⏱️ Max age for preflight requests
//		configuration.setMaxAge(3600L);
//
//		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
//		source.registerCorsConfiguration("/**", configuration); 
//
//		return source;
//	}
//}