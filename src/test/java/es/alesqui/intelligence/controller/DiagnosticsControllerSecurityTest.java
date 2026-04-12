package es.alesqui.intelligence.controller;

import es.alesqui.intelligence.security.JwtService;
import es.alesqui.intelligence.service.conversation.ConversationService;

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

/**
 * Role-based access control tests for DiagnosticsController.
 * Verifies that BUSINESS users receive 403 on /api/diagnostics/** endpoints.
 */
@WebFluxTest(DiagnosticsController.class)
@Import(DiagnosticsControllerSecurityTest.RoleEnforcingSecurityConfig.class)
class DiagnosticsControllerSecurityTest {

    /**
     * Minimal security configuration that enforces the same RBAC rules as the
     * production SecurityConfig for diagnostics endpoints.
     */
    @TestConfiguration
    @EnableWebFluxSecurity
    static class RoleEnforcingSecurityConfig {

        @Bean
        @Primary
        public SecurityWebFilterChain testSecurityFilterChain(ServerHttpSecurity http) {
            return http
                    .csrf(ServerHttpSecurity.CsrfSpec::disable)
                    .authorizeExchange(exchanges -> exchanges
                            // Same rules as SecurityConfig for diagnostics endpoints
                            .pathMatchers("/api/diagnostics/**").hasAnyRole("IT", "SUPERADMIN", "TRIAL")
                            .anyExchange().authenticated()
                    )
                    .build();
        }
    }

    @Autowired
    WebTestClient webClient;

    // JwtAuthenticationWebFilter is a @Component WebFilter loaded by @WebFluxTest;
    // its dependencies must be mocked so it can be instantiated.
    @MockitoBean JwtService jwtService;
    @MockitoBean ReactiveUserDetailsService reactiveUserDetailsService;

    @MockitoBean ConversationService conversationService;

    // ── BUSINESS role is forbidden (403) ────────────────────────────────

    @Test
    void getTickets_businessRole_returns403() {
        webClient.mutateWith(SecurityMockServerConfigurers.mockUser("biz@example.com").roles("BUSINESS"))
                .get().uri("/api/diagnostics/tickets")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void getTicketStats_businessRole_returns403() {
        webClient.mutateWith(SecurityMockServerConfigurers.mockUser("biz@example.com").roles("BUSINESS"))
                .get().uri("/api/diagnostics/tickets/stats")
                .exchange()
                .expectStatus().isForbidden();
    }
}
