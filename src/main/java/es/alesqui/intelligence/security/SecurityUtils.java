package es.alesqui.intelligence.security;

import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import reactor.core.publisher.Mono;

/**
 * Utility class for common Spring Security operations in a reactive context.
 */
public final class SecurityUtils {

    /**
     * Retrieves the username of the currently authenticated user from the
     * reactive security context.
     *
     * @return A {@link Mono} emitting the username, or a fallback value
     * if no user is authenticated or the context is empty.
     */
    public static Mono<String> getCurrentUsername() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication().getPrincipal())
                .cast(UserDetails.class)
                .map(UserDetails::getUsername)
                .defaultIfEmpty("anonymous_fallback"); // Centralized fallback value
    }
}