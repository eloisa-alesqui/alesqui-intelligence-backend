package es.alesqui.intelligence.dto.admin;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * Request payload for assigning one or more users to a target Group.
 * Used by endpoint: POST /api/admin/groups/{groupId}/users
 * Behavior:
 * - Existing memberships are skipped silently (idempotent add semantics).
 * - Missing users trigger an error for that id (service layer validation).
 * Example JSON: {"userIds":["66fab9...","66fac0..."]}
 */
@Data
public class AssignUsersRequest {
    /**
     * List of user document identifiers to add as members of the group.
     */
    @NotEmpty
    private List<String> userIds;
}
