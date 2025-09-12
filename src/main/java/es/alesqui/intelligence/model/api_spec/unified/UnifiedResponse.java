package es.alesqui.intelligence.model.api_spec.unified;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Represents a possible response from an API endpoint.
 * Defines the structure and content of HTTP responses for different status codes.
 * 
 * Each endpoint can have multiple responses for different scenarios:
 * successful responses, client errors, server errors, etc.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UnifiedResponse {
    
    /**
     * Description of this response.
     * Should explain when this response is returned and what it means.
     * Example: "Successfully retrieved user data" for a 200 response
     * Example: "User not found" for a 404 response
     */
    private String description;
    
    /**
     * Map of content types to their media type definitions.
     * Key: Media type (e.g., "application/json", "application/xml", "text/plain")
     * Value: UnifiedMediaType containing schema and examples for that content type
     * 
     * Allows the same response to be available in different formats.
     * The client can request a specific format using the Accept header.
     */
    private Map<String, UnifiedMediaType> content;
    
    /**
     * Map of headers that will be included in this response.
     * Key: Header name (e.g., "X-Rate-Limit-Remaining", "Location")
     * Value: Header description or example value
     * 
     * Documents response-specific headers beyond standard HTTP headers.
     * Useful for pagination headers, rate limiting info, etc.
     */
    private Map<String, String> headers;
}
