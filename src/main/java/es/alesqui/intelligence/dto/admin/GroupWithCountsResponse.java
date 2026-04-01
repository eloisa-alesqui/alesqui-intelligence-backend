package es.alesqui.intelligence.dto.admin;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Group information with user and API counts for user detail responses.
 *
 * Purpose:
 * - Embedded inside user detail responses to show which groups a user belongs to.
 * - Includes counts of other users and APIs in each group.
 *
 * Example JSON fragment:
 * {
 *   "id": "group-1",
 *   "code": "DEV",
 *   "name": "Development Team",
 *   "description": "Dev team access",
 *   "createdAt": "2024-01-01T00:00:00Z",
 *   "userCount": 5,
 *   "apiCount": 10
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupWithCountsResponse {
    
    /** Unique identifier of the group. */
    private String id;

    /** Stable slug-like code used internally. */
    private String code;

    /** Display name of the group. */
    private String name;

    /** Optional description of the group's purpose. */
    private String description;

    /** Creation timestamp. */
    private Instant createdAt;

    /** Number of users in this group. */
    private long userCount;

    /** Number of APIs linked to this group. */
    private long apiCount;
}
