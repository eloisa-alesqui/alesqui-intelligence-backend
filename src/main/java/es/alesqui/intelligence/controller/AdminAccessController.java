package es.alesqui.intelligence.controller;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
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
import es.alesqui.intelligence.dto.audit.AuditLogResponse;
import es.alesqui.intelligence.dto.audit.AuditLogSummaryResponse;
import es.alesqui.intelligence.dto.audit.AuditStatsResponse;
import es.alesqui.intelligence.dto.audit.PagedAuditLogResponse;
import es.alesqui.intelligence.model.access.ApiGroupLink;
import es.alesqui.intelligence.model.access.Group;
import es.alesqui.intelligence.model.access.GroupMembership;
import es.alesqui.intelligence.model.audit.AuditAction;
import es.alesqui.intelligence.model.audit.AuditLog;
import es.alesqui.intelligence.model.audit.EntityType;
import es.alesqui.intelligence.model.core.User;
import es.alesqui.intelligence.model.core.enums.Role;
import es.alesqui.intelligence.service.access.ApiGroupLinkService;
import es.alesqui.intelligence.service.access.GroupManagementService;
import es.alesqui.intelligence.service.access.GroupMembershipService;
import es.alesqui.intelligence.service.access.UserManagementService;
import es.alesqui.intelligence.service.audit.AuditService;
import es.alesqui.intelligence.service.trial.TrialRegistrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
public class AdminAccessController {

    private final GroupManagementService groupManagementService;
    private final GroupMembershipService groupMembershipService;
    private final ApiGroupLinkService apiGroupLinkService;
    private final UserManagementService userManagementService;
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
        return userManagementService.updateUserRoles(username, req, httpRequest)
                .onErrorResume(error -> 
                    userManagementService.logUserOperationFailure(
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
        return userManagementService.createUser(req, httpRequest)
                .onErrorResume(error -> 
                    userManagementService.logUserOperationFailure(
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
        return userManagementService.listAllUsers();
    }

    /**
     * Retrieves detailed information about a specific user including their groups.
     *
     * @param userId the user ID
     * @return a ResponseEntity with user details or 404 if not found
     */
    @GetMapping("/users/{userId}")
    public Mono<ResponseEntity<UserDetailResponse>> getUserDetail(@PathVariable String userId) {
        return userManagementService.getUserDetail(userId)
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
        return userManagementService.updateUserRolesById(userId, req, httpRequest)
                .onErrorResume(error -> 
                    userManagementService.logUserOperationFailure(
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
        return userManagementService.updateUser(userId, req, httpRequest)
                .map(ResponseEntity::ok)
                .onErrorResume(error -> 
                    userManagementService.logUserOperationFailure(
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
        return userManagementService.deleteUser(userId, currentUsername, httpRequest)
                .thenReturn(ResponseEntity.noContent().<Void>build())
                .onErrorResume(error -> 
                    userManagementService.logUserOperationFailure(
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

    // ==================== AUDIT LOG ENDPOINTS ====================

    /**
     * Retrieves recent audit logs with optional pagination.
     * 
     * Returns a list of the most recent audit log entries, ordered by timestamp descending.
     * Useful for displaying recent activity in admin dashboards.
     *
     * @param limit maximum number of logs to retrieve (default: 100, max: 500)
     * @return a Flux of audit log summaries
     */
    @GetMapping("/audit-logs/recent")
    public Flux<AuditLogSummaryResponse> getRecentAuditLogs(
            @RequestParam(defaultValue = "100") @Min(1) int limit) {
        
        log.debug("Fetching {} recent audit logs", limit);
        
        // Cap the limit to prevent excessive data retrieval
        int cappedLimit = Math.min(limit, 500);
        
        return auditService.getRecentLogs(cappedLimit)
                .map(this::mapToSummaryResponse);
    }

    /**
     * Retrieves paginated audit logs for a specific user.
     * 
     * Returns actions performed by a specific user with pagination support.
     * Useful for investigating user activity or compliance auditing.
     *
     * @param username the username to filter by
     * @param page the page number (zero-based, default: 0)
     * @param size the number of items per page (default: 50, max: 200)
     * @return a Mono containing paginated audit log details
     */
    @GetMapping("/audit-logs/user/{username}")
    public Mono<PagedAuditLogResponse<AuditLogResponse>> getAuditLogsByUser(
            @PathVariable String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        
        log.debug("Fetching audit logs for user: {} (page={}, size={})", username, page, size);
        
        // Cap size to prevent excessive data retrieval
        int cappedSize = Math.min(size, 200);
        
        return auditService.countLogsByUser(username)
                .flatMap(totalElements -> 
                    auditService.getLogsByUser(username, page, cappedSize)
                            .map(this::mapToFullResponse)
                            .collectList()
                            .map(content -> buildPagedResponse(content, page, cappedSize, totalElements))
                );
    }

    /**
     * Retrieves paginated audit logs for a specific entity (e.g., a user, group, or API).
     * 
     * Returns complete history of actions performed on a specific entity with pagination support.
     * Includes all modifications and deletions.
     *
     * @param entityType the type of entity (USER, GROUP, API, etc.)
     * @param entityId the ID of the entity
     * @param page the page number (zero-based, default: 0)
     * @param size the number of items per page (default: 50, max: 200)
     * @return a Mono containing paginated audit log details
     */
    @GetMapping("/audit-logs/entity/{entityType}/{entityId}")
    public Mono<PagedAuditLogResponse<AuditLogResponse>> getAuditLogsByEntity(
            @PathVariable EntityType entityType,
            @PathVariable String entityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        
        log.debug("Fetching audit logs for entity: {} with ID: {} (page={}, size={})", 
                  entityType, entityId, page, size);
        
        // Cap size to prevent excessive data retrieval
        int cappedSize = Math.min(size, 200);
        
        return auditService.countLogsByEntity(entityType, entityId)
                .flatMap(totalElements -> 
                    auditService.getLogsByEntity(entityType, entityId, page, cappedSize)
                            .map(this::mapToFullResponse)
                            .collectList()
                            .map(content -> buildPagedResponse(content, page, cappedSize, totalElements))
                );
    }

    /**
     * Retrieves paginated audit logs filtered by action type.
     * 
     * Returns audit logs matching a specific action with pagination support.
     * Useful for analyzing specific types of administrative activities.
     *
     * @param action the audit action to filter by
     * @param page the page number (zero-based, default: 0)
     * @param size the number of items per page (default: 50, max: 200)
     * @return a Mono containing paginated audit log summaries
     */
    @GetMapping("/audit-logs/action/{action}")
    public Mono<PagedAuditLogResponse<AuditLogSummaryResponse>> getAuditLogsByAction(
            @PathVariable AuditAction action,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        
        log.debug("Fetching audit logs for action: {} (page={}, size={})", action, page, size);
        
        // Cap size to prevent excessive data retrieval
        int cappedSize = Math.min(size, 200);
        
        return auditService.countLogsByAction(action)
                .flatMap(totalElements -> 
                    auditService.getLogsByAction(action, page, cappedSize)
                            .map(this::mapToSummaryResponse)
                            .collectList()
                            .map(content -> buildPagedResponse(content, page, cappedSize, totalElements))
                );
    }

    /**
     * Retrieves paginated audit logs within a specific date range.
     * 
     * Returns audit logs between the specified start and end timestamps with pagination support.
     * Both date parameters are required to ensure bounded queries.
     *
     * @param startDate the start date (ISO 8601 format: YYYY-MM-DDTHH:mm:ss.SSSZ)
     * @param endDate the end date (ISO 8601 format: YYYY-MM-DDTHH:mm:ss.SSSZ)
     * @param page the page number (zero-based, default: 0)
     * @param size the number of items per page (default: 50, max: 200)
     * @return a Mono containing paginated audit log summaries
     */
    @GetMapping("/audit-logs/date-range")
    public Mono<PagedAuditLogResponse<AuditLogSummaryResponse>> getAuditLogsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) String startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) String endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        
        try {
            Instant start = Instant.parse(startDate);
            Instant end = Instant.parse(endDate);
            
            log.debug("Fetching audit logs from {} to {} (page={}, size={})", start, end, page, size);
            
            // Cap size to prevent excessive data retrieval
            int cappedSize = Math.min(size, 200);
            
            return auditService.countLogsByDateRange(start, end)
                    .flatMap(totalElements -> 
                        auditService.getLogsByDateRange(start, end, page, cappedSize)
                                .map(this::mapToSummaryResponse)
                                .collectList()
                                .map(content -> buildPagedResponse(content, page, cappedSize, totalElements))
                    );
                    
        } catch (DateTimeParseException e) {
            log.error("Invalid date format provided: startDate={}, endDate={}", startDate, endDate);
            return Mono.error(new IllegalArgumentException("Invalid date format. Use ISO 8601 format: YYYY-MM-DDTHH:mm:ss.SSSZ"));
        }
    }

    /**
     * Retrieves detailed information about a specific audit log entry.
     * 
     * Returns complete details including before/after data snapshots for the specified log entry.
     *
     * @param logId the ID of the audit log entry
     * @return a ResponseEntity with full audit log details or 404 if not found
     */
    @GetMapping("/audit-logs/{logId}")
    public Mono<ResponseEntity<AuditLogResponse>> getAuditLogById(@PathVariable String logId) {
        log.debug("Fetching audit log by ID: {}", logId);
        
        return auditService.getLogById(logId)
                .map(this::mapToFullResponse)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Retrieves statistical summary of audit log data.
     * 
     * Returns aggregated metrics including:
     * - Total number of logs
     * - Success/failure counts
     * - Action type distribution
     * - Entity type distribution
     * - User activity summary
     * 
     * Useful for compliance reporting and dashboard displays.
     *
     * @return a Mono containing audit statistics
     */
    @GetMapping("/audit-logs/stats")
    public Mono<AuditStatsResponse> getAuditLogStats() {
        log.debug("Fetching audit log statistics");
        
        return auditService.getAuditStats();
    }

    // ==================== HELPER METHODS FOR AUDIT LOG MAPPING ====================

    /**
     * Builds a paginated response wrapper for audit logs.
     * 
     * @param content the list of items for the current page
     * @param page the current page number (zero-based)
     * @param size the number of items per page
     * @param totalElements the total number of items matching the query
     * @return a paginated response with metadata
     */
    private <T> PagedAuditLogResponse<T> buildPagedResponse(List<T> content, int page, int size, long totalElements) {
        int totalPages = (int) Math.ceil((double) totalElements / size);
        
        return PagedAuditLogResponse.<T>builder()
                .content(content)
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .first(page == 0)
                .last(page >= totalPages - 1)
                .hasNext(page < totalPages - 1)
                .hasPrevious(page > 0)
                .build();
    }

    /**
     * Maps an AuditLog entity to a summary response DTO.
     * 
     * @param log the audit log entity
     * @return a summary response DTO
     */
    private AuditLogSummaryResponse mapToSummaryResponse(AuditLog log) {
        return AuditLogSummaryResponse.builder()
                .id(log.getId())
                .timestamp(log.getTimestamp())
                .actorUsername(log.getActorUsername())
                .action(log.getAction())
                .actionDescription(getActionDescription(log.getAction()))
                .entityType(log.getEntityType())
                .entityName(log.getEntityName())
                .result(log.getResult())
                .build();
    }

    /**
     * Maps an AuditLog entity to a full response DTO.
     * 
     * @param log the audit log entity
     * @return a full response DTO with all details
     */
    private AuditLogResponse mapToFullResponse(AuditLog log) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .timestamp(log.getTimestamp())
                .actorUsername(log.getActorUsername())
                .actorUserId(log.getActorUserId())
                .action(log.getAction())
                .actionDescription(getActionDescription(log.getAction()))
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .entityName(log.getEntityName())
                .details(log.getDetails())
                .ipAddress(log.getIpAddress())
                .userAgent(log.getUserAgent())
                .result(log.getResult())
                .errorMessage(log.getErrorMessage())
                .previousData(log.getPreviousData())
                .newData(log.getNewData())
                .build();
    }

    /**
     * Provides a human-readable description for an audit action.
     * 
     * @param action the audit action
     * @return a descriptive string
     */
    private String getActionDescription(AuditAction action) {
        return switch (action) {
            case USER_CREATED -> "User account created";
            case USER_UPDATED -> "User information updated";
            case USER_DELETED -> "User account deleted";
            case USER_ACTIVATED -> "User account activated";
            case USER_DEACTIVATED -> "User account deactivated";
            case USER_PASSWORD_CHANGED -> "User password changed";
            case USER_ROLES_CHANGED -> "User roles modified";
            case USER_ASSIGNED_TO_GROUP -> "User assigned to group";
            case USER_REMOVED_FROM_GROUP -> "User removed from group";
            case GROUP_CREATED -> "Group created";
            case GROUP_UPDATED -> "Group information updated";
            case GROUP_DELETED -> "Group deleted";
            case GROUP_USERS_ASSIGNED -> "Users assigned to group";
            case GROUP_USER_REMOVED -> "User removed from group";
            case GROUP_APIS_ASSIGNED -> "APIs assigned to group";
            case GROUP_API_REMOVED -> "API removed from group";
            case API_CREATED -> "API created";
            case API_UPDATED -> "API updated";
            case API_DELETED -> "API deleted";
            case API_ASSIGNED_TO_GROUP -> "API assigned to group";
            case API_REMOVED_FROM_GROUP -> "API removed from group";
            case AUTH_LOGIN -> "User login attempt";
            case AUTH_LOGOUT -> "User logged out";
            case AUTH_TOKEN_REFRESH -> "Authentication token refreshed";
            case AUTH_PASSWORD_RESET -> "Password reset";
            default -> "Unknown action";
        };
    }
}
