package es.alesqui.intelligence.dto.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request payload for creating a new access Group.
 * Used by the administrative endpoint:
 * POST /api/admin/groups
 * Validation rules:
 * - {@code code} and {@code name} are required (non-blank)
 * - {@code code} must be unique across groups (validated in service layer)
 * Example JSON:
 * {"code":"sales","name":"Sales","description":"Business unit"}
 */
@Data
public class GroupCreateRequest {
    /**
     * Stable unique short code (slug) identifying the group.
     * Recommended to be lowercase, no spaces.
     */
    @NotBlank
    private String code;

    /**
     * Human-friendly display name for the group.
     */
    @NotBlank
    private String name;

    /**
     * Optional free-text description of the group's purpose.
     */
    private String description;
}
