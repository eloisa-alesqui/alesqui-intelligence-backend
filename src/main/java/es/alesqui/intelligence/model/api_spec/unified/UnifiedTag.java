package es.alesqui.intelligence.model.api_spec.unified;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a tag used for grouping API endpoints.
 * A tag consists of a name and an optional description.
 * This model is unified from OpenAPI's Tag Object.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UnifiedTag {

    /**
     * REQUIRED. The name of the tag.
     */
    private String name;

    /**
     * A description for the tag.
     * CommonMark syntax MAY be used for rich text representation.
     */
    private String description;
}