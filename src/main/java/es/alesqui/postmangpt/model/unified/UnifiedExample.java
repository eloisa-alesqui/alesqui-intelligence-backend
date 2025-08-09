package es.alesqui.postmangpt.model.unified;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents an example for API documentation and testing.
 * Examples can be used to show sample requests, responses, or parameter values.
 * 
 * Examples are crucial for API documentation as they provide concrete
 * illustrations of how to use the API correctly.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UnifiedExample {
    
    /**
     * Unique name for this example.
     * Should be descriptive and help identify the example's purpose.
     * Example: "successful-response", "validation-error", "empty-list"
     */
    private String name;
    
    /**
     * Short summary of what this example demonstrates.
     * One-line description of the example's purpose.
     * Example: "Successful user creation response"
     */
    private String summary;
    
    /**
     * Detailed description of the example.
     * Can explain the context, prerequisites, or special conditions.
     * May include markdown formatting.
     */
    private String description;
    
    /**
     * The actual example value.
     * Can be any type: string, number, object, array, etc.
     * For JSON examples, this would typically be a Map or List.
     */
    private Object value;
    
    /**
     * URL pointing to an external example.
     * Used when the example is too large to embed or is maintained elsewhere.
     * The URL should return the example value when accessed.
     */
    private String externalValue;
}
