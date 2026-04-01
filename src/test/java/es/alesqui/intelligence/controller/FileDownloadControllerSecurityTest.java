package es.alesqui.intelligence.controller;

import es.alesqui.intelligence.security.JwtService;
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
 * Role-based access control tests for FileDownloadController.
 * Verifies that unauthenticated requests to /api/files/** receive 401.
 */
@WebFluxTest(FileDownloadController.class)
@Import(FileDownloadControllerSecurityTest.RoleEnforcingSecurityConfig.class)
class FileDownloadControllerSecurityTest {

    @TestConfiguration
    @EnableWebFluxSecurity
    static class RoleEnforcingSecurityConfig {

        @Bean
        @Primary
        public SecurityWebFilterChain testSecurityFilterChain(ServerHttpSecurity http) {
            return http
                    .csrf(ServerHttpSecurity.CsrfSpec::disable)
                    .authorizeExchange(exchanges -> exchanges
                            .anyExchange().authenticated()
                    )
                    .build();
        }
    }

    @Autowired
    WebTestClient webClient;

    @MockitoBean JwtService jwtService;
    @MockitoBean ReactiveUserDetailsService reactiveUserDetailsService;

    @Test
    void downloadFile_unauthenticated_returns401() {
        webClient.get()
                .uri("/api/files/download/report.txt")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
