package es.alesqui.intelligence.dto.admin;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Detailed snapshot of a user for administrative inspection.
 *
 * Purpose:
 * - Backing model for GET /api/admin/users/{userId} combining user attributes and groups.
 * - Avoids extra round trips by including counts and arrays in a single payload.
 *
 * Invariants:
 * - groupCount equals groups.size.
 *
 * Example JSON:
 * {
 *   "id": "user-123",
 *   "username": "john.doe@example.com",
 *   "roles": ["USER", "ADMIN"],
 *   "createdAt": "2024-01-15T10:30:00Z",
 *   "groupCount": 2,
 *   "groups": [
 *     {
 *       "id": "group-1",
 *       "code": "DEV",
 *       "name": "Development Team",
 *       "description": "Dev team access",
 *       "createdAt": "2024-01-01T00:00:00Z",
 *       "userCount": 5,
 *       "apiCount": 10
 *     }
 *   ]
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDetailResponse {
    
    /** Unique identifier of the user. */
    private String id;

    /** User's email address (used as username). */
    private String username;

    /** Granted roles for the user, e.g. USER, ADMIN. */
    private List<String> roles;

    /** Creation timestamp. */
    private Instant createdAt;

    /** Number of groups the user belongs to (equals groups.size). */
    private long groupCount;

    /** Groups that the user belongs to, with additional metadata. */
    private List<GroupWithCountsResponse> groups;
}
