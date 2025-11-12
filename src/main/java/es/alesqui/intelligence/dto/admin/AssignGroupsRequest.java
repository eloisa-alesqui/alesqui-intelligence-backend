package es.alesqui.intelligence.dto.admin;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * Request payload for assigning one or more groups to a target User.
 * Used by endpoint: POST /api/admin/users/{userId}/groups
 * Behavior:
 * - Existing memberships are skipped silently (idempotent add semantics).
 * - Missing groups trigger an error for that id (service layer validation).
 * Example JSON: {"groupIds":["66fab9...","66fac0...","66fac1..."]}
 */
@Data
public class AssignGroupsRequest {
    /**
     * List of group document identifiers to assign the user to.
     */
    @NotEmpty(message = "Group IDs list cannot be empty")
    private List<String> groupIds;
}
