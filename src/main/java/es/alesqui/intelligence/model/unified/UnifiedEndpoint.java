package es.alesqui.intelligence.model.unified;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * Represents a single API endpoint/operation in the unified model.
 * Combines endpoint information from both OpenAPI/Swagger and Postman formats.
 * 
 * An endpoint represents a specific operation that can be performed on a path,
 * such as GET /users/{id} or POST /users.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UnifiedEndpoint {
    
    /**
     * Unique identifier for this endpoint.
     * Could be the operationId from OpenAPI or a generated ID.
     */
    private String id;
    
    /**
     * Human-readable name for the endpoint.
     * In Postman: the request name
     * In OpenAPI: could be the summary or operationId
     */
    private String name;
    
    /**
     * Short summary of what the endpoint does.
     * Typically a one-line description.
     * Example: "Get user by ID"
     */
    private String summary;
    
    /**
     * Detailed description of the endpoint's functionality.
     * May include markdown formatting for rich text.
     * Should explain what the endpoint does, when to use it, etc.
     */
    private String description;
    
    /**
     * The URL path template for this endpoint.
     * May include path parameters in curly braces.
     * Example: "/users/{userId}/orders/{orderId}"
     */
    private String path;
    
    /**
     * HTTP method for this endpoint.
     * Values: "GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS", "TRACE"
     */
    private String method;
    
    /**
     * Unique operation identifier from OpenAPI specification.
     * Used for code generation and reference purposes.
     * Example: "getUserById", "createOrder"
     */
    private String operationId;
    
    /**
     * List of tags for grouping and categorization.
     * Tags help organize endpoints into logical groups.
     * Example: ["users", "admin"] for admin user operations
     */
    private List<String> tags;
    
    /**
     * List of parameters for this endpoint.
     * Includes path, query, header, and cookie parameters.
     * Does not include the request body (see requestBody field).
     */
    private List<UnifiedParameter> parameters;
    
    /**
     * The request body definition for this endpoint.
     * Only applicable for methods that support request bodies (POST, PUT, PATCH).
     * Contains content types and their schemas.
     */
    private UnifiedRequestBody requestBody;
    
    /**
     * Map of possible responses from this endpoint.
     * Key: HTTP status code (e.g., "200", "404", "default")
     * Value: Response definition including content types and schemas
     */
    private Map<String, UnifiedResponse> responses;
    
    /**
     * Additional headers specific to this endpoint.
     * These are headers that should be sent with the request.
     * Key: Header name
     * Value: Header value or description
     */
    private Map<String, String> headers;
    
    /**
     * Authentication configuration specific to this endpoint.
     * If null, the global authentication configuration applies.
     * Allows endpoints to override or specify different auth requirements.
     */
    private UnifiedAuthentication authentication;
    
    /**
     * List of examples showing how to use this endpoint.
     * Examples can include request/response pairs with actual data.
     * Useful for documentation and testing purposes.
     */
    private List<UnifiedExample> examples;
    
    /**
     * Indicates whether this endpoint is deprecated.
     * Deprecated endpoints should be avoided in new implementations.
     * Documentation should indicate migration paths for deprecated endpoints.
     */
    private boolean deprecated;
}
