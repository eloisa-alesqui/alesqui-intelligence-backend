package es.alesqui.intelligence.model.unified;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Represents the request body for an API endpoint.
 * Defines what data can be sent in the body of HTTP requests.
 * 
 * Request bodies are typically used with POST, PUT, and PATCH methods
 * to send data to the server for creating or updating resources.
 * 
 * @author alesqui
 * @since 1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UnifiedRequestBody {
    
    /**
     * Description of the request body's purpose and contents.
     * Should explain what data is expected and how it will be used.
     * May include markdown formatting for rich documentation.
     */
    private String description;
    
    /**
     * Indicates whether the request body is required.
     * If true, requests without a body will be rejected.
     * If false, the body is optional.
     */
    private boolean required;
    
    /**
     * Map of content types to their media type definitions.
     * Key: Media type (e.g., "application/json", "application/xml", "multipart/form-data")
     * Value: UnifiedMediaType containing schema and examples for that content type
     * 
     * Allows the same endpoint to accept different content formats.
     * The client specifies which format using the Content-Type header.
     */
    private Map<String, UnifiedMediaType> content;
}
