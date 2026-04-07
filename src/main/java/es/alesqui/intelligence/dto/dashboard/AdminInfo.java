package es.alesqui.intelligence.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Platform-wide statistics shown exclusively in the admin section of the Dashboard.
 * Null in {@link DashboardSummaryResponse} when the authenticated user is not an admin.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminInfo {

    /** Total number of registered users in the platform. */
    private long totalUsers;

    /** Total number of unified API documents registered in the platform. */
    private long totalApis;

    /** Total number of access groups defined in the platform. */
    private long totalGroups;

    /** Number of support tickets currently in an open state. */
    private long openTickets;
}
