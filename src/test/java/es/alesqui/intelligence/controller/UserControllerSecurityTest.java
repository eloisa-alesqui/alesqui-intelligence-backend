package es.alesqui.intelligence.controller;

import es.alesqui.intelligence.security.JwtService;
import es.alesqui.intelligence.service.access.GroupManagementService;
import es.alesqui.intelligence.service.identity.UserService;
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
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Role-based access control tests for UserController.
 * Verifies that unauthenticated requests to /api/me/** receive 401.
 */
@WebFluxTest(UserController.class)
@Import(UserControllerSecurityTest.RoleEnforcingSecurityConfig.class)
class UserControllerSecurityTest {

    @TestConfiguration
    @EnableWebFluxSecurity
    static class RoleEnforcingSecurityConfig {

        @Bean
        @Primary
        public SecurityWebFilterChain testSecurityFilterChain(ServerHttpSecurity http) {
            return http
                    .csrf(ServerHttpSecurity.CsrfSpec::disable)
                    .authorizeExchange(exchanges -> exchanges
                            .pathMatchers("/api/me/**").authenticated()
                            .anyExchange().authenticated()
                    )
                    .build();
        }
    }

    @Autowired
    WebTestClient webClient;

    @MockitoBean JwtService jwtService;
    @MockitoBean ReactiveUserDetailsService reactiveUserDetailsService;
    @MockitoBean UserService userService;
    @MockitoBean GroupManagementService groupManagementService;

    @Test
    void getCurrentUserGroups_unauthenticated_returns401() {
        webClient.get()
                .uri("/api/me/groups")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
