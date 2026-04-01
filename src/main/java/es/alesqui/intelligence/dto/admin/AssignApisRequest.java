package es.alesqui.intelligence.dto.admin;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * Request payload for assigning one or more Unified APIs to a target Group.
 * Used by endpoint: POST /api/admin/groups/{groupId}/apis
 * Behavior:
 * - Each {@code apiId} references a UnifiedApiDocument.
 * - Existing links are skipped silently (idempotent add semantics).
 * - Missing APIs trigger an error for that id (service layer validation).
 * Example JSON: {"apiIds":["671ab123e9...","671ab456e9..."]}
 */
@Data
public class AssignApisRequest {
    /**
     * List of Unified API document identifiers to link to the group.
     */
    @NotEmpty
    private List<String> apiIds;
}
