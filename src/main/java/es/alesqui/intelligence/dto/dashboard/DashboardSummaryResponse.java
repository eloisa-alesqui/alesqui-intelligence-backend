package es.alesqui.intelligence.dto.dashboard;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Top-level response returned by the Dashboard summary endpoint.
 *
 * Role-specific sections ({@link TrialInfo}, {@link AdminInfo}, {@link SupportInfo}) are omitted
 * from the JSON output when null, keeping the payload minimal for each user profile.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DashboardSummaryResponse {

    /** Basic profile information about the authenticated user. */
    private UserInfo user;

    /** Trial period details; null when the user is not on a trial plan. */
    private TrialInfo trial;

    /** Aggregated activity metrics for the authenticated user. */
    private ActivityInfo activity;

    /** List of APIs accessible to the user. */
    private List<ApiSummaryItem> apis;

    /** Platform-wide admin statistics; null when the user is not an admin. */
    private AdminInfo admin;

    /** Support ticket summary for the user; null when the section is not applicable. */
    private SupportInfo support;
}
