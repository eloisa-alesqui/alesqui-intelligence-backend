package es.alesqui.intelligence.model.unified;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Represents content for a specific media type in requests or responses.
 * Defines the schema and examples for a particular content type like
 * application/json, application/xml, multipart/form-data, etc.
 * 
 * This model is used within request bodies and responses to specify
 * how data should be formatted for different content types.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UnifiedMediaType {
    
    /**
     * The schema defining the structure of the content.
     * Describes the data model including types, constraints, and nested structures.
     * For JSON content, this would define the JSON schema.
     */
    private UnifiedSchema schema;
    
    /**
     * A single example of the content.
     * Quick reference example showing typical usage.
     * For multiple examples, use the examples map instead.
     */
    private Object example;
    
    /**
     * Map of multiple named examples.
     * Allows providing various examples for different scenarios.
     * Key: Example name (e.g., "minimal", "complete", "error-case")
     * Value: UnifiedExample object with the example details
     */
    private Map<String, UnifiedExample> examples;
}
