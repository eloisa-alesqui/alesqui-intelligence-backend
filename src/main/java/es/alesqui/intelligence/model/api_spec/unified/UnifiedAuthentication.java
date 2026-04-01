package es.alesqui.intelligence.model.api_spec.unified;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Represents authentication/authorization configuration for API access.
 * This unified model supports various authentication schemes from both
 * OpenAPI/Swagger and Postman specifications.
 * 
 * Supports multiple authentication types including:
 * - HTTP authentication (basic, bearer)
 * - API Keys (in header, query, or cookie)
 * - OAuth 2.0 flows
 * - OpenID Connect
 * - Custom schemes
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UnifiedAuthentication {
    
    /**
     * The type of authentication.
     * Common values: "basic", "bearer", "apiKey", "oauth2", "openIdConnect"
     * For HTTP auth, this would be "http"
     */
    private String type;
    
    /**
     * The name of the HTTP Authorization scheme to be used.
     * Only applies when type is "http".
     * Example values: "basic", "bearer", "digest"
     */
    private String scheme;
    
    /**
     * Format of the bearer token.
     * Only applies when type is "http" and scheme is "bearer".
     * Example: "JWT" for JSON Web Tokens
     */
    private String bearerFormat;
    
    /**
     * The name of the API key or header/query parameter.
     * For apiKey type: name of the parameter
     * For other types: might contain the header name
     */
    private String name;
    
    /**
     * Location of the API key or auth parameter.
     * Valid values: "header", "query", "cookie"
     * Only applies to apiKey type
     */
    private String in;
    
    /**
     * OAuth 2.0 flows configuration.
     * Contains flow types (implicit, password, clientCredentials, authorizationCode)
     * and their respective configurations (authorizationUrl, tokenUrl, scopes, etc.)
     */
    private Map<String, Object> flows;
    
    /**
     * Additional attributes for authentication configuration.
     * Used to store auth-specific parameters that don't fit in other fields.
     * For Postman: might include username/password for basic auth
     * For OAuth: might include additional parameters
     */
    private Map<String, String> attributes;
}
