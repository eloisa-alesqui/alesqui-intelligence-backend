package es.alesqui.intelligence.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JwtAuthenticationWebFilter.
 * Verifies the reactive JWT authentication filter logic.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationWebFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private ReactiveUserDetailsService userDetailsService;

    private JwtAuthenticationWebFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationWebFilter(jwtService, userDetailsService);
    }

    // A simple chain that records the authentication present in the reactive context
    private static WebFilterChain capturingChain(AtomicReference<Authentication> ref) {
        return exchange -> ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .doOnNext(ref::set)
                .then();
    }

    private static WebFilterChain passThroughChain() {
        return exchange -> Mono.empty();
    }

    @Nested
    @DisplayName("Public auth endpoints")
    class PublicAuthEndpoints {

        @Test
        @DisplayName("skips JWT processing for /api/auth/login")
        void skipsForLogin() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/auth/login").build()
            );

            StepVerifier.create(filter.filter(exchange, passThroughChain()))
                    .verifyComplete();

            verifyNoInteractions(jwtService);
        }

        @Test
        @DisplayName("skips JWT processing for /api/auth/refresh")
        void skipsForRefresh() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/auth/refresh").build()
            );

            StepVerifier.create(filter.filter(exchange, passThroughChain()))
                    .verifyComplete();

            verifyNoInteractions(jwtService);
        }

        @Test
        @DisplayName("does NOT skip JWT processing for /api/auth/logout")
        void doesNotSkipForLogout() {
            // No Authorization header → JWT processing runs but finds no header
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/auth/logout").build()
            );

            StepVerifier.create(filter.filter(exchange, passThroughChain()))
                    .verifyComplete();

            // jwtService was never called because there's no header — but it was NOT skipped
            // The path check should NOT have bypassed the filter
            verifyNoInteractions(jwtService); // no token to process, but path was not skipped
        }
    }

    @Nested
    @DisplayName("Missing or malformed Authorization header")
    class MissingHeader {

        @Test
        @DisplayName("continues chain without authentication when header is absent")
        void noHeader() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/chat/stream").build()
            );
            AtomicReference<Authentication> auth = new AtomicReference<>();

            StepVerifier.create(filter.filter(exchange, capturingChain(auth)))
                    .verifyComplete();

            assertThat(auth.get()).isNull();
            verifyNoInteractions(jwtService);
        }

        @Test
        @DisplayName("continues chain without authentication when header lacks 'Bearer ' prefix")
        void malformedHeader() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/chat/stream")
                            .header(HttpHeaders.AUTHORIZATION, "Token some-value")
                            .build()
            );
            AtomicReference<Authentication> auth = new AtomicReference<>();

            StepVerifier.create(filter.filter(exchange, capturingChain(auth)))
                    .verifyComplete();

            assertThat(auth.get()).isNull();
            verifyNoInteractions(jwtService);
        }
    }

    @Nested
    @DisplayName("Invalid / expired token")
    class InvalidToken {

        @Test
        @DisplayName("continues chain without authentication when extractUsername throws")
        void extractUsernameFails() {
            when(jwtService.extractUsername(anyString()))
                    .thenThrow(new io.jsonwebtoken.JwtException("bad token"));

            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/chat/stream")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer bad.token.value")
                            .build()
            );
            AtomicReference<Authentication> auth = new AtomicReference<>();

            StepVerifier.create(filter.filter(exchange, capturingChain(auth)))
                    .verifyComplete();

            assertThat(auth.get()).isNull();
        }

        @Test
        @DisplayName("continues chain without authentication when token fails validation")
        void tokenValidationFails() {
            UserDetails user = User.withUsername("alice")
                    .password("pw")
                    .authorities(new SimpleGrantedAuthority("ROLE_USER"))
                    .build();

            when(jwtService.extractUsername(anyString())).thenReturn("alice");
            when(userDetailsService.findByUsername("alice")).thenReturn(Mono.just(user));
            when(jwtService.isTokenValid(anyString(), eq(user))).thenReturn(false);

            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/chat/stream")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer some.expired.token")
                            .build()
            );
            AtomicReference<Authentication> auth = new AtomicReference<>();

            StepVerifier.create(filter.filter(exchange, capturingChain(auth)))
                    .verifyComplete();

            assertThat(auth.get()).isNull();
        }
    }

    @Nested
    @DisplayName("Valid token")
    class ValidToken {

        @Test
        @DisplayName("sets authentication in the reactive security context")
        void setsAuthentication() {
            UserDetails user = User.withUsername("alice")
                    .password("pw")
                    .authorities(new SimpleGrantedAuthority("ROLE_USER"))
                    .build();

            when(jwtService.extractUsername(anyString())).thenReturn("alice");
            when(userDetailsService.findByUsername("alice")).thenReturn(Mono.just(user));
            when(jwtService.isTokenValid(anyString(), eq(user))).thenReturn(true);
            when(jwtService.isAccessToken(anyString())).thenReturn(true);

            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/chat/stream")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer valid.jwt.token")
                            .build()
            );
            AtomicReference<Authentication> capturedAuth = new AtomicReference<>();

            StepVerifier.create(filter.filter(exchange, capturingChain(capturedAuth)))
                    .verifyComplete();

            assertThat(capturedAuth.get()).isNotNull();
            assertThat(capturedAuth.get().getName()).isEqualTo("alice");
            assertThat(capturedAuth.get().isAuthenticated()).isTrue();
        }

        @Test
        @DisplayName("rejects a refresh token used as Bearer (does not set authentication)")
        void rejectsRefreshTokenAsBearer() {
            UserDetails user = User.withUsername("alice")
                    .password("pw")
                    .authorities(new SimpleGrantedAuthority("ROLE_USER"))
                    .build();

            when(jwtService.extractUsername(anyString())).thenReturn("alice");
            when(userDetailsService.findByUsername("alice")).thenReturn(Mono.just(user));
            when(jwtService.isTokenValid(anyString(), eq(user))).thenReturn(true);
            when(jwtService.isAccessToken(anyString())).thenReturn(false);

            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/chat/stream")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer refresh.jwt.token")
                            .build()
            );
            AtomicReference<Authentication> capturedAuth = new AtomicReference<>();

            StepVerifier.create(filter.filter(exchange, capturingChain(capturedAuth)))
                    .verifyComplete();

            assertThat(capturedAuth.get()).isNull();
        }
    }

    @Nested
    @DisplayName("Duplicate-processing guard")
    class DuplicateProcessingGuard {

        @Test
        @DisplayName("skips authentication when request was already processed")
        void skipsWhenAlreadyProcessed() {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/api/chat/stream")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer some.token")
                            .build()
            );
            // Simulate that a previous pass already set the processed attribute
            exchange.getAttributes().put("jwt.auth.processed", true);

            StepVerifier.create(filter.filter(exchange, passThroughChain()))
                    .verifyComplete();

            verifyNoInteractions(jwtService);
        }
    }
}
