package es.alesqui.intelligence.dto.admin;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Compact projection of a group for list views and dashboards.
 *
 * Purpose:
 * - Returned by GET /api/admin/groups.
 * - Provides counts so the UI can decide whether to fetch full detail lazily.
 *
 * Invariants:
 * - userCount >= 0
 * - apiCount >= 0
 *
 * Example JSON fragment:
 * {"id":"g1","code":"sales","name":"Sales","userCount":12,"apiCount":5}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupSummaryResponse {
    /** Unique identifier of the group. */
    private String id;
    /** Stable code for internal reference. */
    private String code;
    /** Human readable name. */
    private String name;
    /** Optional description. */
    private String description;
    /** Creation timestamp. */
    private Instant createdAt;

    /** Number of users assigned to the group. */
    private long userCount;
    /** Number of APIs linked to the group. */
    private long apiCount;
}
