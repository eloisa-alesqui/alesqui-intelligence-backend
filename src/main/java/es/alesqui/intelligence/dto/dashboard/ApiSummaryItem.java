package es.alesqui.intelligence.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight representation of an API visible to the user in the Dashboard API panel.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiSummaryItem {

    /** Unique identifier of the API. */
    private String id;

    /** Human-readable API name. */
    private String name;

    /** Short description of the API purpose. */
    private String description;

    /** Whether this API is currently active and available for use. */
    private boolean active;
}
