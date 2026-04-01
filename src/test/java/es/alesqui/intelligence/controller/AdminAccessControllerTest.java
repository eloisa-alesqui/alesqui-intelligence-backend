package es.alesqui.intelligence.controller;

import es.alesqui.intelligence.config.TestSecurityConfig;
import es.alesqui.intelligence.dto.admin.*;
import es.alesqui.intelligence.model.access.ApiGroupLink;
import es.alesqui.intelligence.model.access.Group;
import es.alesqui.intelligence.model.access.GroupMembership;
import es.alesqui.intelligence.model.core.User;
import es.alesqui.intelligence.model.core.enums.Role;
import es.alesqui.intelligence.security.JwtService;
import es.alesqui.intelligence.service.access.ApiGroupLinkService;
import es.alesqui.intelligence.service.access.GroupManagementService;
import es.alesqui.intelligence.service.access.GroupMembershipService;
import es.alesqui.intelligence.service.access.UserAdminService;
import es.alesqui.intelligence.service.audit.AuditService;
import es.alesqui.intelligence.service.trial.TrialRegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

/**
 * Functional controller-layer tests for AdminAccessController.
 * Uses TestSecurityConfig to bypass the security filter so tests focus on
 * business logic, serialization, and HTTP response codes.
 * Role-based access control (403 for non-SUPERADMIN) is verified in
 * AdminAccessControllerSecurityTest.
 */
@WebFluxTest(AdminAccessController.class)
@Import(TestSecurityConfig.class)
class AdminAccessControllerTest {

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

    private Group mockGroup;
    private User mockUser;
    private GroupMembership mockMembership;
    private ApiGroupLink mockApiLink;

    @BeforeEach
    void setUp() {
        mockGroup = new Group();
        mockGroup.setId("group-1");
        mockGroup.setCode("sales");
        mockGroup.setName("Sales");
        mockGroup.setDescription("Sales department");
        mockGroup.setCreatedAt(Instant.now());

        mockUser = User.builder()
                .id("user-1")
                .username("user@example.com")
                .roles(Set.of(Role.ROLE_IT))
                .isActive(true)
                .createdAt(Instant.now())
                .build();

        mockMembership = new GroupMembership();
        mockMembership.setId("membership-1");
        mockMembership.setGroupId("group-1");
        mockMembership.setUserId("user-1");

        mockApiLink = new ApiGroupLink();
        mockApiLink.setId("link-1");
        mockApiLink.setGroupId("group-1");
        mockApiLink.setApiId("api-1");

        // Default stubs for audit service
        given(auditService.logUserOperationFailure(any(), any(), any(), any(), any()))
                .willReturn(Mono.empty());
        given(groupManagementService.logGroupOperationFailure(any(), any(), any(), any(), any()))
                .willReturn(Mono.empty());
    }

    // ══════════════════════════════════════════════════════════════════════
    // GROUP CRUD
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void createGroup_success_returns200WithGroup() {
        given(groupManagementService.createGroup(any(), any())).willReturn(Mono.just(mockGroup));

        webClient.post().uri("/api/admin/groups")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("code", "sales", "name", "Sales", "description", "Sales dept"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Group.class)
                .value(group -> {
                    assertThat(group.getId()).isEqualTo("group-1");
                    assertThat(group.getName()).isEqualTo("Sales");
                });
    }

    @Test
    void createGroup_blankName_returns400() {
        webClient.post().uri("/api/admin/groups")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("code", "", "name", ""))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void listGroups_returns200WithGroupList() {
        GroupSummaryResponse summary = GroupSummaryResponse.builder()
                .id("group-1").code("sales").name("Sales").userCount(5).apiCount(3).build();
        given(groupManagementService.listGroupsWithCounts()).willReturn(Flux.just(summary));

        webClient.get().uri("/api/admin/groups")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(GroupSummaryResponse.class)
                .value(list -> {
                    assertThat(list).hasSize(1);
                    assertThat(list.get(0).getName()).isEqualTo("Sales");
                    assertThat(list.get(0).getUserCount()).isEqualTo(5);
                });
    }

    @Test
    void listGroups_emptyList_returns200WithEmptyArray() {
        given(groupManagementService.listGroupsWithCounts()).willReturn(Flux.empty());

        webClient.get().uri("/api/admin/groups")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(GroupSummaryResponse.class)
                .value(list -> assertThat(list).isEmpty());
    }

    @Test
    void getGroupDetail_found_returns200() {
        GroupDetailResponse detail = new GroupDetailResponse();
        given(groupManagementService.getGroupDetail("group-1")).willReturn(Mono.just(detail));

        webClient.get().uri("/api/admin/groups/group-1")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void getGroupDetail_notFound_returns404() {
        given(groupManagementService.getGroupDetail("missing")).willReturn(Mono.empty());

        webClient.get().uri("/api/admin/groups/missing")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void updateGroup_success_returns200() {
        given(groupManagementService.updateGroup(eq("group-1"), any(), any()))
                .willReturn(Mono.just(mockGroup));

        webClient.patch().uri("/api/admin/groups/group-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", "Sales Updated", "code", "sales"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Group.class)
                .value(group -> assertThat(group.getId()).isEqualTo("group-1"));
    }

    @Test
    void deleteGroup_success_returns204() {
        given(groupManagementService.deleteGroup(eq("group-1"), any())).willReturn(Mono.empty());

        webClient.delete().uri("/api/admin/groups/group-1")
                .exchange()
                .expectStatus().isNoContent();
    }

    // ══════════════════════════════════════════════════════════════════════
    // GROUP ↔ API ASSIGNMENTS
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void assignApis_success_returns200WithLinks() {
        given(apiGroupLinkService.assignApis(eq("group-1"), any(), any()))
                .willReturn(Flux.just(mockApiLink));

        webClient.post().uri("/api/admin/groups/group-1/apis")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("apiIds", List.of("api-1")))
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ApiGroupLink.class)
                .value(list -> assertThat(list).hasSize(1));
    }

    @Test
    void removeApiFromGroup_success_returns204() {
        given(apiGroupLinkService.removeApiFromGroup("group-1", "api-1", null))
                .willReturn(Mono.empty());
        given(apiGroupLinkService.removeApiFromGroup(eq("group-1"), eq("api-1"), any()))
                .willReturn(Mono.empty());

        webClient.delete().uri("/api/admin/groups/group-1/apis/api-1")
                .exchange()
                .expectStatus().isNoContent();
    }

    // ══════════════════════════════════════════════════════════════════════
    // GROUP ↔ USER ASSIGNMENTS
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void assignUsers_success_returns200WithMemberships() {
        given(groupMembershipService.assignUsers(eq("group-1"), any(), any()))
                .willReturn(Flux.just(mockMembership));

        webClient.post().uri("/api/admin/groups/group-1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("userIds", List.of("user-1")))
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(GroupMembership.class)
                .value(list -> assertThat(list).hasSize(1));
    }

    @Test
    void removeUserFromGroup_success_returns204() {
        given(groupMembershipService.removeUserFromGroup(eq("group-1"), eq("user-1"), any()))
                .willReturn(Mono.empty());

        webClient.delete().uri("/api/admin/groups/group-1/users/user-1")
                .exchange()
                .expectStatus().isNoContent();
    }

    // ══════════════════════════════════════════════════════════════════════
    // USER CRUD
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void createUser_success_returns200WithUser() {
        given(userAdminService.createUser(any(CreateUserRequest.class), any(ServerHttpRequest.class))).willReturn(Mono.just(mockUser));

        // User implements UserDetails with GrantedAuthority; use jsonPath to avoid Jackson
        // abstract-type deserialization issue on the client side.
        webClient.post().uri("/api/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "username", "user@example.com",
                        "roles", List.of("ROLE_IT")
                ))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.username").isEqualTo("user@example.com")
                .jsonPath("$.id").isEqualTo("user-1");
    }

    @Test
    void createUser_invalidEmail_returns400() {
        webClient.post().uri("/api/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("username", "not-an-email", "roles", List.of("ROLE_IT")))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void createUser_missingRoles_returns400() {
        webClient.post().uri("/api/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("username", "user@example.com"))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void listUsers_returns200WithUserList() {
        UserSummaryResponse summary = UserSummaryResponse.builder()
                .id("user-1").username("user@example.com")
                .roles(List.of("ROLE_IT")).active(true).groupCount(2).build();
        given(userAdminService.listAllUsers()).willReturn(Flux.just(summary));

        webClient.get().uri("/api/admin/users")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(UserSummaryResponse.class)
                .value(list -> {
                    assertThat(list).hasSize(1);
                    assertThat(list.get(0).getUsername()).isEqualTo("user@example.com");
                });
    }

    @Test
    void getUserDetail_found_returns200() {
        UserDetailResponse detail = new UserDetailResponse();
        given(userAdminService.getUserDetail("user-1")).willReturn(Mono.just(detail));

        webClient.get().uri("/api/admin/users/user-1")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void getUserDetail_notFound_returns404() {
        given(userAdminService.getUserDetail("missing")).willReturn(Mono.empty());

        webClient.get().uri("/api/admin/users/missing")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void updateUser_success_returns200() {
        UpdateUserResponse response = UpdateUserResponse.builder()
                .id("user-1").username("updated@example.com").build();
        given(userAdminService.updateUser(eq("user-1"), any(), any()))
                .willReturn(Mono.just(response));

        webClient.patch().uri("/api/admin/users/user-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("username", "updated@example.com"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(UpdateUserResponse.class)
                .value(u -> assertThat(u.getUsername()).isEqualTo("updated@example.com"));
    }

    @Test
    void deleteUser_success_returns204() {
        given(userAdminService.deleteUser(eq("user-1"), anyString(), any(ServerHttpRequest.class)))
                .willReturn(Mono.empty());

        // deleteUser requires Authentication injection — use mockUser to populate the context
        webClient.mutateWith(SecurityMockServerConfigurers.mockUser("admin@example.com").roles("SUPERADMIN"))
                .delete().uri("/api/admin/users/user-1")
                .exchange()
                .expectStatus().isNoContent();
    }

    // ══════════════════════════════════════════════════════════════════════
    // USER ROLES
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @Disabled("Production code defect: @PatchMapping(\"/users/{username}/roles\") and " +
              "@PatchMapping(\"/users/{userId}/roles\") have identical URL templates, " +
              "causing Spring to throw IllegalStateException: Ambiguous handler methods. " +
              "Fix: rename one endpoint path before enabling this test.")
    void updateUserRoles_success_returns200() {
        given(userAdminService.updateUserRoles(eq("user@example.com"), any(), any()))
                .willReturn(Mono.just(mockUser));

        webClient.patch().uri("/api/admin/users/user@example.com/roles")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("roles", List.of("ROLE_BUSINESS")))
                .exchange()
                .expectStatus().isOk();
    }

    // ══════════════════════════════════════════════════════════════════════
    // USER ↔ GROUP ASSIGNMENTS
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void assignGroupsToUser_success_returns204() {
        given(groupMembershipService.assignGroupsToUser(eq("user-1"), any(), any()))
                .willReturn(Flux.just(mockMembership));

        webClient.post().uri("/api/admin/users/user-1/groups")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("groupIds", List.of("group-1")))
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void removeGroupFromUser_success_returns204() {
        given(groupMembershipService.removeGroupFromUser(eq("user-1"), eq("group-1"), any()))
                .willReturn(Mono.empty());

        webClient.delete().uri("/api/admin/users/user-1/groups/group-1")
                .exchange()
                .expectStatus().isNoContent();
    }

    // ══════════════════════════════════════════════════════════════════════
    // ORPHAN APIS + API-GROUP ASSIGNMENTS
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void getOrphanApis_returns200WithList() {
        ApiSummaryResponse api = new ApiSummaryResponse();
        given(apiGroupLinkService.listOrphanApis()).willReturn(Flux.just(api));

        webClient.get().uri("/api/admin/apis/orphans")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ApiSummaryResponse.class)
                .value(list -> assertThat(list).hasSize(1));
    }

    @Test
    void assignGroupsToApi_success_returns200() {
        given(apiGroupLinkService.assignGroupsToApi(eq("api-1"), any(), any()))
                .willReturn(Flux.just(mockApiLink));

        webClient.post().uri("/api/admin/apis/api-1/groups")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("groupIds", List.of("group-1")))
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ApiGroupLink.class)
                .value(list -> assertThat(list).hasSize(1));
    }

    @Test
    void getGroupsForApi_returns200() {
        GroupSummaryResponse summary = GroupSummaryResponse.builder().id("group-1").name("Sales").build();
        given(apiGroupLinkService.getGroupsForApi("api-1")).willReturn(Flux.just(summary));

        webClient.get().uri("/api/admin/apis/api-1/groups")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(GroupSummaryResponse.class)
                .value(list -> assertThat(list).hasSize(1));
    }

    // ══════════════════════════════════════════════════════════════════════
    // TRIAL USERS
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void listTrialUsers_returns200WithTrialUserList() {
        User trialUser = User.builder()
                .id("trial-1")
                .username("trial@example.com")
                .roles(Set.of(Role.ROLE_TRIAL))
                .isActive(true)
                .trialStartDate(Instant.now().minusSeconds(3600))
                .trialEndDate(Instant.now().plusSeconds(86400 * 7))
                .createdAt(Instant.now())
                .build();

        given(trialRegistrationService.listAllTrialUsers()).willReturn(Flux.just(trialUser));
        given(trialRegistrationService.getDaysRemaining(any(User.class))).willReturn(7L);
        given(trialRegistrationService.isTrialExpired(any(User.class))).willReturn(false);

        webClient.get().uri("/api/admin/trial-users")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(TrialUserResponse.class)
                .value(list -> {
                    assertThat(list).hasSize(1);
                    assertThat(list.get(0).getEmail()).isEqualTo("trial@example.com");
                    assertThat(list.get(0).getDaysRemaining()).isEqualTo(7L);
                    assertThat(list.get(0).isExpired()).isFalse();
                });
    }
}
