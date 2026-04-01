package es.alesqui.intelligence.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import reactor.test.StepVerifier;

import java.security.Principal;
import java.util.List;

/**
 * Unit tests for SecurityUtils.
 * Verifies username extraction from the reactive security context.
 */
class SecurityUtilsTest {

    @Nested
    @DisplayName("getCurrentUsername() — with UserDetails principal")
    class WithUserDetailsPrincipal {

        @Test
        @DisplayName("returns the username from UserDetails")
        void returnsUsernameFromUserDetails() {
            UserDetails user = User.withUsername("alice")
                    .password("pw")
                    .authorities(new SimpleGrantedAuthority("ROLE_USER"))
                    .build();
            Authentication auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());

            StepVerifier.create(
                            SecurityUtils.getCurrentUsername()
                                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth))
                    )
                    .expectNext("alice")
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("getCurrentUsername() — with Principal principal")
    class WithPrincipalOnly {

        @Test
        @DisplayName("returns the name from a Principal object")
        void returnsNameFromPrincipal() {
            Principal principal = () -> "bob";
            Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());

            StepVerifier.create(
                            SecurityUtils.getCurrentUsername()
                                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth))
                    )
                    .expectNext("bob")
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("getCurrentUsername() — with generic Object principal")
    class WithGenericPrincipal {

        @Test
        @DisplayName("returns toString() of the principal object")
        void returnsToStringOfObject() {
            Object principal = new Object() {
                @Override
                public String toString() {
                    return "custom-principal";
                }
            };
            Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());

            StepVerifier.create(
                            SecurityUtils.getCurrentUsername()
                                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth))
                    )
                    .expectNext("custom-principal")
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("getCurrentUsername() — unauthenticated / empty context")
    class Unauthenticated {

        @Test
        @DisplayName("returns 'anonymous_fallback' when context is empty")
        void returnsAnonymousFallbackWhenContextEmpty() {
            StepVerifier.create(SecurityUtils.getCurrentUsername())
                    .expectNext("anonymous_fallback")
                    .verifyComplete();
        }

        @Test
        @DisplayName("returns 'anonymous_fallback' when authentication has null principal")
        void returnsAnonymousFallbackWhenNullPrincipal() {
            Authentication auth = new UsernamePasswordAuthenticationToken(null, null);
            SecurityContextImpl context = new SecurityContextImpl(auth);

            StepVerifier.create(
                            SecurityUtils.getCurrentUsername()
                                    .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(
                                            reactor.core.publisher.Mono.just(context)))
                    )
                    .expectNext("anonymous_fallback")
                    .verifyComplete();
        }
    }
}
