package es.alesqui.intelligence.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import es.alesqui.intelligence.dto.admin.ApiSummaryResponse;
import es.alesqui.intelligence.dto.admin.AssignApisRequest;
import es.alesqui.intelligence.dto.admin.AssignGroupsRequest;
import es.alesqui.intelligence.dto.admin.AssignGroupsToApiRequest;
import es.alesqui.intelligence.dto.admin.AssignUsersRequest;
import es.alesqui.intelligence.dto.admin.CreateUserRequest;
import es.alesqui.intelligence.dto.admin.GroupCreateRequest;
import es.alesqui.intelligence.dto.admin.GroupDetailResponse;
import es.alesqui.intelligence.dto.admin.GroupUpdateRequest;
import es.alesqui.intelligence.dto.admin.GroupSummaryResponse;
import es.alesqui.intelligence.dto.admin.TrialUserResponse;
import es.alesqui.intelligence.dto.admin.UpdateUserRolesRequest;
import es.alesqui.intelligence.dto.admin.UpdateUserRequest;
import es.alesqui.intelligence.dto.admin.UpdateUserResponse;
import es.alesqui.intelligence.dto.admin.UserDetailResponse;
import es.alesqui.intelligence.dto.admin.UserSummaryResponse;
import es.alesqui.intelligence.model.access.ApiGroupLink;
import es.alesqui.intelligence.model.access.Group;
import es.alesqui.intelligence.model.access.GroupMembership;
import es.alesqui.intelligence.model.audit.AuditAction;
import es.alesqui.intelligence.model.core.User;
import es.alesqui.intelligence.model.core.enums.Role;
import es.alesqui.intelligence.service.access.ApiGroupLinkService;
import es.alesqui.intelligence.service.access.GroupManagementService;
import es.alesqui.intelligence.service.access.GroupMembershipService;
import es.alesqui.intelligence.service.access.UserAdminService;
import es.alesqui.intelligence.service.audit.AuditService;
import es.alesqui.intelligence.service.trial.TrialRegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * REST controller for administrative operations on users, groups, and API access management.
 * Provides endpoints for CRUD operations on groups, user management, role assignments,
 * and membership relationships between users, groups, and APIs.
 * All endpoints require ADMIN or SUPERADMIN role authorization.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Validated
@Slf4j
@PreAuthorize("hasRole('SUPERADMIN')")
public class AdminAccessController {

    private final GroupManagementService groupManagementService;
    private final GroupMembershipService groupMembershipService;
    private final ApiGroupLinkService apiGroupLinkService;
    private final UserAdminService userAdminService;
    private final TrialRegistrationService trialRegistrationService;
    private final AuditService auditService;
    
    /**
     * Creates a new group with the specified name and description.
     *
     * @param req the group creation request containing name and description
     * @param httpRequest the HTTP request for audit logging
     * @return a Mono containing the newly created group
     */
    @PostMapping("/groups")
    public Mono<Group> createGroup(@Valid @RequestBody GroupCreateRequest req, ServerHttpRequest httpRequest) {
        return groupManagementService.createGroup(req, httpRequest)
                .onErrorResume(error -> 
                    groupManagementService.logGroupOperationFailure(
                            AuditAction.GROUP_CREATED,
                            null,
                            req.getName(),
                            error.getMessage(),
                            httpRequest
                    ).then(Mono.error(error))
                );
    }

    /**
     * Lists all groups with their user and API counts.
     *
     * @return a Flux of group summaries including counts
     */
    @GetMapping("/groups")
    public Flux<GroupSummaryResponse> listGroups() {
        return groupManagementService.listGroupsWithCounts();
    }

    /**
     * Retrieves detailed information about a specific group including its users and APIs.
     *
     * @param id the group ID
     * @return a ResponseEntity with group details or 404 if not found
     */
    @GetMapping("/groups/{id}")
    public Mono<ResponseEntity<GroupDetailResponse>> getGroupDetail(@PathVariable String id) {
        return groupManagementService.getGroupDetail(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Updates a group's name and description.
     *
     * @param groupId the group ID to update
     * @param req the update request containing new name and description
     * @param httpRequest the HTTP request for audit logging
     * @return a Mono containing the updated group
     */
    @PatchMapping("/groups/{groupId}")
    public Mono<Group> updateGroup(@PathVariable String groupId, @Valid @RequestBody GroupUpdateRequest req,
                                   ServerHttpRequest httpRequest) {
        return groupManagementService.updateGroup(groupId, req, httpRequest)
                .onErrorResume(error -> 
                    groupManagementService.logGroupOperationFailure(
                            AuditAction.GROUP_UPDATED,
                            groupId,
                            null,
                            error.getMessage(),
                            httpRequest
                    ).then(Mono.error(error))
                );
    }

    /**
     * Deletes a group and all its associated memberships and API links.
     *
     * @param groupId the group ID to delete
     * @param httpRequest the HTTP request for audit logging
     * @return a ResponseEntity with 204 No Content on success
     */
    @DeleteMapping("/groups/{groupId}")
    public Mono<ResponseEntity<Void>> deleteGroup(@PathVariable String groupId, ServerHttpRequest httpRequest) {
        return groupManagementService.deleteGroup(groupId, httpRequest)
            .onErrorResume(error -> 
                groupManagementService.logGroupOperationFailure(
                    AuditAction.GROUP_DELETED,
                    groupId,
                    null,
                    error.getMessage(),
                    httpRequest
                )
                .then(Mono.error(error))
            )
            .then(Mono.just(ResponseEntity.noContent().build()));
    }

    /**
     * Assigns multiple APIs to a group. Operation is idempotent.
     *
     * @param groupId the group ID
     * @param req the request containing API IDs to assign
     * @param httpRequest the HTTP request for audit logging
     * @return a Flux of created API-group links
     */
    @PostMapping("/groups/{groupId}/apis")
    public Flux<ApiGroupLink> assignApis(@PathVariable String groupId, @Valid @RequestBody AssignApisRequest req,
                                         ServerHttpRequest httpRequest) {
        return apiGroupLinkService.assignApis(groupId, req, httpRequest);
    }

    /**
     * Removes an API from a group.
     *
     * @param groupId the group ID
     * @param apiId the API ID to remove
     * @param httpRequest the HTTP request for audit logging
     * @return a ResponseEntity with 204 No Content on success
     */
    @DeleteMapping("/groups/{groupId}/apis/{apiId}")
    public Mono<ResponseEntity<Void>> removeApiFromGroup(@PathVariable String groupId, @PathVariable String apiId,
                                                          ServerHttpRequest httpRequest) {
        return apiGroupLinkService.removeApiFromGroup(groupId, apiId, httpRequest)
                .thenReturn(ResponseEntity.noContent().build());
    }

    /**
     * Assigns multiple users to a group. Operation is idempotent.
     *
     * @param groupId the group ID
     * @param req the request containing user IDs to assign
     * @param httpRequest the HTTP request for audit logging
     * @return a Flux of created group memberships
     */
    @PostMapping("/groups/{groupId}/users")
    public Flux<GroupMembership> assignUsers(@PathVariable String groupId, @Valid @RequestBody AssignUsersRequest req,
                                             ServerHttpRequest httpRequest) {
        return groupMembershipService.assignUsers(groupId, req, httpRequest);
    }

    /**
     * Removes a user from a group.
     *
     * @param groupId the group ID
     * @param userId the user ID to remove
     * @param httpRequest the HTTP request for audit logging
     * @return a ResponseEntity with 204 No Content on success
     */
    @DeleteMapping("/groups/{groupId}/users/{userId}")
    public Mono<ResponseEntity<Void>> removeUserFromGroup(@PathVariable String groupId, @PathVariable String userId,
                                                          ServerHttpRequest httpRequest) {
        return groupMembershipService.removeUserFromGroup(groupId, userId, httpRequest)
                .thenReturn(ResponseEntity.noContent().build());
    }

    /**
     * Updates user roles by username.
     *
     * @param username the username
     * @param req the request containing new roles
     * @param httpRequest the HTTP request for audit logging
     * @return a Mono containing the updated user
     */
    @PatchMapping("/users/{username}/roles")
    public Mono<User> updateUserRoles(@PathVariable String username, @Valid @RequestBody UpdateUserRolesRequest req,
                                      ServerHttpRequest httpRequest) {
        return userAdminService.updateUserRoles(username, req, httpRequest)
                .onErrorResume(error ->
                    auditService.logUserOperationFailure(
                            AuditAction.USER_ROLES_CHANGED,
                            null,
                            username,
                            error.getMessage(),
                            httpRequest
                    ).then(Mono.error(error))
                );
    }

    /**
     * Creates a new user with the specified username, password, and roles.
     *
     * @param req the request containing username, password, and roles
     * @param httpRequest the HTTP request for audit logging
     * @return a Mono containing the newly created user
     */
    @PostMapping("/users")
    public Mono<User> createUser(@Valid @RequestBody CreateUserRequest req, ServerHttpRequest httpRequest) {
        return userAdminService.createUser(req, httpRequest)
                .onErrorResume(error ->
                    auditService.logUserOperationFailure(
                            AuditAction.USER_CREATED,
                            null,
                            req.getUsername(),
                            error.getMessage(),
                            httpRequest
                    ).then(Mono.error(error))
                );
    }

    /**
     * Lists all users with their role and group count summary.
     *
     * @return a Flux of user summaries
     */
    @GetMapping("/users")
    public Flux<UserSummaryResponse> listUsers() {
        return userAdminService.listAllUsers();
    }

    /**
     * Retrieves detailed information about a specific user including their groups.
     *
     * @param userId the user ID
     * @return a ResponseEntity with user details or 404 if not found
     */
    @GetMapping("/users/{userId}")
    public Mono<ResponseEntity<UserDetailResponse>> getUserDetail(@PathVariable String userId) {
        return userAdminService.getUserDetail(userId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Updates user roles by user ID.
     *
     * @param userId the user ID
     * @param req the request containing new roles
     * @param httpRequest the HTTP request for audit logging
     * @return a Mono containing the updated user
     */
    @PatchMapping("/users/{userId}/roles")
    public Mono<User> updateUserRolesById(@PathVariable String userId, @Valid @RequestBody UpdateUserRolesRequest req,
                                          ServerHttpRequest httpRequest) {
        return userAdminService.updateUserRolesById(userId, req, httpRequest)
                .onErrorResume(error ->
                    auditService.logUserOperationFailure(
                            AuditAction.USER_ROLES_CHANGED,
                            userId,
                            null,
                            error.getMessage(),
                            httpRequest
                    ).then(Mono.error(error))
                );
    }

    /**
     * Updates user information with partial updates for username, password, and roles.
     * Username must be unique, password is encrypted with BCrypt.
     * Prevents removal of the last SUPERADMIN role to maintain system access.
     *
     * @param userId the user ID to update
     * @param req the request containing optional username, password, and roles
     * @param httpRequest the HTTP request for audit logging
     * @return a ResponseEntity with the updated user details
     */
    @PatchMapping("/users/{userId}")
    public Mono<ResponseEntity<UpdateUserResponse>> updateUser(@PathVariable String userId, @Valid @RequestBody UpdateUserRequest req,
                                                                ServerHttpRequest httpRequest) {
        return userAdminService.updateUser(userId, req, httpRequest)
                .map(ResponseEntity::ok)
                .onErrorResume(error ->
                    auditService.logUserOperationFailure(
                            AuditAction.USER_UPDATED,
                            userId,
                            null,
                            error.getMessage(),
                            httpRequest
                    ).then(Mono.error(error))
                );
    }

    /**
     * Assigns multiple groups to a user. Operation is idempotent.
     *
     * @param userId the user ID
     * @param req the request containing group IDs to assign
     * @param httpRequest the HTTP request for audit logging
     * @return a ResponseEntity with 204 No Content on success
     */
    @PostMapping("/users/{userId}/groups")
    public Mono<ResponseEntity<Void>> assignGroupsToUser(@PathVariable String userId, @Valid @RequestBody AssignGroupsRequest req,
                                                         ServerHttpRequest httpRequest) {
        return groupMembershipService.assignGroupsToUser(userId, req, httpRequest)
                .collectList()
                .thenReturn(ResponseEntity.noContent().<Void>build());
    }

    /**
     * Removes a user from a specific group.
     *
     * @param userId the user ID
     * @param groupId the group ID to remove the user from
     * @param httpRequest the HTTP request for audit logging
     * @return a ResponseEntity with 204 No Content on success
     */
    @DeleteMapping("/users/{userId}/groups/{groupId}")
    public Mono<ResponseEntity<Void>> removeGroupFromUser(@PathVariable String userId, @PathVariable String groupId,
                                                          ServerHttpRequest httpRequest) {
        return groupMembershipService.removeGroupFromUser(userId, groupId, httpRequest)
                .thenReturn(ResponseEntity.noContent().<Void>build());
    }

    /**
     * Deletes a user from the system.
     * Prevents deleting own account and the last SUPERADMIN.
     * For TRIAL users, also deletes their auto-created workspace.
     *
     * @param userId the user ID to delete
     * @param authentication the authenticated user (to prevent self-deletion)
     * @param httpRequest the HTTP request for audit logging
     * @return a ResponseEntity with 204 No Content on success
     */
    @DeleteMapping("/users/{userId}")
    public Mono<ResponseEntity<Void>> deleteUser(@PathVariable String userId, Authentication authentication,
                                                  ServerHttpRequest httpRequest) {
        String currentUsername = authentication.getName();
        return userAdminService.deleteUser(userId, currentUsername, httpRequest)
                .thenReturn(ResponseEntity.noContent().<Void>build())
                .onErrorResume(error ->
                    auditService.logUserOperationFailure(
                            AuditAction.USER_DELETED,
                            userId,
                            null,
                            error.getMessage(),
                            httpRequest
                    ).then(Mono.error(error))
                );
    }

    /**
     * Lists all APIs that are not assigned to any group (orphan APIs).
     *
     * @return a Flux of orphan API summaries
     */
    @GetMapping("/apis/orphans")
    public Flux<ApiSummaryResponse> getOrphanApis() {
        return apiGroupLinkService.listOrphanApis();
    }

    /**
     * Assigns multiple groups to an API. Operation is idempotent.
     *
     * @param apiId the API ID
     * @param req the request containing group IDs to assign
     * @param httpRequest the HTTP request for audit logging
     * @return a Flux of created API-group links
     */
    @PostMapping("/apis/{apiId}/groups")
    @PreAuthorize("hasAnyRole('IT','SUPERADMIN','TRIAL')")
    public Flux<ApiGroupLink> assignGroupsToApi(@PathVariable String apiId, @Valid @RequestBody AssignGroupsToApiRequest req,
                                                 ServerHttpRequest httpRequest) {
        return apiGroupLinkService.assignGroupsToApi(apiId, req, httpRequest);
    }

    /**
     * Retrieves all groups linked to a specific API.
     *
     * @param apiId the API ID
     * @return a Flux of GroupSummaryResponse for groups linked to the API
     */
    @GetMapping("/apis/{apiId}/groups")
    @PreAuthorize("hasAnyRole('IT','SUPERADMIN','TRIAL')")
    public Flux<GroupSummaryResponse> getGroupsForApi(@PathVariable String apiId) {
        return apiGroupLinkService.getGroupsForApi(apiId);
    }

    /**
     * Lists all trial users with their status and expiration information.
     * 
     * Purpose:
     * - Monitor trial user activity and expiration
     * - Track trial conversions and usage
     * - Identify expired trials for cleanup
     * 
     * @return a Flux of TrialUserResponse containing trial user details
     */
    @GetMapping("/trial-users")
    public Flux<TrialUserResponse> listTrialUsers() {
        return trialRegistrationService.listAllTrialUsers()
            .map(user -> {
                long daysRemaining = trialRegistrationService.getDaysRemaining(user);
                boolean isExpired = trialRegistrationService.isTrialExpired(user);
                
                return TrialUserResponse.builder()
                    .id(user.getId())
                    .email(user.getUsername())
                    .isActive(user.isActive())
                    .trialStartDate(user.getTrialStartDate())
                    .trialEndDate(user.getTrialEndDate())
                    .daysRemaining(daysRemaining)
                    .isExpired(isExpired)
                    .createdAt(user.getCreatedAt())
                    .workspaceCode(user.getId() != null ? "trial-" + user.getId() : null)
                    .roles(user.getRoles().stream()
                        .map(Role::name)
                        .toList())
                    .build();
            });
    }

}
