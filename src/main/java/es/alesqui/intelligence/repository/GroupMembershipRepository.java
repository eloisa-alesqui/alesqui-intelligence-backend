package es.alesqui.intelligence.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

import es.alesqui.intelligence.model.access.GroupMembership;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive Mongo repository for {@link GroupMembership} documents.
 * Links users and groups, enabling group-based access control.
 */
@Repository
public interface GroupMembershipRepository extends ReactiveMongoRepository<GroupMembership, String> {

    /**
     * Lists all group memberships for a specific user.
     * @param userId The user's id.
     * @return Flux of memberships.
     */
    Flux<GroupMembership> findByUserId(String userId);

    /**
     * Lists all group memberships for a specific group.
     * @param groupId The group's id.
     * @return Flux of memberships.
     */
    Flux<GroupMembership> findByGroupId(String groupId);
    
    /**
     * Lists all group memberships for any of the specified groups.
     * @param groupIds List of group IDs.
     * @return Flux of memberships.
     */
    Flux<GroupMembership> findByGroupIdIn(List<String> groupIds);

    /**
     * Checks whether a membership exists for a given user and group pair.
     * @param userId The user's id.
     * @param groupId The group's id.
     * @return Mono true if exists, false otherwise.
     */
    Mono<Boolean> existsByUserIdAndGroupId(String userId, String groupId);
}
