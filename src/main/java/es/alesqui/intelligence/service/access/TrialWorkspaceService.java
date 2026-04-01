package es.alesqui.intelligence.service.access;

import java.time.Instant;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;

import es.alesqui.intelligence.model.access.Group;
import es.alesqui.intelligence.model.access.GroupMembership;
import es.alesqui.intelligence.model.audit.AuditAction;
import es.alesqui.intelligence.model.audit.EntityType;
import es.alesqui.intelligence.model.core.User;
import es.alesqui.intelligence.repository.ApiGroupLinkRepository;
import es.alesqui.intelligence.repository.GroupMembershipRepository;
import es.alesqui.intelligence.repository.GroupRepository;
import es.alesqui.intelligence.service.UnifiedApiService;
import es.alesqui.intelligence.service.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Service responsible for managing trial workspaces for TRIAL users.
 * Handles automatic creation and deletion of trial workspaces with associated resources.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TrialWorkspaceService {

    private final GroupRepository groupRepository;
    private final GroupMembershipRepository membershipRepository;
    private final ApiGroupLinkRepository apiGroupLinkRepository;
    private final UnifiedApiService unifiedApiService;
    private final AuditService auditService;

    /**
     * Creates a workspace group automatically for TRIAL users.
     * Group name: "{username}'s Workspace"
     * Group code: "trial-{userId}"
     * 
     * @param user the TRIAL user for whom to create the workspace
     * @param request the HTTP request for audit logging (optional, can be null)
     * @return Mono of the created Group
     */
    public Mono<Group> createTrialWorkspace(User user, ServerHttpRequest request) {
        String username = user.getUsername();
        String displayName = username.contains("@")
                ? username.substring(0, username.indexOf("@"))
                : username;

        Group workspace = new Group();
        workspace.setCode("trial-" + user.getId());
        workspace.setName(displayName + "'s Workspace");
        workspace.setDescription("Auto-created workspace for trial user");
        workspace.setCreatedBy(user.getId());
        workspace.setCreatedAt(Instant.now());

        log.info("Creating trial workspace for user {}: code={}, name={}",
                username, workspace.getCode(), workspace.getName());

        return groupRepository.save(workspace)
                .flatMap(savedGroup -> {
                    GroupMembership membership = new GroupMembership();
                    membership.setUserId(user.getId());
                    membership.setGroupId(savedGroup.getId());
                    membership.setCreatedAt(Instant.now());
                    return membershipRepository.save(membership).thenReturn(savedGroup);
                })
                .flatMap(savedGroup -> {
                    // Log trial workspace creation audit event (if request context available)
                    if (request != null) {
                        return auditService.logActionWithUser(
                                AuditAction.GROUP_CREATED,
                                EntityType.GROUP,
                                savedGroup.getId(),
                                savedGroup.getName(),
                                user.getUsername(),
                                user.getId(),
                                "Trial workspace auto-created: " + savedGroup.getCode(),
                                request
                        ).thenReturn(savedGroup);
                    }
                    return Mono.just(savedGroup);
                })
                .doOnSuccess(g -> log.info("Successfully created trial workspace {} for user {}",
                        g.getCode(), username))
                .doOnError(e -> log.error("Failed to create trial workspace for user {}", username, e));
    }

    /**
     * Deletes the auto-created workspace for a TRIAL user.
     * Includes deletion of all APIs and API links associated with the workspace.
     * 
     * @param userId the ID of the TRIAL user
     * @return Mono signaling completion
     */
    public Mono<Void> deleteTrialUserWorkspace(String userId) {
        String expectedGroupCode = "trial-" + userId;

        log.debug("Looking for trial workspace with code: {}", expectedGroupCode);

        return groupRepository.findByCodeIgnoreCase(expectedGroupCode)
                .flatMap(group -> {
                    log.info("Deleting trial workspace: {} (ID: {})", group.getName(), group.getId());

                    // Step 1: Check if other users are in this group (safety check)
                    return membershipRepository.findByGroupId(group.getId())
                            .count()
                            .flatMap(memberCount -> {
                                if (memberCount > 0) {
                                    log.warn("Trial workspace {} has {} other members, skipping deletion",
                                            group.getCode(), memberCount);
                                    return Mono.error(new IllegalStateException(
                                            "Trial workspace has other members, cannot delete"));
                                }

                                // Step 2: Delete APIs and their links for this group
                                Mono<Void> deleteApisAndLinks = apiGroupLinkRepository.findByGroupId(group.getId())
                                        .<Void>flatMap(link -> apiGroupLinkRepository.deleteById(link.getId())
                                                .then(unifiedApiService.deleteApi(link.getApiId()))
                                                .doOnSuccess(v -> log.debug("Deleted API {} and its link for group {}",
                                                        link.getApiId(), group.getId())))
                                        .then()
                                        .doOnSuccess(v -> log.debug("Deleted all APIs and links for group {}",
                                                group.getId()));

                                // Step 3: Delete the group itself
                                Mono<Void> deleteGroup = groupRepository.deleteById(group.getId())
                                        .doOnSuccess(v -> log.info("Deleted trial workspace: {}", group.getCode()));

                                return deleteApisAndLinks.then(deleteGroup);
                            });
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("No trial workspace found with code: {}", expectedGroupCode);
                    return Mono.empty();
                }));
    }
}
