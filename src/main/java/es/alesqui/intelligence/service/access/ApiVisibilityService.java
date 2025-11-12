package es.alesqui.intelligence.service.access;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import es.alesqui.intelligence.model.access.ApiGroupLink;
import es.alesqui.intelligence.model.access.GroupMembership;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedApiDocument;
import es.alesqui.intelligence.repository.ApiGroupLinkRepository;
import es.alesqui.intelligence.repository.GroupMembershipRepository;
import es.alesqui.intelligence.repository.UnifiedApiRepository;
import es.alesqui.intelligence.service.identity.UserService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Resolves which APIs are visible to a given user based on group membership.
 * Visibility rules:
 *  - If an API has zero links it is PUBLIC (everyone sees it).
 *  - If an API has links, user must belong to at least one linked group.
 *  - SUPERADMIN bypass supported via explicit overloads.
 */
@Service
public class ApiVisibilityService {

    private final UnifiedApiRepository unifiedApiRepository;
    private final ApiGroupLinkRepository apiGroupLinkRepository;
    private final GroupMembershipRepository groupMembershipRepository;
    private final UserService userService;

    public ApiVisibilityService(UnifiedApiRepository unifiedApiRepository,
                                ApiGroupLinkRepository apiGroupLinkRepository,
                                GroupMembershipRepository groupMembershipRepository,
                                UserService userService) {
        this.unifiedApiRepository = unifiedApiRepository;
        this.apiGroupLinkRepository = apiGroupLinkRepository;
        this.groupMembershipRepository = groupMembershipRepository;
        this.userService = userService;
    }

    /**
     * Returns all UnifiedApiDocument objects visible to the given user.
     * @param userId user id.
     * @return Flux of visible APIs.
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
     * @param userId user id.
     * @param apiId api id.
     * @return Mono true if visible.
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

}
