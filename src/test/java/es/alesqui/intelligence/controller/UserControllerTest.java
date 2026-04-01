package es.alesqui.intelligence.controller;

import es.alesqui.intelligence.config.TestSecurityConfig;
import es.alesqui.intelligence.dto.admin.GroupSummaryResponse;
import es.alesqui.intelligence.model.core.User;
import es.alesqui.intelligence.model.core.enums.Role;
import es.alesqui.intelligence.security.JwtService;
import es.alesqui.intelligence.service.access.GroupManagementService;
import es.alesqui.intelligence.service.identity.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Set;

import static org.mockito.BDDMockito.given;

/**
 * Controller-layer tests for UserController.
 * Uses TestSecurityConfig to bypass the JWT filter.
 * userService.getCurrentUser() is mocked directly so no real SecurityUtils call is made.
 */
@WebFluxTest(UserController.class)
@Import(TestSecurityConfig.class)
class UserControllerTest {

    @Autowired
    WebTestClient webClient;

    @MockitoBean UserService userService;
    @MockitoBean GroupManagementService groupManagementService;
    @MockitoBean JwtService jwtService;
    @MockitoBean ReactiveUserDetailsService reactiveUserDetailsService;

    @Test
    void getCurrentUserGroups_superadmin_returnsAllGroups() {
        User superAdminUser = User.builder()
                .id("admin-1").username("admin")
                .roles(Set.of(Role.ROLE_SUPERADMIN))
                .build();

        GroupSummaryResponse group1 = GroupSummaryResponse.builder()
                .id("g1").code("sales").name("Sales").userCount(5).apiCount(2).build();
        GroupSummaryResponse group2 = GroupSummaryResponse.builder()
                .id("g2").code("dev").name("Development").userCount(3).apiCount(4).build();

        given(userService.getCurrentUser()).willReturn(Mono.just(superAdminUser));
        given(groupManagementService.listGroupsWithCounts()).willReturn(Flux.just(group1, group2));

        webClient.mutateWith(SecurityMockServerConfigurers.mockUser("admin"))
                .get().uri("/api/me/groups")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(GroupSummaryResponse.class)
                .hasSize(2);
    }

    @Test
    void getCurrentUserGroups_regularUser_returnsOwnGroups() {
        User regularUser = User.builder()
                .id("user-1").username("user1")
                .roles(Set.of(Role.ROLE_BUSINESS))
                .build();

        GroupSummaryResponse group1 = GroupSummaryResponse.builder()
                .id("g1").code("sales").name("Sales").userCount(5).apiCount(2).build();

        given(userService.getCurrentUser()).willReturn(Mono.just(regularUser));
        given(groupManagementService.getGroupsForUser("user-1")).willReturn(Flux.just(group1));

        webClient.mutateWith(SecurityMockServerConfigurers.mockUser("user1"))
                .get().uri("/api/me/groups")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(GroupSummaryResponse.class)
                .hasSize(1);
    }
}
