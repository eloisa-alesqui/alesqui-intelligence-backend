package es.alesqui.intelligence.security;

import lombok.RequiredArgsConstructor;
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
 * This filter intercepts all requests, extracts the JWT from the 'Authorization' header,
 * validates it, and sets the authentication context for Spring Security in a
 * non-blocking way.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationWebFilter implements WebFilter {

    private final JwtService jwtService;
    private final ReactiveUserDetailsService userDetailsService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return chain.filter(exchange); // Continue without authentication
        }

        String token = authHeader.substring(7);
        String username = jwtService.extractUsername(token);

        if (username == null) {
            return chain.filter(exchange); // Invalid token
        }

        // The whole process is a reactive chain
        return userDetailsService.findByUsername(username)
                .filter(userDetails -> jwtService.isTokenValid(token, userDetails))
                .flatMap(userDetails -> {
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    // Set the authentication in the reactive context
                    return chain.filter(exchange)
                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
                })
                .switchIfEmpty(chain.filter(exchange)); // If user not found or token invalid, continue without auth
    }
}