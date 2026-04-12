package es.alesqui.intelligence.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Trial period details for users on a time-limited trial plan.
 * Null in {@link DashboardSummaryResponse} when the user is not on a trial.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrialInfo {

    /** Number of calendar days remaining before the trial expires. */
    private int daysRemaining;

    /** UTC timestamp when the trial period ends. */
    private Instant trialEndDate;

    /** True when the trial end date has already passed. */
    private boolean isExpired;
}
