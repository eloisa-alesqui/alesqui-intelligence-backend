package es.alesqui.intelligence.service.access;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import es.alesqui.intelligence.dto.admin.ApiSummaryResponse;
import es.alesqui.intelligence.dto.admin.AssignApisRequest;
import es.alesqui.intelligence.dto.admin.AssignGroupsToApiRequest;
import es.alesqui.intelligence.dto.admin.GroupSummaryResponse;
import es.alesqui.intelligence.model.access.ApiGroupLink;
import es.alesqui.intelligence.model.access.GroupMembership;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedApiDocument;
import es.alesqui.intelligence.repository.ApiGroupLinkRepository;
import es.alesqui.intelligence.repository.GroupMembershipRepository;
import es.alesqui.intelligence.repository.GroupRepository;
import es.alesqui.intelligence.repository.UnifiedApiRepository;
import es.alesqui.intelligence.repository.UserRepository;
import es.alesqui.intelligence.service.UnifiedApiService;
import es.alesqui.intelligence.service.identity.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Service responsible for managing API-Group links and API visibility.
 * Handles the many-to-many relationship between APIs and groups, and determines
 * which APIs are visible to users based on group membership.
 * 
 * Visibility rules:
 * - If an API has zero links it is PUBLIC (everyone sees it).
 * - If an API has links, user must belong to at least one linked group.
 * - SUPERADMIN users bypass all visibility restrictions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApiGroupLinkService {

    private final ApiGroupLinkRepository apiGroupLinkRepository;
    private final GroupRepository groupRepository;
    private final UnifiedApiService unifiedApiService;
    private final UserRepository userRepository;
    private final UnifiedApiRepository unifiedApiRepository;
    private final GroupMembershipRepository groupMembershipRepository;
    private final UserService userService;

    /**
     * Assigns APIs to a group.
     * 
     * @param groupId the ID of the group
     * @param req     the request containing API IDs to assign
     * @return a Flux of assigned API group links
     */
    public Flux<ApiGroupLink> assignApis(String groupId, AssignApisRequest req) {
        // Validate group exists
        Mono<Boolean> groupExists = groupRepository.existsById(groupId)
                .filter(exists -> exists)
                .switchIfEmpty(Mono.error(new IllegalStateException("Group not found: " + groupId)));

        return groupExists.flatMapMany(exists -> Flux.fromIterable(req.getApiIds())
                .flatMap(apiId -> unifiedApiService.findById(apiId)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("API not found: " + apiId)))
                        .then(apiGroupLinkRepository.existsByApiIdAndGroupId(apiId, groupId)
                                .flatMap(linkExists -> linkExists ? Mono.empty()
                                        : apiGroupLinkRepository.save(buildLink(apiId, groupId))
                                                .doOnSuccess(link -> log.info("Linked API {} to group {}", apiId, groupId))))));
    }

    /**
     * Removes an API from a group by deleting the ApiGroupLink.
     * 
     * @param groupId the ID of the group
     * @param apiId   the ID of the API to remove
     * @return a Mono signaling completion
     */
    public Mono<Void> removeApiFromGroup(String groupId, String apiId) {
        return groupRepository.existsById(groupId)
                .filter(exists -> exists)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Group not found: " + groupId)))
                .then(unifiedApiService.findById(apiId)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("API not found: " + apiId))))
                .then(apiGroupLinkRepository.existsByApiIdAndGroupId(apiId, groupId)
                        .flatMap(exists -> {
                            if (!exists) {
                                return Mono.error(new IllegalArgumentException(
                                        "API " + apiId + " is not linked to group " + groupId));
                            }
                            return apiGroupLinkRepository.findByApiId(apiId)
                                    .filter(link -> link.getGroupId().equals(groupId))
                                    .next()
                                    .flatMap(link -> apiGroupLinkRepository.deleteById(link.getId())
                                            .doOnSuccess(v -> log.info("Unlinked API {} from group {}", apiId, groupId)));
                        }));
    }

    /**
     * Lists all orphan APIs (APIs not linked to any group).
     * 
     * @return a Flux of API summary responses for APIs without group links
     */
    public Flux<ApiSummaryResponse> listOrphanApis() {
        return unifiedApiService.findAll()
                .flatMap(api -> apiGroupLinkRepository.findByApiId(api.getId())
                        .hasElements()
                        .filter(hasLinks -> !hasLinks)
                        .map(hasLinks -> ApiSummaryResponse.builder()
                                .id(api.getId())
                                .name(api.getName())
                                .description(api.getDescription())
                                .active(api.isActive())
                                .version(api.getVersion())
                                .tags(api.getTags() == null ? List.of()
                                        : api.getTags().stream().map(t -> t.getName()).toList())
                                .isPublic(true)
                                .build()));
    }

    /**
     * Automatically links an API to a TRIAL user's workspace if the user has ROLE_TRIAL.
     * This method is called after an API is created.
     * 
     * @param apiId the ID of the newly created API
     * @param username the username of the user who created the API
     * @return Mono signaling completion
     */
    public Mono<Void> autoLinkApiToTrialWorkspace(String apiId, String username) {
        return userRepository.findByUsername(username)
            .flatMap(user -> {
                // Check if user has ROLE_TRIAL
                boolean isTrialUser = user.getRoles() != null && 
                        user.getRoles().contains(es.alesqui.intelligence.model.core.enums.Role.ROLE_TRIAL);
                
                if (!isTrialUser) {
                    log.debug("User {} is not a TRIAL user, skipping auto-link", username);
                    return Mono.empty();
                }
                
                // Find the user's trial workspace
                String workspaceCode = "trial-" + user.getId();
                log.info("User {} is TRIAL, auto-linking API {} to workspace {}", username, apiId, workspaceCode);
                
                return groupRepository.findByCodeIgnoreCase(workspaceCode)
                    .flatMap(group -> {
                        // Check if link already exists
                        return apiGroupLinkRepository.existsByApiIdAndGroupId(apiId, group.getId())
                            .flatMap(exists -> {
                                if (exists) {
                                    log.debug("API {} already linked to group {}", apiId, group.getId());
                                    return Mono.empty();
                                }
                                
                                // Create the link
                                ApiGroupLink link = buildLink(apiId, group.getId());
                                return apiGroupLinkRepository.save(link)
                                    .doOnSuccess(savedLink -> log.info(
                                        "Successfully auto-linked API {} to trial workspace {} for user {}", 
                                        apiId, workspaceCode, username))
                                    .then();
                            });
                    })
                    .switchIfEmpty(Mono.defer(() -> {
                        log.warn("Trial workspace {} not found for user {}, cannot auto-link API", 
                            workspaceCode, username);
                        return Mono.empty();
                    }));
            })
            .onErrorResume(e -> {
                log.error("Failed to auto-link API {} for user {}", apiId, username, e);
                return Mono.empty(); // Don't fail the API creation if auto-link fails
            });
    }

    /**
     * Counts the number of APIs linked to a group.
     * 
     * @param groupId the ID of the group
     * @return a Mono emitting the count
     */
    public Mono<Long> countApisByGroupId(String groupId) {
        return apiGroupLinkRepository.findByGroupId(groupId).count();
    }

    /**
     * Deletes all API links for a specific group.
     * Used when deleting a group or during cleanup operations.
     * 
     * @param groupId the ID of the group
     * @return a Mono signaling completion
     */
    public Mono<Void> deleteAllByGroupId(String groupId) {
        return apiGroupLinkRepository.findByGroupId(groupId)
                .flatMap(link -> apiGroupLinkRepository.deleteById(link.getId()))
                .then()
                .doOnSuccess(v -> log.debug("Deleted all API links for group {}", groupId));
    }

    /**
     * Deletes all group links for a specific API.
     * Used when deleting an API or during cleanup operations.
     * 
     * @param apiId the ID of the API
     * @return a Mono signaling completion
     */
    public Mono<Void> deleteAllByApiId(String apiId) {
        return apiGroupLinkRepository.findByApiId(apiId)
                .flatMap(link -> apiGroupLinkRepository.deleteById(link.getId()))
                .then()
                .doOnSuccess(v -> log.debug("Deleted all group links for API {}", apiId));
    }

    /**
     * Returns all UnifiedApiDocument objects visible to the given user.
     * 
     * Visibility rules:
     * - SUPERADMIN sees all APIs
     * - Public APIs (no group links) are visible to everyone
     * - Private APIs (with group links) are only visible if user belongs to at least one linked group
     * 
     * @param userId user id
     * @return Flux of visible APIs
     */
    public Flux<UnifiedApiDocument> listVisibleApis(String userId) {
        // SUPERADMIN bypass
        return userService.isUserSuperAdmin(userId)
                .flatMapMany(isSuperAdmin -> {
                    if (isSuperAdmin) {
                        return unifiedApiRepository.findAll();
                    }

                    Mono<Set<String>> userGroupIds = (userId == null)
                            ? Mono.just(Set.of())
                            : groupMembershipRepository.findByUserId(userId)
                                    .map(GroupMembership::getGroupId)
                                    .collect(Collectors.toSet());

                    // First get all APIs, then filter reactively by visibility rules.
                    return unifiedApiRepository.findAll()
                            .flatMap(api -> apiGroupLinkRepository.findByApiId(api.getId())
                                    .collectList()
                                    .flatMapMany(links -> {
                                        if (links.isEmpty()) {
                                            // Public API (no links)
                                            return Flux.just(api);
                                        }
                                        return userGroupIds.flatMapMany(groups -> {
                                            boolean allowed = links.stream()
                                                    .map(ApiGroupLink::getGroupId)
                                                    .anyMatch(groups::contains);
                                            return allowed ? Flux.just(api) : Flux.empty();
                                        });
                                    })
                            );
                });
    }

    /**
     * Checks if the user can access a specific API.
     * 
     * Visibility rules:
     * - SUPERADMIN can access all APIs
     * - Public APIs (no group links) are accessible to everyone
     * - Private APIs (with group links) are only accessible if user belongs to at least one linked group
     * 
     * @param userId user id
     * @param apiId api id
     * @return Mono true if visible
     */
    public Mono<Boolean> canAccess(String userId, String apiId) {
        // SUPERADMIN bypass
        return userService.isUserSuperAdmin(userId)
                .flatMap(isSuperAdmin -> {
                    if (isSuperAdmin) return Mono.just(true);

                    Mono<Set<String>> groupIdsMono = (userId == null)
                            ? Mono.just(Set.of())
                            : groupMembershipRepository.findByUserId(userId)
                                    .map(GroupMembership::getGroupId)
                                    .collect(Collectors.toSet());

                    Mono<Boolean> hasLinks = apiGroupLinkRepository.findByApiId(apiId)
                            .hasElements();

                    Mono<Boolean> anyGroupMatch = apiGroupLinkRepository.findByApiId(apiId)
                            .map(ApiGroupLink::getGroupId)
                            .collect(Collectors.toSet())
                            .zipWith(groupIdsMono, (apiGroups, userGroups) -> {
                                for (String g : apiGroups) {
                                    if (userGroups.contains(g)) return true;
                                }
                                return false;
                            });

                    return hasLinks.flatMap(linked -> {
                        if (!linked) {
                            return Mono.just(true); // public
                        }
                        return anyGroupMatch;
                    });
                });
    }

    /**
     * Builds an API group link.
     * 
     * @param apiId   the ID of the API
     * @param groupId the ID of the group
     * @return the created API group link
     */
    private ApiGroupLink buildLink(String apiId, String groupId) {
        ApiGroupLink l = new ApiGroupLink();
        l.setApiId(apiId);
        l.setGroupId(groupId);
        l.setCreatedAt(Instant.now());
        return l;
    }

    /**
     * Assigns groups to an API.
     * 
     * @param apiId the ID of the API
     * @param req   the request containing group IDs to assign
     * @return a Flux of assigned API group links
     */
    public Flux<ApiGroupLink> assignGroupsToApi(String apiId, AssignGroupsToApiRequest req) {
        // Validate API exists
        Mono<Boolean> apiExists = unifiedApiService.findById(apiId)
                .map(api -> true)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("API not found: " + apiId)));

        return apiExists.flatMapMany(exists -> Flux.fromIterable(req.getGroupIds())
                .flatMap(groupId -> groupRepository.existsById(groupId)
                        .filter(groupExists -> groupExists)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("Group not found: " + groupId)))
                        .then(apiGroupLinkRepository.existsByApiIdAndGroupId(apiId, groupId)
                                .flatMap(linkExists -> linkExists ? Mono.empty()
                                        : apiGroupLinkRepository.save(buildLink(apiId, groupId))
                                                .doOnSuccess(link -> log.info("Linked API {} to group {}", apiId, groupId))))));
    }

    /**
     * Retrieves all groups linked to a specific API with user and API counts.
     * 
     * @param apiId the ID of the API
     * @return a Flux of group summary responses for groups linked to the API
     */
    public Flux<GroupSummaryResponse> getGroupsForApi(String apiId) {
        // Validate API exists
        return unifiedApiService.findById(apiId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("API not found: " + apiId)))
                .flatMapMany(api -> apiGroupLinkRepository.findByApiId(apiId)
                        .flatMap(link -> groupRepository.findById(link.getGroupId()))
                        .flatMap(group -> Mono.zip(
                                groupMembershipRepository.findByGroupId(group.getId()).count(),
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
                        .doOnNext(group -> log.debug("Retrieved group {} for API {}", group.getCode(), apiId)));
    }
}
