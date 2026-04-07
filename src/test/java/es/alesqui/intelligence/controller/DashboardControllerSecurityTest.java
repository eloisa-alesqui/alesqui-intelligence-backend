package es.alesqui.intelligence.controller;

import es.alesqui.intelligence.dto.dashboard.DashboardSummaryResponse;
import es.alesqui.intelligence.security.JwtService;
import es.alesqui.intelligence.service.dashboard.DashboardService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.BDDMockito.given;

/**
 * Security enforcement tests for DashboardController.
 * Verifies that authenticated users receive 200 and unauthenticated requests receive 401.
 */
@WebFluxTest(DashboardController.class)
@Import(DashboardControllerSecurityTest.AuthEnforcingSecurityConfig.class)
class DashboardControllerSecurityTest {

    /**
     * Minimal security configuration that enforces authentication for all exchanges,
     * without requiring JWT or CORS beans.
     */
    @TestConfiguration
    @EnableWebFluxSecurity
    static class AuthEnforcingSecurityConfig {

        @Bean
        @Primary
        public SecurityWebFilterChain testSecurityFilterChain(ServerHttpSecurity http) {
            return http
                    .csrf(ServerHttpSecurity.CsrfSpec::disable)
                    .authorizeExchange(ex -> ex.anyExchange().authenticated())
                    .build();
        }
    }

    @Autowired
    WebTestClient webClient;

    // JwtAuthenticationWebFilter is a @Component WebFilter loaded by @WebFluxTest;
    // its dependencies must be mocked so it can be instantiated.
    @MockitoBean
    JwtService jwtService;

    @MockitoBean
    ReactiveUserDetailsService reactiveUserDetailsService;

    @MockitoBean
    DashboardService dashboardService;

    // ── Authenticated user is allowed ────────────────────────────────────────────

    @Test
    void getSummary_withValidToken_returns200() {
        given(dashboardService.getSummary())
                .willReturn(Mono.just(DashboardSummaryResponse.builder().build()));

        webClient.mutateWith(SecurityMockServerConfigurers.mockUser("testuser"))
                .get()
                .uri("/api/dashboard/summary")
                .exchange()
                .expectStatus().isOk();
    }

    // ── Unauthenticated returns 401 ────────────────────────────────────────────

    @Test
    void getSummary_withoutToken_returns401() {
        webClient.get()
                .uri("/api/dashboard/summary")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
