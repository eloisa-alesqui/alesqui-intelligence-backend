package es.alesqui.intelligence.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import es.alesqui.intelligence.dto.admin.ApiSummaryResponse;
import es.alesqui.intelligence.dto.admin.AssignApisRequest;
import es.alesqui.intelligence.dto.admin.AssignGroupsRequest;
import es.alesqui.intelligence.dto.admin.AssignUsersRequest;
import es.alesqui.intelligence.dto.admin.CreateUserRequest;
import es.alesqui.intelligence.dto.admin.GroupCreateRequest;
import es.alesqui.intelligence.dto.admin.GroupDetailResponse;
import es.alesqui.intelligence.dto.admin.GroupUpdateRequest;
import es.alesqui.intelligence.dto.admin.GroupSummaryResponse;
import es.alesqui.intelligence.dto.admin.UpdateUserRolesRequest;
import es.alesqui.intelligence.dto.admin.UpdateUserRequest;
import es.alesqui.intelligence.dto.admin.UpdateUserResponse;
import es.alesqui.intelligence.dto.admin.UserDetailResponse;
import es.alesqui.intelligence.dto.admin.UserSummaryResponse;
import es.alesqui.intelligence.model.access.ApiGroupLink;
import es.alesqui.intelligence.model.access.Group;
import es.alesqui.intelligence.model.access.GroupMembership;
import es.alesqui.intelligence.model.core.User;
import es.alesqui.intelligence.service.access.AccessAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
public class AdminAccessController {

    private final AccessAdminService adminService;

    /**
     * Creates a new group with the specified name and description.
     *
     * @param req the group creation request containing name and description
     * @return a Mono containing the newly created group
     */
    @PostMapping("/groups")
    public Mono<Group> createGroup(@Valid @RequestBody GroupCreateRequest req) {
        return adminService.createGroup(req);
    }

    /**
     * Lists all groups with their user and API counts.
     *
     * @return a Flux of group summaries including counts
     */
    @GetMapping("/groups")
    public Flux<GroupSummaryResponse> listGroups() {
        return adminService.listGroupsWithCounts();
    }

    /**
     * Retrieves detailed information about a specific group including its users and APIs.
     *
     * @param id the group ID
     * @return a ResponseEntity with group details or 404 if not found
     */
    @GetMapping("/groups/{id}")
    public Mono<ResponseEntity<GroupDetailResponse>> getGroupDetail(@PathVariable String id) {
        return adminService.getGroupDetail(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Updates a group's name and description.
     *
     * @param groupId the group ID to update
     * @param req the update request containing new name and description
     * @return a Mono containing the updated group
     */
    @PatchMapping("/groups/{groupId}")
    public Mono<Group> updateGroup(@PathVariable String groupId, @Valid @RequestBody GroupUpdateRequest req) {
        return adminService.updateGroup(groupId, req);
    }

    /**
     * Deletes a group and all its associated memberships and API links.
     *
     * @param groupId the group ID to delete
     * @return a ResponseEntity with 204 No Content on success
     */
    @DeleteMapping("/groups/{groupId}")
    public Mono<ResponseEntity<Void>> deleteGroup(@PathVariable String groupId) {
        return adminService.deleteGroup(groupId).thenReturn(ResponseEntity.noContent().build());
    }

    /**
     * Assigns multiple APIs to a group. Operation is idempotent.
     *
     * @param groupId the group ID
     * @param req the request containing API IDs to assign
     * @return a Flux of created API-group links
     */
    @PostMapping("/groups/{groupId}/apis")
    public Flux<ApiGroupLink> assignApis(@PathVariable String groupId, @Valid @RequestBody AssignApisRequest req) {
        return adminService.assignApis(groupId, req);
    }

    /**
     * Removes an API from a group.
     *
     * @param groupId the group ID
     * @param apiId the API ID to remove
     * @return a ResponseEntity with 204 No Content on success
     */
    @DeleteMapping("/groups/{groupId}/apis/{apiId}")
    public Mono<ResponseEntity<Void>> removeApiFromGroup(@PathVariable String groupId, @PathVariable String apiId) {
        return adminService.removeApiFromGroup(groupId, apiId)
                .thenReturn(ResponseEntity.noContent().build());
    }

    /**
     * Assigns multiple users to a group. Operation is idempotent.
     *
     * @param groupId the group ID
     * @param req the request containing user IDs to assign
     * @return a Flux of created group memberships
     */
    @PostMapping("/groups/{groupId}/users")
    public Flux<GroupMembership> assignUsers(@PathVariable String groupId, @Valid @RequestBody AssignUsersRequest req) {
        return adminService.assignUsers(groupId, req);
    }

    /**
     * Removes a user from a group.
     *
     * @param groupId the group ID
     * @param userId the user ID to remove
     * @return a ResponseEntity with 204 No Content on success
     */
    @DeleteMapping("/groups/{groupId}/users/{userId}")
    public Mono<ResponseEntity<Void>> removeUserFromGroup(@PathVariable String groupId, @PathVariable String userId) {
        return adminService.removeUserFromGroup(groupId, userId)
                .thenReturn(ResponseEntity.noContent().build());
    }

    /**
     * Updates user roles by username.
     *
     * @param username the username
     * @param req the request containing new roles
     * @return a Mono containing the updated user
     */
    @PatchMapping("/users/{username}/roles")
    public Mono<User> updateUserRoles(@PathVariable String username, @Valid @RequestBody UpdateUserRolesRequest req) {
        return adminService.updateUserRoles(username, req);
    }

    /**
     * Creates a new user with the specified username, password, and roles.
     *
     * @param req the request containing username, password, and roles
     * @return a Mono containing the newly created user
     */
    @PostMapping("/users")
    public Mono<User> createUser(@Valid @RequestBody CreateUserRequest req) {
        return adminService.createUser(req);
    }

    /**
     * Lists all users with their role and group count summary.
     *
     * @return a Flux of user summaries
     */
    @GetMapping("/users")
    public Flux<UserSummaryResponse> listUsers() {
        return adminService.listAllUsers();
    }

    /**
     * Retrieves detailed information about a specific user including their groups.
     *
     * @param userId the user ID
     * @return a ResponseEntity with user details or 404 if not found
     */
    @GetMapping("/users/{userId}")
    public Mono<ResponseEntity<UserDetailResponse>> getUserDetail(@PathVariable String userId) {
        return adminService.getUserDetail(userId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Updates user roles by user ID.
     *
     * @param userId the user ID
     * @param req the request containing new roles
     * @return a Mono containing the updated user
     */
    @PatchMapping("/users/{userId}/roles")
    public Mono<User> updateUserRolesById(@PathVariable String userId, @Valid @RequestBody UpdateUserRolesRequest req) {
        return adminService.updateUserRolesById(userId, req);
    }

    /**
     * Updates user information with partial updates for username, password, and roles.
     * Username must be unique, password is encrypted with BCrypt.
     * Prevents removal of the last SUPERADMIN role to maintain system access.
     *
     * @param userId the user ID to update
     * @param req the request containing optional username, password, and roles
     * @return a ResponseEntity with the updated user details
     */
    @PatchMapping("/users/{userId}")
    public Mono<ResponseEntity<UpdateUserResponse>> updateUser(@PathVariable String userId, @Valid @RequestBody UpdateUserRequest req) {
        return adminService.updateUser(userId, req)
                .map(ResponseEntity::ok);
    }

    /**
     * Assigns multiple groups to a user. Operation is idempotent.
     *
     * @param userId the user ID
     * @param req the request containing group IDs to assign
     * @return a ResponseEntity with 204 No Content on success
     */
    @PostMapping("/users/{userId}/groups")
    public Mono<ResponseEntity<Void>> assignGroupsToUser(@PathVariable String userId, @Valid @RequestBody AssignGroupsRequest req) {
        return adminService.assignGroupsToUser(userId, req)
                .collectList()
                .thenReturn(ResponseEntity.noContent().<Void>build());
    }

    /**
     * Removes a user from a specific group.
     *
     * @param userId the user ID
     * @param groupId the group ID to remove the user from
     * @return a ResponseEntity with 204 No Content on success
     */
    @DeleteMapping("/users/{userId}/groups/{groupId}")
    public Mono<ResponseEntity<Void>> removeGroupFromUser(@PathVariable String userId, @PathVariable String groupId) {
        return adminService.removeGroupFromUser(userId, groupId)
                .thenReturn(ResponseEntity.noContent().<Void>build());
    }

    /**
     * Lists all APIs that are not assigned to any group (orphan APIs).
     *
     * @return a Flux of orphan API summaries
     */
    @GetMapping("/apis/orphans")
    public Flux<ApiSummaryResponse> getOrphanApis() {
        return adminService.listOrphanApis();
    }
}
