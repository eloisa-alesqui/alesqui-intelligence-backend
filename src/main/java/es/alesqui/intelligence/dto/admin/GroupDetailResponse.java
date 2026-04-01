package es.alesqui.intelligence.dto.admin;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Detailed snapshot of a group for administrative inspection.
 *
 * Purpose:
 * - Backing model for GET /api/admin/groups/{id} combining group attributes, users and APIs.
 * - Avoids extra round trips by including counts and arrays in a single payload.
 *
 * Invariants:
 * - userCount equals users.size.
 * - apiCount equals apis.size.
 *
 * Example JSON (abbreviated):
 * {"id":"g2","code":"core","name":"Core Services",
 *  "userCount":4,"apiCount":8,
 *  "apis":[{"id":"api-1","name":"Billing","isPublic":false}],
 *  "users":[{"id":"u2","username":"asmith","roles":["ROLE_BUSINESS"]}]}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupDetailResponse {
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

    /** Number of users in the group (equals users.size). */
    private long userCount;

    /** Number of APIs linked to the group (equals apis.size). */
    private long apiCount;

    /** APIs assigned to the group. */
    private List<ApiSummaryResponse> apis;

    /** Users that belong to the group. */
    private List<UserSummaryResponse> users;
}
