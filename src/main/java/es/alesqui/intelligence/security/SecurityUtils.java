package es.alesqui.intelligence.security;

import java.security.Principal;

import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.userdetails.UserDetails;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Utility class for common Spring Security operations in a reactive context.
 */
@Slf4j
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
	            .map(SecurityContext::getAuthentication)
	            .flatMap(authentication -> {
	                // Check if authentication and principal are present
	                if (authentication == null || authentication.getPrincipal() == null) {
	                    return Mono.empty();
	                }
	                
	                Object principal = authentication.getPrincipal();
	                String username;
	                
	                // Extract username based on principal type
	                if (principal instanceof UserDetails) {
	                    username = ((UserDetails) principal).getUsername();
	                } else if (principal instanceof Principal) {
	                    username = ((Principal) principal).getName();
	                } else {
	                    username = principal.toString();
	                }
	                
	                return Mono.just(username);
	            })
	            .switchIfEmpty(Mono.just("anonymous_fallback")); // Fallback for unauthenticated users
	}
	
}