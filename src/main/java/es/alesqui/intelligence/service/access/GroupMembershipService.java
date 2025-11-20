package es.alesqui.intelligence.service.access;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import es.alesqui.intelligence.dto.admin.AssignGroupsRequest;
import es.alesqui.intelligence.dto.admin.AssignUsersRequest;
import es.alesqui.intelligence.model.access.GroupMembership;
import es.alesqui.intelligence.repository.GroupMembershipRepository;
import es.alesqui.intelligence.repository.GroupRepository;
import es.alesqui.intelligence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service responsible for managing group memberships.
 * Handles the many-to-many relationship between users and groups.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GroupMembershipService {

    private final GroupMembershipRepository membershipRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;

    /**
     * Assigns users to a group.
     * 
     * @param groupId the ID of the group
     * @param req     the request containing user IDs to assign
     * @return a Flux of assigned group memberships
     */
    public Flux<GroupMembership> assignUsers(String groupId, AssignUsersRequest req) {
        Mono<Boolean> groupExists = groupRepository.existsById(groupId)
                .filter(exists -> exists)
                .switchIfEmpty(Mono.error(new IllegalStateException("Group not found: " + groupId)));

        return groupExists.flatMapMany(exists -> Flux.fromIterable(req.getUserIds())
                .flatMap(userId -> userRepository.findById(userId)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("User not found: " + userId)))
                        .then(membershipRepository.existsByUserIdAndGroupId(userId, groupId)
                                .flatMap(membershipExists -> membershipExists ? Mono.empty()
                                        : membershipRepository.save(buildMembership(userId, groupId))))));
    }

    /**
     * Removes a user from a group by deleting the GroupMembership.
     * 
     * @param groupId the ID of the group
     * @param userId  the ID of the user to remove
     * @return a Mono signaling completion
     */
    public Mono<Void> removeUserFromGroup(String groupId, String userId) {
        return groupRepository.existsById(groupId)
                .filter(exists -> exists)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Group not found: " + groupId)))
                .then(userRepository.existsById(userId)
                        .filter(exists -> exists)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("User not found: " + userId))))
                .then(membershipRepository.existsByUserIdAndGroupId(userId, groupId)
                        .flatMap(exists -> {
                            if (!exists) {
                                return Mono.error(new IllegalArgumentException(
                                        "User " + userId + " is not a member of group " + groupId));
                            }
                            return membershipRepository.findByUserId(userId)
                                    .filter(membership -> membership.getGroupId().equals(groupId))
                                    .next()
                                    .flatMap(membership -> membershipRepository.deleteById(membership.getId()));
                        }));
    }

    /**
     * Assigns a user to multiple groups at once.
     * 
     * @param userId the ID of the user
     * @param req    the request containing group IDs to assign the user to
     * @return a Flux of assigned group memberships
     */
    public Flux<GroupMembership> assignGroupsToUser(String userId, AssignGroupsRequest req) {
        Mono<Boolean> userExists = userRepository.existsById(userId)
                .filter(exists -> exists)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("User not found: " + userId)));

        return userExists.flatMapMany(exists -> Flux.fromIterable(req.getGroupIds())
                .flatMap(groupId -> groupRepository.findById(groupId)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("Group not found: " + groupId)))
                        .then(membershipRepository.existsByUserIdAndGroupId(userId, groupId)
                                .flatMap(membershipExists -> membershipExists ? Mono.empty()
                                        : membershipRepository.save(buildMembership(userId, groupId))))));
    }

    /**
     * Removes a group from a user by deleting the GroupMembership.
     * This is a convenience method with reversed parameter order for user-centric
     * endpoints.
     * 
     * @param userId  the ID of the user
     * @param groupId the ID of the group to remove the user from
     * @return a Mono signaling completion
     */
    public Mono<Void> removeGroupFromUser(String userId, String groupId) {
        return removeUserFromGroup(groupId, userId);
    }

    /**
     * Retrieves all group IDs for a given user.
     * * This method is used for permission and visibility filtering,
     * allowing other services to determine which groups a user belongs to
     * without directly accessing the GroupMembershipRepository.
     *
     * @param userId the ID of the user
     * @return a Flux emitting the group IDs the user belongs to
     */
    public Flux<String> getGroupIdsByUserId(String userId) {
        if (userId == null || userId.isEmpty()) {
            return Flux.empty();
        }
        return membershipRepository.findByUserId(userId)
                .map(GroupMembership::getGroupId);
    }

    /**
     * Retrieves all user IDs for users who belong to any of the specified groups.
     * * This method is used for group-based filtering in other services,
     * such as showing tickets only from users in shared groups.
     *
     * @param groupIds a list of group IDs to search for members
     * @return a Flux emitting the user IDs of all members in those groups
     */
    public Flux<String> getUserIdsByGroupIds(List<String> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return Flux.empty();
        }
        return membershipRepository.findByGroupIdIn(groupIds)
                .map(GroupMembership::getUserId)
                .distinct(); // Remove duplicates (users in multiple groups)
    }

    /**
     * Counts the number of users in a group.
     * 
     * @param groupId the ID of the group
     * @return a Mono emitting the count
     */
    public Mono<Long> countUsersByGroupId(String groupId) {
        return membershipRepository.findByGroupId(groupId).count();
    }

    /**
     * Counts the number of groups a user belongs to.
     * 
     * @param userId the ID of the user
     * @return a Mono emitting the count
     */
    public Mono<Long> countGroupsByUserId(String userId) {
        return membershipRepository.findByUserId(userId).count();
    }

    /**
     * Deletes all memberships for a specific group.
     * Used when deleting a group or during cleanup operations.
     * 
     * @param groupId the ID of the group
     * @return a Mono signaling completion
     */
    public Mono<Void> deleteAllByGroupId(String groupId) {
        return membershipRepository.findByGroupId(groupId)
                .flatMap(membership -> membershipRepository.deleteById(membership.getId()))
                .then()
                .doOnSuccess(v -> log.debug("Deleted all memberships for group {}", groupId));
    }

    /**
     * Deletes all memberships for a specific user.
     * Used when deleting a user or during cleanup operations.
     * 
     * @param userId the ID of the user
     * @return a Mono signaling completion
     */
    public Mono<Void> deleteAllByUserId(String userId) {
        return membershipRepository.findByUserId(userId)
                .flatMap(membership -> membershipRepository.deleteById(membership.getId()))
                .then()
                .doOnSuccess(v -> log.debug("Deleted all memberships for user {}", userId));
    }

    /**
     * Builds a group membership entity.
     * 
     * @param userId  the ID of the user
     * @param groupId the ID of the group
     * @return the created group membership
     */
    private GroupMembership buildMembership(String userId, String groupId) {
        GroupMembership m = new GroupMembership();
        m.setUserId(userId);
        m.setGroupId(groupId);
        m.setCreatedAt(Instant.now());
        return m;
    }
}
