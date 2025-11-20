package es.alesqui.intelligence.service.access;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import es.alesqui.intelligence.dto.admin.GroupCreateRequest;
import es.alesqui.intelligence.dto.admin.GroupDetailResponse;
import es.alesqui.intelligence.dto.admin.GroupSummaryResponse;
import es.alesqui.intelligence.dto.admin.GroupUpdateRequest;
import es.alesqui.intelligence.dto.admin.GroupWithCountsResponse;
import es.alesqui.intelligence.dto.admin.ApiSummaryResponse;
import es.alesqui.intelligence.dto.admin.UserSummaryResponse;
import es.alesqui.intelligence.model.access.Group;
import es.alesqui.intelligence.model.access.GroupMembership;
import es.alesqui.intelligence.model.access.ApiGroupLink;
import es.alesqui.intelligence.model.core.User;
import es.alesqui.intelligence.model.core.enums.Role;
import es.alesqui.intelligence.repository.GroupRepository;
import es.alesqui.intelligence.repository.GroupMembershipRepository;
import es.alesqui.intelligence.repository.ApiGroupLinkRepository;
import es.alesqui.intelligence.repository.UserRepository;
import es.alesqui.intelligence.service.UnifiedApiService;
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

    /**
     * Creates a new group.
     * 
     * @param req the request containing group creation details
     * @return the created group
     */
    public Mono<Group> createGroup(GroupCreateRequest req) {
        Group g = new Group();
        g.setCode(req.getCode().trim());
        g.setName(req.getName().trim());
        g.setDescription(req.getDescription());
        g.setCreatedAt(Instant.now());
        
        return groupRepository.existsByCodeIgnoreCase(g.getCode())
                .flatMap(exists -> exists
                        ? Mono.error(new IllegalArgumentException("Group code already exists: " + g.getCode()))
                        : groupRepository.save(g)
                                .doOnSuccess(group -> log.info("Created group: {} ({})", group.getName(), group.getCode())));
    }

    /**
     * Updates an existing group.
     * 
     * @param groupId the ID of the group to update
     * @param req     the request containing updated group details
     * @return the updated group
     */
    public Mono<Group> updateGroup(String groupId, GroupUpdateRequest req) {
        return groupRepository.findById(groupId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Group not found: " + groupId)))
                .flatMap(g -> {
                    g.setName(req.getName().trim());
                    g.setDescription(req.getDescription());
                    return groupRepository.save(g)
                            .doOnSuccess(group -> log.info("Updated group: {}", group.getCode()));
                });
    }

    /**
     * Deletes a group by its ID.
     * Prevents deletion if the group has any users or APIs associated with it.
     * 
     * @param groupId the ID of the group to delete
     * @return a Mono signaling completion
     */
    public Mono<Void> deleteGroup(String groupId) {
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
}
