package es.alesqui.intelligence.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Support ticket summary shown to non-admin users in the Dashboard support widget.
 * Null in {@link DashboardSummaryResponse} when no support section should be rendered.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportInfo {

    /** Number of support tickets opened by the user that are still unresolved. */
    private long openTickets;
}
