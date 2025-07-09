package es.alesqui.postmangpt.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Represents an API request ready for execution against a company's internal APIs.
 * 
 * This class encapsulates all the necessary information to execute an HTTP request
 * based on a user's natural language query. It contains the endpoint details, HTTP method,
 * parameters, headers, authentication, and request body needed to make the actual API call.
 * 
 * Usage Example:
 * APIRequest request = APIRequest.builder()
 *     .endpoint("/users/search")
 *     .method("GET")
 *     .parameters(Map.of("email", "john@example.com"))
 *     .authToken("Bearer abc123...")
 *     .build();
 * 
 * Flow Context:
 * 1. User asks: "Show me the user with email john@example.com"
 * 2. AI Service converts this to an APIRequest object
 * 3. API Execution Engine uses this object to make the actual HTTP call
 * 4. Results are returned to the user in a formatted response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class APIRequest {
  
  /**
   * The API endpoint path to call (relative to the base URL).
   * 
   * This should be the path portion of the URL, typically starting with a forward slash.
   * The base URL is configured separately in the collection settings.
   * 
   * Examples:
   * - "/users" - Get all users
   * - "/users/search" - Search for specific users
   * - "/orders/{orderId}" - Get specific order (path parameters handled separately)
   * - "/api/v1/employees" - Versioned API endpoint
   * 
   * @see #parameters for query parameters
   * @see #pathVariables for URL path variables
   */
  private String endpoint;
  
  /**
   * HTTP method for the request.
   * 
   * Standard HTTP methods supported by most REST APIs. The method determines
   * the type of operation being performed on the resource.
   * 
   * Common Values:
   * - "GET" - Retrieve data (most common for PostmanGPT queries)
   * - "POST" - Create new resources
   * - "PUT" - Update existing resources
   * - "DELETE" - Remove resources
   * - "PATCH" - Partial updates
   * 
   * Note: PostmanGPT primarily focuses on GET requests for data retrieval,
   * but supports all methods based on the imported Postman collection.
   */
  private String method;
  
  /**
   * Query parameters to append to the URL.
   * 
   * These are key-value pairs that get appended to the URL after a question mark (?).
   * Multiple parameters are separated by ampersands (&).
   * 
   * Example: If endpoint is "/users/search" and parameters
   * contain {"email": "john@example.com", "status": "active"}, the final URL becomes:
   * "/users/search?email=john@example.com&status=active"
   * 
   * Common Use Cases:
   * - Search filters: {"name": "John", "department": "Engineering"}
   * - Pagination: {"page": "1", "limit": "20"}
   * - Sorting: {"sort": "created_date", "order": "desc"}
   * - Date ranges: {"start_date": "2024-01-01", "end_date": "2024-12-31"}
   * 
   * @see #pathVariables for URL path substitutions
   */
  private Map<String, String> parameters;
  
  /**
   * HTTP headers to include with the request.
   * 
   * Headers provide additional metadata about the request or specify how the
   * request should be processed by the server.
   * 
   * Common Headers:
   * - "Content-Type": "application/json" - For JSON request bodies
   * - "Accept": "application/json" - Expected response format
   * - "User-Agent": "PostmanGPT/1.0" - Client identification
   * - "X-API-Version": "v1" - API version specification
   * - "X-Request-ID": "uuid" - Request tracking
   * 
   * Note: Authentication headers are handled separately via authToken.
   * This map is for additional headers required by specific endpoints.
   * 
   * @see #authToken for authentication headers
   */
  private Map<String, String> headers;
  
  /**
   * Request body for POST, PUT, and PATCH requests.
   * 
   * The request body contains the data to be sent to the server. This is typically
   * used for creating or updating resources. The body format depends on the API's
   * requirements and the Content-Type header.
   * 
   * Common Body Types:
   * - JSON Object: {"name": "John", "email": "john@example.com"}
   * - JSON Array: [{"id": 1}, {"id": 2}]
   * - String: Raw text or XML content
   * - Map: Key-value pairs for form data
   * 
   * Usage Examples:
   * // Creating a new user
   * body = Map.of(
   *     "name", "John Doe",
   *     "email", "john@company.com",
   *     "department", "Engineering"
   * );
   * 
   * // Updating user preferences
   * body = "{\"theme\": \"dark\", \"notifications\": true}";
   * 
   * Note: This field is typically null for GET and DELETE requests.
   */
  private Object body;
  
  /**
   * Authentication token for API access.
   * 
   * This field contains the authentication credentials needed to access the API.
   * The format depends on the authentication method configured in the original
   * Postman collection.
   * 
   * Common Authentication Formats:
   * - Bearer Token: "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
   * - API Key: "api-key-12345"
   * - Basic Auth: "Basic dXNlcm5hbWU6cGFzc3dvcmQ="
   * - Custom Header: "X-API-KEY: secret-key-value"
   * 
   * Security Note: This token is extracted from the imported Postman
   * collection's authentication configuration. All authentication remains within the
   * company's secure environment.
   * 
   * Usage: The API Execution Engine will add this as an Authorization
   * header or use it according to the authentication method specified in the collection.
   * 
   * @see #headers for additional custom headers
   */
  private String authToken;
  
  /**
   * Path variables for dynamic URL segments.
   * 
   * Path variables are placeholders in the URL path that get replaced with actual values.
   * They are commonly used in RESTful APIs to specify resource identifiers.
   * 
   * Example: If the endpoint is "/users/{userId}/orders/{orderId}"
   * and pathVariables contains {"userId": "123", "orderId": "456"}, the final
   * URL becomes "/users/123/orders/456"
   * 
   * Common Use Cases:
   * - Resource IDs: {"userId": "12345"}
   * - Nested resources: {"companyId": "abc", "departmentId": "xyz"}
   * - Dynamic paths: {"version": "v2", "format": "json"}
   * 
   * Processing: The API Execution Engine will replace all
   * {variableName} placeholders in the endpoint with corresponding values
   * from this map before making the HTTP request.
   * 
   * @see #endpoint for the URL template
   * @see #parameters for query parameters
   */
  private Map<String, String> pathVariables;
  
  /**
   * The collection ID this request belongs to.
   * 
   * This identifier links the request back to the specific Postman collection
   * that was imported into PostmanGPT. It's used for:
   * 
   * - Context: Ensuring the request is executed against the correct API
   * - Configuration: Retrieving collection-specific settings (base URL, auth)
   * - Logging: Tracking which collection generated the request
   * - Validation: Ensuring the endpoint exists in the specified collection
   * 
   * Example Value: "user-management-api-v1" or "hr-employee-api"
   * 
   * Usage: Set by the AI Service when converting natural language
   * queries to API requests, based on the user's selected collection.
   */
  private String collectionId;
  
  /**
   * Optional timeout for the request in milliseconds.
   * 
   * Specifies how long to wait for the API response before timing out.
   * If not specified, a default timeout from the collection or system configuration is used.
   * 
   * Default Values:
   * - If null: Use collection or system default (typically 30 seconds)
   * - Minimum recommended: 5000ms (5 seconds)
   * - Maximum recommended: 300000ms (5 minutes)
   * 
   * Use Cases:
   * - Long-running reports: Higher timeout values
   * - Real-time queries: Lower timeout values
   * - External API calls: Account for network latency
   */
  private Long timeoutMs;
  
  /**
   * Creates a simple APIRequest with just endpoint and method.
   * 
   * Convenience method for creating basic requests. Other fields can be
   * set using the builder pattern or setter methods.
   * 
   * @param endpoint The API endpoint path
   * @param method HTTP method (GET, POST, etc.)
   * @return A new APIRequest instance
   * 
   * @see #builder() for more complex request construction
   */
  public static APIRequest of(String endpoint, String method) {
      return APIRequest.builder()
              .endpoint(endpoint)
              .method(method)
              .build();
  }
  
  /**
   * Creates a GET request with query parameters.
   * 
   * Convenience method for the most common PostmanGPT use case:
   * executing GET requests with search parameters.
   * 
   * @param endpoint The API endpoint path
   * @param parameters Query parameters for the request
   * @return A new APIRequest configured for GET with parameters
   * 
   * @see #of(String, String) for simple requests
   * @see #builder() for full customization
   */
  public static APIRequest get(String endpoint, Map<String, String> parameters) {
      return APIRequest.builder()
              .endpoint(endpoint)
              .method("GET")
              .parameters(parameters)
              .build();
  }
}