package es.alesqui.intelligence.model.api_spec.unified;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Represents a server configuration where the API is hosted.
 * This model unifies server definitions from both OpenAPI/Swagger and Postman formats.
 * 
 * A server provides a URL template for accessing the API, which may include variables
 * that can be substituted with actual values at runtime.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiServer {
    
    /**
     * The URL template for the server.
     * May contain variables in curly braces that can be substituted.
     * Example: "https://{environment}.api.example.com/{version}"
     */
    private String url;
    
    /**
     * Optional description of the server.
     * Useful for distinguishing between production, staging, development servers, etc.
     * Example: "Production server for North America region"
     */
    private String description;
    
    /**
     * Map of variable names to their definitions.
     * These variables can be used in the URL template.
     * Key: variable name (e.g., "environment", "version")
     * Value: ServerVariable object containing possible values and default
     */
    private Map<String, ServerVariable> variables;
}
