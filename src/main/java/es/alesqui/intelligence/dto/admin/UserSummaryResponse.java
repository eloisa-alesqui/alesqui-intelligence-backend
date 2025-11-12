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
 *
 * Example JSON fragment:
 * {"id":"u42","username":"alice","roles":["ROLE_IT","ROLE_SUPERADMIN"]}
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
}
