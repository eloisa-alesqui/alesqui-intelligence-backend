package es.alesqui.intelligence.model.access;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

/**
 * Links a user to a group, representing membership in MongoDB.
 * A user may belong to zero, one or many groups.
 * Only memberships whose group is active should count for visibility.
 */
@Data
@Document("group_memberships")
@CompoundIndexes({
        @CompoundIndex(name = "uniq_user_group", def = "{ 'userId': 1, 'groupId': 1 }", unique = true)
})
public class GroupMembership {

    /**
     * Unique identifier for the membership document.
     */
    @Id
    private String id;

    /**
     * The ID of the user who is a member of the group.
     */
    private String userId;

    /**
     * The ID of the group the user belongs to.
     */
    private String groupId;

    /**
     * Creation timestamp for auditing purposes.
     */
    private Instant createdAt = Instant.now();

}
