package es.alesqui.intelligence.controller;

import es.alesqui.intelligence.dto.admin.GroupSummaryResponse;
import es.alesqui.intelligence.security.JwtService;
import es.alesqui.intelligence.service.access.ApiGroupLinkService;
import es.alesqui.intelligence.service.access.GroupManagementService;
import es.alesqui.intelligence.service.access.GroupMembershipService;
import es.alesqui.intelligence.service.access.UserAdminService;
import es.alesqui.intelligence.service.audit.AuditService;
import es.alesqui.intelligence.service.trial.TrialRegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import static org.mockito.BDDMockito.given;

/**
 * Role-based access control tests for AdminAccessController.
 * Verifies that only SUPERADMIN users can reach /api/admin/** endpoints,
 * while BUSINESS users receive 403 and unauthenticated requests receive 401.
 */
@WebFluxTest(AdminAccessController.class)
@Import(AdminAccessControllerSecurityTest.RoleEnforcingSecurityConfig.class)
class AdminAccessControllerSecurityTest {

    /**
     * Minimal security configuration that enforces the same RBAC rules as the
     * production SecurityConfig for admin endpoints, without requiring JWT or CORS beans.
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
                            // Same rules as SecurityConfig for admin endpoints
                            .pathMatchers("/api/admin/apis/*/groups").hasAnyRole("IT", "SUPERADMIN", "TRIAL")
                            .pathMatchers("/api/admin/**").hasRole("SUPERADMIN")
                            .anyExchange().authenticated()
                    )
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
    ReactiveUserDetailsService userDetailsService;

    @MockitoBean
    GroupManagementService groupManagementService;

    @MockitoBean
    GroupMembershipService groupMembershipService;

    @MockitoBean
    ApiGroupLinkService apiGroupLinkService;

    @MockitoBean
    UserAdminService userAdminService;

    @MockitoBean
    TrialRegistrationService trialRegistrationService;

    @MockitoBean
    AuditService auditService;

    // ── SUPERADMIN is allowed ────────────────────────────────────────────

    @Test
    void listGroups_superadmin_returns200() {
        given(groupManagementService.listGroupsWithCounts()).willReturn(Flux.empty());

        webClient.mutateWith(SecurityMockServerConfigurers.mockUser("admin@example.com").roles("SUPERADMIN"))
                .get().uri("/api/admin/groups")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void listUsers_superadmin_returns200() {
        given(userAdminService.listAllUsers()).willReturn(Flux.empty());

        webClient.mutateWith(SecurityMockServerConfigurers.mockUser("admin@example.com").roles("SUPERADMIN"))
                .get().uri("/api/admin/users")
                .exchange()
                .expectStatus().isOk();
    }

    // ── BUSINESS role is forbidden (403) ────────────────────────────────

    @Test
    void listUsers_businessRole_returns403() {
        webClient.mutateWith(SecurityMockServerConfigurers.mockUser("biz@example.com").roles("BUSINESS"))
                .get().uri("/api/admin/users")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void createGroup_businessRole_returns403() {
        webClient.mutateWith(SecurityMockServerConfigurers.mockUser("biz@example.com").roles("BUSINESS"))
                .post().uri("/api/admin/groups")
                .bodyValue("{\"code\":\"test\",\"name\":\"Test\"}")
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── IT role is forbidden on admin-only endpoints (403) ────────────────

    @Test
    void createGroup_itRole_returns403() {
        webClient.mutateWith(SecurityMockServerConfigurers.mockUser("it@example.com").roles("IT"))
                .post().uri("/api/admin/groups")
                .bodyValue("{\"code\":\"test\",\"name\":\"Test\"}")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void listUsers_itRole_returns403() {
        webClient.mutateWith(SecurityMockServerConfigurers.mockUser("it@example.com").roles("IT"))
                .get().uri("/api/admin/users")
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── IT role IS allowed on api-group endpoint ──────────────────────────

    @Test
    void getGroupsForApi_itRole_returns200() {
        given(apiGroupLinkService.getGroupsForApi("api-1"))
                .willReturn(Flux.just(GroupSummaryResponse.builder().id("g1").name("G1").build()));

        webClient.mutateWith(SecurityMockServerConfigurers.mockUser("it@example.com").roles("IT"))
                .get().uri("/api/admin/apis/api-1/groups")
                .exchange()
                .expectStatus().isOk();
    }

    // ── Unauthenticated returns 401 ────────────────────────────────────────

    @Test
    void listUsers_unauthenticated_returns401() {
        webClient.get().uri("/api/admin/users")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void listGroups_unauthenticated_returns401() {
        webClient.get().uri("/api/admin/groups")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
