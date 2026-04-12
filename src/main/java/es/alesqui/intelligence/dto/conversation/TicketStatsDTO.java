package es.alesqui.intelligence.dto.conversation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object representing the count of tickets grouped by status
 * for the last 30 days.
 *
 * Used by the IT Dashboard to display a quick overview of ticket distribution
 * without exposing individual ticket details or user identifiers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketStatsDTO {

    /** Number of tickets flagged by users for review. */
    private long reportedByUser;

    /** Number of tickets that resulted from a processing error. */
    private long errorProcessing;

    /** Number of tickets currently being investigated by the IT team. */
    private long underReview;

    /** Number of tickets that have been closed/resolved. */
    private long resolved;
}
