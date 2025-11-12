package es.alesqui.intelligence.dto.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request payload for updating basic attributes of an existing access Group.
 * Used by the administrative endpoint:
 * PATCH /api/admin/groups/{groupId}
 * Notes:
 * - {@code name} is required; {@code description} and {@code active} are optional.
 * - If {@code active} is provided, the group's active status will be updated.
 * - Other structural changes (members/APIs) are managed via dedicated endpoints.
 * Example JSON: {"name":"Sales EU","description":"Region EU","active":true}
 */
@Data
public class GroupUpdateRequest {
    /**
     * New display name for the group.
     */
    @NotBlank
    private String name;

    /**
     * Optional new description.
     */
    private String description;
}
