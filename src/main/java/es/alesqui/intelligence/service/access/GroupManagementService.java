package es.alesqui.intelligence.service.access;

import java.time.Instant;
import java.util.List;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;

import es.alesqui.intelligence.dto.admin.GroupCreateRequest;
import es.alesqui.intelligence.dto.admin.GroupDetailResponse;
import es.alesqui.intelligence.dto.admin.GroupSummaryResponse;
import es.alesqui.intelligence.dto.admin.GroupUpdateRequest;
import es.alesqui.intelligence.dto.admin.ApiSummaryResponse;
import es.alesqui.intelligence.dto.admin.UserSummaryResponse;
import es.alesqui.intelligence.model.access.Group;
import es.alesqui.intelligence.model.access.GroupMembership;
import es.alesqui.intelligence.model.access.ApiGroupLink;
import es.alesqui.intelligence.model.audit.AuditAction;
import es.alesqui.intelligence.model.audit.EntityType;
import es.alesqui.intelligence.model.core.enums.Role;
import es.alesqui.intelligence.repository.GroupRepository;
import es.alesqui.intelligence.repository.GroupMembershipRepository;
import es.alesqui.intelligence.repository.ApiGroupLinkRepository;
import es.alesqui.intelligence.repository.UserRepository;
import es.alesqui.intelligence.service.UnifiedApiService;
import es.alesqui.intelligence.service.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service responsible for CRUD operations on groups.
 * Handles group creation, updates, deletion, and queries.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GroupManagementService {

    private final GroupRepository groupRepository;
    private final GroupMembershipRepository membershipRepository;
    private final ApiGroupLinkRepository apiGroupLinkRepository;
    private final UserRepository userRepository;
    private final UnifiedApiService unifiedApiService;
    private final AuditService auditService;

    /**
     * Creates a new group.
     * 
     * @param req the request containing group creation details
     * @param request the HTTP request for audit logging
     * @return the created group
     */
    public Mono<Group> createGroup(GroupCreateRequest req, ServerHttpRequest request) {
        Group g = new Group();
        g.setCode(req.getCode().trim());
        g.setName(req.getName().trim());
        g.setDescription(req.getDescription());
        g.setCreatedAt(Instant.now());
        
        return groupRepository.existsByCodeIgnoreCase(g.getCode())
                .flatMap(exists -> exists
                        ? Mono.error(new IllegalArgumentException("Group code already exists: " + g.getCode()))
                        : groupRepository.save(g)
                                .flatMap(group -> {
                                    log.info("Created group: {} ({})", group.getName(), group.getCode());
                                    // Log group creation audit event
                                    return auditService.logAction(
                                            AuditAction.GROUP_CREATED,
                                            EntityType.GROUP,
                                            group.getId(),
                                            group.getName(),
                                            "Group created with code: " + group.getCode(),
                                            request
                                    ).thenReturn(group);
                                }));
    }

    /**
     * Updates an existing group.
     * 
     * @param groupId the ID of the group to update
     * @param req     the request containing updated group details
     * @param request the HTTP request for audit logging
     * @return the updated group
     */
    public Mono<Group> updateGroup(String groupId, GroupUpdateRequest req, ServerHttpRequest request) {
        return groupRepository.findById(groupId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Group not found: " + groupId)))
                .flatMap(originalGroup -> {
                    // Create a copy of the original state for audit logging
                    Group groupBeforeUpdate = new Group();
                    groupBeforeUpdate.setId(originalGroup.getId());
                    groupBeforeUpdate.setCode(originalGroup.getCode());
                    groupBeforeUpdate.setName(originalGroup.getName());
                    groupBeforeUpdate.setDescription(originalGroup.getDescription());
                    groupBeforeUpdate.setCreatedAt(originalGroup.getCreatedAt());
                    
                    // Apply updates
                    originalGroup.setName(req.getName().trim());
                    originalGroup.setDescription(req.getDescription());
                    
                    return groupRepository.save(originalGroup)
                            .flatMap(updatedGroup -> {
                                log.info("Updated group: {}", updatedGroup.getCode());
                                // Log group update audit event with before/after state
                                return auditService.logActionWithData(
                                        AuditAction.GROUP_UPDATED,
                                        EntityType.GROUP,
                                        updatedGroup.getId(),
                                        updatedGroup.getName(),
                                        groupBeforeUpdate,
                                        updatedGroup,
                                        request
                                ).thenReturn(updatedGroup);
                            });
                });
    }

    /**
     * Deletes a group by its ID.
     * Prevents deletion if the group has any users or APIs associated with it.
     * 
     * @param groupId the ID of the group to delete
     * @param request the HTTP request for audit logging
     * @return a Mono signaling completion
     */
    public Mono<Void> deleteGroup(String groupId, ServerHttpRequest request) {
        return groupRepository.findById(groupId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Group not found: " + groupId)))
                .flatMap(group -> {
                    // Check if group has any memberships
                    Mono<Long> membershipCount = membershipRepository.findByGroupId(groupId).count();
                    // Check if group has any API links
                    Mono<Long> apiLinkCount = apiGroupLinkRepository.findByGroupId(groupId).count();

                    return Mono.zip(membershipCount, apiLinkCount)
                            .flatMap(tuple -> {
                                long userCount = tuple.getT1();
                                long apiCount = tuple.getT2();

                                if (userCount > 0 && apiCount > 0) {
                                    return Mono.error(new IllegalStateException(
                                            "Cannot delete group: it has " + userCount + " user(s) and " + apiCount
                                                    + " API(s) associated"));
                                } else if (userCount > 0) {
                                    return Mono.error(new IllegalStateException(
                                            "Cannot delete group: it has " + userCount + " user(s) associated"));
                                } else if (apiCount > 0) {
                                    return Mono.error(new IllegalStateException(
                                            "Cannot delete group: it has " + apiCount + " API(s) associated"));
                                }

                                return groupRepository.deleteById(groupId)
                                        .then(
                                                // Log group deletion audit event
                                                auditService.logAction(
                                                        AuditAction.GROUP_DELETED,
                                                        EntityType.GROUP,
                                                        group.getId(),
                                                        group.getName(),
                                                        "Group deleted with code: " + group.getCode(),
                                                        request
                                                )
                                        )
                                        .doOnSuccess(v -> log.info("Deleted group: {} ({})", group.getName(), group.getCode()));
                            });
                });
    }

    /**
     * Lists all groups with user and API counts.
     * 
     * @return a Flux of group summary responses
     */
    public Flux<GroupSummaryResponse> listGroupsWithCounts() {
        return groupRepository.findAll()
                .flatMap(group -> Mono.zip(
                        membershipRepository.findByGroupId(group.getId()).count(),
                        apiGroupLinkRepository.findByGroupId(group.getId()).count())
                        .map(tuple -> GroupSummaryResponse.builder()
                                .id(group.getId())
                                .code(group.getCode())
                                .name(group.getName())
                                .description(group.getDescription())
                                .createdAt(group.getCreatedAt())
                                .userCount(tuple.getT1())
                                .apiCount(tuple.getT2())
                                .build()));
    }

    /**
     * Retrieves detailed information about a group.
     * 
     * @param groupId the ID of the group
     * @return the group detail response
     */
    public Mono<GroupDetailResponse> getGroupDetail(String groupId) {
        return groupRepository.findById(groupId)
                .switchIfEmpty(Mono.empty())
                .flatMap(group -> {
                    // Load memberships -> users
                    Mono<List<UserSummaryResponse>> usersMono = membershipRepository.findByGroupId(groupId)
                            .collectList()
                            .flatMapMany(members -> {
                                log.debug("Group {} has {} raw membership records", groupId, members.size());
                                if (members.isEmpty())
                                    return Flux.empty();
                                var userIds = members.stream().map(GroupMembership::getUserId).toList();
                                return userRepository.findAllById(userIds);
                            })
                            .flatMap(user -> membershipRepository.findByUserId(user.getId())
                                    .count()
                                    .map(groupCount -> UserSummaryResponse.builder()
                                            .id(user.getId())
                                            .username(user.getUsername())
                                            .roles(user.getRoles() == null ? List.of()
                                                    : user.getRoles().stream().map(Role::name).toList())
                                            .active(user.isActive())
                                            .groupCount(groupCount.intValue())
                                            .build()))
                            .collectList()
                            .doOnNext(list -> log.debug("Group {} resolved {} user summaries", groupId, list.size()));

                    // Load API links -> unified documents
                    Mono<List<ApiSummaryResponse>> apisMono = apiGroupLinkRepository.findByGroupId(groupId)
                            .collectList()
                            .flatMapMany(links -> {
                                log.debug("Group {} has {} api link records", groupId, links.size());
                                if (links.isEmpty())
                                    return Flux.empty();
                                var apiIds = links.stream().map(ApiGroupLink::getApiId).distinct().toList();
                                return Flux.fromIterable(apiIds)
                                        .flatMap(id -> unifiedApiService.findById(id)
                                                .flatMap(doc -> apiGroupLinkRepository.findByApiId(doc.getId())
                                                        .hasElements()
                                                        .map(hasLinks -> ApiSummaryResponse.builder()
                                                                .id(doc.getId())
                                                                .name(doc.getName())
                                                                .description(doc.getDescription())
                                                                .active(doc.isActive())
                                                                .version(doc.getVersion())
                                                                .tags(doc.getTags() == null ? List.of()
                                                                        : doc.getTags().stream().map(t -> t.getName())
                                                                                .toList())
                                                                .isPublic(!hasLinks)
                                                                .build()))
                                                .onErrorResume(e -> {
                                                    log.warn("Failed to load unified API {} for group {}: {}", id,
                                                            groupId, e.getMessage());
                                                    return Mono.empty();
                                                }));
                            })
                            .collectList()
                            .doOnNext(list -> log.debug("Group {} resolved {} api summaries", groupId, list.size()));

                    return Mono.zip(usersMono, apisMono)
                            .map(tuple -> GroupDetailResponse.builder()
                                    .id(group.getId())
                                    .code(group.getCode())
                                    .name(group.getName())
                                    .description(group.getDescription())
                                    .createdAt(group.getCreatedAt())
                                    .userCount(tuple.getT1().size())
                                    .apiCount(tuple.getT2().size())
                                    .users(tuple.getT1())
                                    .apis(tuple.getT2())
                                    .build())
                            .doOnNext(r -> log.debug("Built GroupDetailResponse for {} with {} users and {} apis",
                                    groupId, r.getUserCount(), r.getApiCount()));
                });
    }

    /**
     * Finds a group by its code (case-insensitive).
     * 
     * @param code the group code
     * @return the group if found
     */
    public Mono<Group> findByCode(String code) {
        return groupRepository.findByCodeIgnoreCase(code);
    }

    /**
     * Checks if a group exists by its code (case-insensitive).
     * 
     * @param code the group code
     * @return true if exists, false otherwise
     */
    public Mono<Boolean> existsByCode(String code) {
        return groupRepository.existsByCodeIgnoreCase(code);
    }

    /**
     * Finds a group by its ID.
     * 
     * @param groupId the group ID
     * @return the group if found
     */
    public Mono<Group> findById(String groupId) {
        return groupRepository.findById(groupId);
    }

    /**
     * Saves a group.
     * 
     * @param group the group to save
     * @return the saved group
     */
    public Mono<Group> save(Group group) {
        return groupRepository.save(group);
    }

    /**
     * Retrieves all groups for a specific user with user and API counts.
     * 
     * @param userId the user ID
     * @return a Flux of group summary responses for the user
     */
    public Flux<GroupSummaryResponse> getGroupsForUser(String userId) {
        return membershipRepository.findByUserId(userId)
                .flatMap(membership -> groupRepository.findById(membership.getGroupId()))
                .flatMap(group -> Mono.zip(
                        membershipRepository.findByGroupId(group.getId()).count(),
                        apiGroupLinkRepository.findByGroupId(group.getId()).count())
                        .map(tuple -> GroupSummaryResponse.builder()
                                .id(group.getId())
                                .code(group.getCode())
                                .name(group.getName())
                                .description(group.getDescription())
                                .createdAt(group.getCreatedAt())
                                .userCount(tuple.getT1())
                                .apiCount(tuple.getT2())
                                .build()))
                .doOnNext(group -> log.debug("Retrieved group {} for user {}", group.getCode(), userId));
    }

    /**
     * Logs a failed group operation for audit purposes.
     * Used in error handlers to track failed administrative actions.
     * 
     * @param action the action that was attempted
     * @param groupId the group ID (if known)
     * @param groupName the group name (if known)
     * @param errorMessage the error message
     * @param request the HTTP request for audit logging
     * @return Mono signaling completion
     */
    /**
     * Counts the total number of groups in the system.
     *
     * @return a Mono emitting the total group count.
     */
    public Mono<Long> countGroups() {
        return groupRepository.count();
    }

    public Mono<Void> logGroupOperationFailure(AuditAction action, String groupId, String groupName,
                                               String errorMessage, ServerHttpRequest request) {
        return auditService.logFailure(
                action,
                EntityType.GROUP,
                groupId != null ? groupId : "unknown",
                groupName != null ? groupName : "unknown",
                errorMessage,
                request
        );
    }
}
