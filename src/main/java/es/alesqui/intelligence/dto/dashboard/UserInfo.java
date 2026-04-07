package es.alesqui.intelligence.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Basic profile information about the authenticated user shown in the Dashboard header.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfo {

    /** The user's login name. */
    private String username;

    /** List of role names assigned to the user (e.g. ROLE_ADMIN, ROLE_USER). */
    private List<String> roles;

    /** UTC timestamp of when the user account was created. */
    private Instant memberSince;
}
