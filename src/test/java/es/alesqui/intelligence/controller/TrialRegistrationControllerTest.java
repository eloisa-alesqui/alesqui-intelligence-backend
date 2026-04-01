package es.alesqui.intelligence.controller;

import es.alesqui.intelligence.config.DeploymentConfig;
import es.alesqui.intelligence.config.TestSecurityConfig;
import es.alesqui.intelligence.dto.trial.TrialRegistrationResponse;
import es.alesqui.intelligence.security.JwtService;
import es.alesqui.intelligence.service.trial.TrialRegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * Controller-layer tests for TrialRegistrationController.
 * Uses TestSecurityConfig to bypass the JWT filter (public endpoint).
 */
@WebFluxTest(TrialRegistrationController.class)
@Import(TestSecurityConfig.class)
class TrialRegistrationControllerTest {

    @Autowired
    WebTestClient webClient;

    @MockitoBean TrialRegistrationService trialRegistrationService;
    @MockitoBean DeploymentConfig deploymentConfig;
    @MockitoBean JwtService jwtService;
    @MockitoBean ReactiveUserDetailsService reactiveUserDetailsService;

    @Test
    void registerTrial_trialMode_returns200() {
        TrialRegistrationResponse response = TrialRegistrationResponse.builder()
                .message("Trial account created successfully.")
                .email("user@example.com")
                .trialEndDate(Instant.parse("2026-04-12T10:00:00Z"))
                .trialDurationDays(14)
                .build();

        given(deploymentConfig.isCorporate()).willReturn(false);
        given(trialRegistrationService.registerTrialUser(any(), any(), any()))
                .willReturn(Mono.just(response));

        webClient.post()
                .uri("/api/public/trial-registration")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"user@example.com\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.email").isEqualTo("user@example.com")
                .jsonPath("$.trialDurationDays").isEqualTo(14);
    }

    @Test
    void registerTrial_corporateMode_returns403() {
        given(deploymentConfig.isCorporate()).willReturn(true);

        webClient.post()
                .uri("/api/public/trial-registration")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"user@example.com\"}")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void registerTrial_invalidEmail_returns400() {
        given(deploymentConfig.isCorporate()).willReturn(false);

        webClient.post()
                .uri("/api/public/trial-registration")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"not-an-email\"}")
                .exchange()
                .expectStatus().isBadRequest();
    }
}
