package es.alesqui.intelligence.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * A reactive WebFilter for JWT authentication.
 *
 * This filter intercepts all requests, extracts the JWT from the
 * 'Authorization' header, validates it, and sets the authentication context for
 * Spring Security in a non-blocking way.
 * 
 * The filter includes protection against duplicate processing of the same request,
 * which can occur in reactive environments where the same request may be processed
 * multiple times due to thread switching between reactor-http-nio and nioEventLoopGroup threads.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationWebFilter implements WebFilter {

    private final JwtService jwtService;
    private final ReactiveUserDetailsService userDetailsService;
    
    // Attribute key to mark that JWT authentication has been processed for this request
    private static final String JWT_AUTH_PROCESSED_KEY = "jwt.auth.processed";

    /**
     * Filters incoming requests to perform JWT authentication.
     * 
     * @param exchange the current server exchange
     * @param chain provides a way to delegate to the next filter
     * @return a Mono to indicate when request processing is complete
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        String requestId = exchange.getRequest().getId();
        
        log.debug("=== JWT Filter Start === Request ID: {}, Thread: {}, Path: {}", 
            requestId, Thread.currentThread().getName(), path);

        // Check if this request has already been processed to avoid duplicate authentication
        if (exchange.getAttribute(JWT_AUTH_PROCESSED_KEY) != null) {
            log.debug("JWT authentication already processed for request ID: {}, skipping", requestId);
            return chain.filter(exchange);
        }

        // Mark this request as processed to prevent duplicate processing
        exchange.getAttributes().put(JWT_AUTH_PROCESSED_KEY, true);

        // Skip JWT processing for public endpoints
        if (path.startsWith("/api/auth/")) {
            log.debug("Skipping JWT authentication for public auth endpoint: {}", path);
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.debug("No valid Authorization header found for path: {}", path);
            return chain.filter(exchange); // Continue without authentication
        }

        String token = authHeader.substring(7);
        String username;

        try {
            username = jwtService.extractUsername(token);
            log.debug("Extracted username from token: {}", username);
        } catch (Exception e) {
            log.debug("Failed to extract username from token for path: {}", path, e);
            return chain.filter(exchange);
        }

        if (username == null) {
            log.debug("Username is null for path: {}", path);
            return chain.filter(exchange); // Invalid token
        }

        // The whole process is a reactive chain
        return userDetailsService.findByUsername(username)
                .cast(org.springframework.security.core.userdetails.UserDetails.class)
                .filter(userDetails -> {
                    try {
                        boolean isValid = jwtService.isTokenValid(token, userDetails);
                        log.debug("Token validation result for user {}: {}", username, isValid);
                        return isValid;
                    } catch (Exception e) {
                        log.debug("Token validation failed for user {}: {}", username, e.getMessage());
                        return false;
                    }
                })
                .flatMap(userDetails -> {
                    log.debug("Creating authentication for user: {}", userDetails.getUsername());
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());

                    // Set the authentication in the reactive context and continue with the chain
                    return chain.filter(exchange)
                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("No user found or token invalid for path: {}", path);
                    return chain.filter(exchange);
                }));
    }
}
