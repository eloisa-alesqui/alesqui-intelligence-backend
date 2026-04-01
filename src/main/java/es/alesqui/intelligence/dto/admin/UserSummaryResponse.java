package es.alesqui.intelligence.dto.admin;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight projection of a user for administrative read operations.
 *
 * Purpose:
 * - Embedded inside group detail responses to avoid exposing full credential / security data.
 * - Provides only identification and role membership needed for access governance UI.
 *
 * Not included:
 * - Password hashes, authentication details, audit metadata.
 *
 * Typical usage flow:
 * 1. Fetch group detail -> receive a list of UserSummaryResponse.
 * 2. Render username and roles badges.
 * 3. Offer role edit action that calls PATCH /api/admin/users/{username}/roles using these values.
 *
 * Invariants:
 * - roles list is never null (empty when user has no roles).
 * - id and username are expected to be stable identifiers.
 * - isActive indicates whether the user has completed account activation.
 * - groupCount indicates the number of groups the user belongs to.
 *
 * Example JSON fragment:
 * {"id":"u42","username":"alice","roles":["ROLE_IT","ROLE_SUPERADMIN"],"active":true,"groupCount":3}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSummaryResponse {
    /** Unique identifier of the user record (Mongo document id). */
    private String id;

    /** Stable login name (principal). */
    private String username;

    /** Granted roles for the user, e.g. ROLE_IT, ROLE_BUSINESS, ROLE_SUPERADMIN. Empty list when none. */
    private List<String> roles;

    /** Whether the user account is active (true) or pending activation (false). */
    private boolean active;

    /** Number of groups this user belongs to. */
    private int groupCount;
}
