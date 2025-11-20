package es.alesqui.intelligence.dto.admin;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * Request payload for assigning one or more groups to a target API.
 * Used by endpoint: POST /api/admin/apis/{apiId}/groups
 * Behavior:
 * - Existing links are skipped silently (idempotent add semantics).
 * - Missing groups trigger an error for that id (service layer validation).
 * Example JSON: {"groupIds":["66fab9...","66fac0...","66fac1..."]}
 */
@Data
public class AssignGroupsToApiRequest {
    /**
     * List of group document identifiers to assign the API to.
     */
    @NotEmpty(message = "Group IDs list cannot be empty")
    private List<String> groupIds;
}
