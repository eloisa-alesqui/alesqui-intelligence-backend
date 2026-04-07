package es.alesqui.intelligence.controller;

import es.alesqui.intelligence.config.TestSecurityConfig;
import es.alesqui.intelligence.dto.dashboard.ActivityInfo;
import es.alesqui.intelligence.dto.dashboard.ApiSummaryItem;
import es.alesqui.intelligence.dto.dashboard.DashboardSummaryResponse;
import es.alesqui.intelligence.dto.dashboard.UserInfo;
import es.alesqui.intelligence.security.JwtService;
import es.alesqui.intelligence.service.dashboard.DashboardService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

import static org.mockito.BDDMockito.given;

/**
 * Controller-layer tests for DashboardController.
 * Uses TestSecurityConfig to bypass the JWT filter.
 * SecurityUtils.getCurrentUsername() is exercised via SecurityMockServerConfigurers.mockUser().
 */
@WebFluxTest(DashboardController.class)
@Import(TestSecurityConfig.class)
class DashboardControllerTest {

    @Autowired
    WebTestClient webClient;

    @MockitoBean
    DashboardService dashboardService;

    // Required by JwtAuthenticationWebFilter (@Component WebFilter auto-scanned)
    @MockitoBean
    JwtService jwtService;
    @MockitoBean
    ReactiveUserDetailsService reactiveUserDetailsService;

    // ──────────────────────────── getSummary — authenticated ────────────────────────────

    @Test
    void getSummary_authenticated_returns200() {
        given(dashboardService.getSummary()).willReturn(Mono.just(buildFullResponse()));

        webClient.mutateWith(SecurityMockServerConfigurers.mockUser("testuser"))
                .get()
                .uri("/api/dashboard/summary")
                .exchange()
                .expectStatus().isOk()
                .expectBody(DashboardSummaryResponse.class)
                .value(body -> {
                    assert body != null;
                    assert body.getUser() != null;
                    assert body.getActivity() != null;
                    assert body.getApis() != null;
                });
    }

    // ──────────────────────────── getSummary — service error ────────────────────────────

    @Test
    void getSummary_serviceError_returns500() {
        given(dashboardService.getSummary())
                .willReturn(Mono.error(new RuntimeException("boom")));

        webClient.mutateWith(SecurityMockServerConfigurers.mockUser("testuser"))
                .get()
                .uri("/api/dashboard/summary")
                .exchange()
                .expectStatus().is5xxServerError();
    }

    // ──────────────────────────── getSummary — JSON structure ────────────────────────────

    @Test
    void getSummary_responseContainsExpectedSections() {
        given(dashboardService.getSummary()).willReturn(Mono.just(buildFullResponse()));

        webClient.mutateWith(SecurityMockServerConfigurers.mockUser("testuser"))
                .get()
                .uri("/api/dashboard/summary")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.user").exists()
                .jsonPath("$.activity").exists()
                .jsonPath("$.apis").exists()
                .jsonPath("$.trial").doesNotExist()
                .jsonPath("$.admin").doesNotExist()
                .jsonPath("$.support").doesNotExist();
    }

    // ──────────────────────────── helpers ────────────────────────────

    private DashboardSummaryResponse buildFullResponse() {
        UserInfo user = UserInfo.builder()
                .username("testuser")
                .roles(List.of("ROLE_BUSINESS"))
                .memberSince(Instant.now())
                .build();

        ActivityInfo activity = ActivityInfo.builder()
                .totalConversations(10L)
                .conversationsLast7Days(3L)
                .totalMessages(42L)
                .chartsGenerated(5L)
                .lastConversation(null)
                .build();

        List<ApiSummaryItem> apis = List.of(
                ApiSummaryItem.builder().id("api-1").name("API One").active(true).build()
        );

        return DashboardSummaryResponse.builder()
                .user(user)
                .activity(activity)
                .apis(apis)
                .build();
    }
}
