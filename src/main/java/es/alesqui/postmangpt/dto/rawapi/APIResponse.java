package es.alesqui.postmangpt.dto.rawapi;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Raw API response from executed request containing all response details.
 * 
 * This class encapsulates the complete HTTP response received from an API call,
 * including status information, headers, response body, and execution metadata.
 * It serves as the bridge between the raw HTTP response and the formatted
 * user-friendly response that PostmanGPT presents to users.
 * 
 * Usage Example:
 * APIResponse response = APIResponse.builder()
 *     .statusCode(200)
 *     .data(userList)
 *     .executionTime(245L)
 *     .timestamp(LocalDateTime.now())
 *     .build();
 * 
 * Flow Context:
 * 1. API Execution Engine makes HTTP request using APIRequest
 * 2. Server responds with HTTP response
 * 3. Response is captured in this APIResponse object
 * 4. AI Service formats this into user-friendly FormattedResponse
 * 5. User receives the formatted result
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class APIResponse {
  
  /**
   * HTTP status code returned by the API.
   * 
   * Standard HTTP status codes indicating the result of the API request.
   * This is crucial for determining if the request was successful and
   * how to handle the response data.
   * 
   * Common Status Codes:
   * - 200: OK - Request successful, data returned
   * - 201: Created - Resource successfully created
   * - 204: No Content - Request successful, no data to return
   * - 400: Bad Request - Invalid request parameters
   * - 401: Unauthorized - Authentication required or failed
   * - 403: Forbidden - Access denied to resource
   * - 404: Not Found - Requested resource doesn't exist
   * - 500: Internal Server Error - Server-side error occurred
   * - 502: Bad Gateway - Upstream server error
   * - 503: Service Unavailable - Server temporarily unavailable
   * 
   * Usage: The AI Service uses this to determine how to format
   * the response for the user (success message vs error explanation).
   */
  private int statusCode;
  
  /**
   * HTTP response headers returned by the API.
   * 
   * Headers provide additional metadata about the response, including
   * content type, caching information, rate limiting, and custom headers
   * specific to the API.
   * 
   * Common Response Headers:
   * - "Content-Type": "application/json" - Response format
   * - "Content-Length": "1234" - Size of response body
   * - "Cache-Control": "no-cache" - Caching instructions
   * - "X-Rate-Limit-Remaining": "95" - API rate limit info
   * - "X-Request-ID": "abc-123" - Request tracking ID
   * - "Last-Modified": "Wed, 21 Oct 2015 07:28:00 GMT" - Resource modification time
   * - "ETag": "\"12345\"" - Resource version identifier
   * 
   * Usage Examples:
   * - Rate limiting: Check X-Rate-Limit headers
   * - Caching: Use ETag and Last-Modified for optimization
   * - Debugging: Use X-Request-ID for tracing issues
   * - Pagination: Look for Link headers with next/prev URLs
   * 
   * Note: Header names are case-insensitive according to HTTP specification,
   * but this map preserves the original casing from the server.
   */
  private Map<String, String> headers;
  
  /**
   * Parsed response body containing the actual data.
   * 
   * This field contains the response body parsed into appropriate Java objects.
   * The type depends on the Content-Type header and the API's response format.
   * This is the primary data that users are interested in.
   * 
   * Common Data Types:
   * - Map<String, Object>: Single JSON object response
   * - List<Map<String, Object>>: Array of JSON objects
   * - String: Plain text, XML, or unparsed content
   * - List<String>: Array of simple values
   * - Custom POJOs: Specific domain objects if deserialized
   * 
   * Response Examples:
   * // Single user object
   * data = Map.of(
   *     "id", 123,
   *     "name", "John Doe",
   *     "email", "john@company.com"
   * );
   * 
   * // List of users
   * data = List.of(
   *     Map.of("id", 123, "name", "John"),
   *     Map.of("id", 124, "name", "Jane")
   * );
   * 
   * // Error response
   * data = Map.of(
   *     "error", "User not found",
   *     "code", "USER_NOT_FOUND",
   *     "details", "No user exists with ID 999"
   * );
   * 
   * Processing: The AI Service analyzes this data to generate
   * human-readable summaries and detailed responses for users.
   * 
   * @see #rawResponse for the original unparsed response
   */
  private Object data;
  
  /**
   * Raw response text as received from the server.
   * 
   * This field contains the original, unparsed response body exactly
   * as received from the API server. It's useful for debugging,
   * logging, and cases where the parsed data might lose information.
   * 
   * Content Examples:
   * // JSON response
   * rawResponse = "{\"users\":[{\"id\":123,\"name\":\"John\"}]}";
   * 
   * // XML response
   * rawResponse = "<?xml version=\"1.0\"?><users><user id=\"123\">John</user></users>";
   * 
   * // Plain text response
   * rawResponse = "Operation completed successfully";
   * 
   * // Error response
   * rawResponse = "{\"error\":\"Invalid API key\",\"code\":401}";
   * 
   * Use Cases:
   * - Debugging: Compare raw vs parsed data
   * - Logging: Store complete response for audit trails
   * - Error Analysis: Examine malformed responses
   * - Custom Parsing: Handle special response formats
   * - Size Calculation: Measure actual response size
   * 
   * Note: For large responses, this field might consume significant memory.
   * Consider truncating or omitting for very large payloads in production.
   * 
   * @see #data for the parsed version of this content
   */
  private String rawResponse;
  
  /**
   * Request execution time in milliseconds.
   * 
   * Total time taken from sending the HTTP request to receiving
   * the complete response. This includes network latency, server
   * processing time, and data transfer time.
   * 
   * Time Ranges:
   * - Fast APIs: 10-100ms (cached data, simple queries)
   * - Normal APIs: 100-1000ms (database queries, business logic)
   * - Slow APIs: 1000-5000ms (complex reports, external calls)
   * - Very Slow: 5000ms+ (heavy processing, large datasets)
   * 
   * Usage Examples:
   * // Performance monitoring
   * if (response.getExecutionTime() > 3000) {
   *     log.warn("Slow API response: {}ms for {}", 
   *              response.getExecutionTime(), endpoint);
   * }
   * 
   * // User feedback
   * String message = response.getExecutionTime() > 2000 
   *     ? "Query completed (took " + response.getExecutionTime() + "ms)"
   *     : "Query completed quickly";
   * 
   * Performance Context:
   * - Network latency: Usually 10-100ms within same datacenter
   * - Database queries: 1-500ms depending on complexity
   * - External API calls: 100-2000ms depending on service
   * - File operations: 10-1000ms depending on size
   * 
   * Note: This measures total round-trip time, not just server processing.
   */
  private long executionTime;
  
  /**
   * Timestamp when the response was received.
   * 
   * Records the exact moment when the API response was received and
   * this APIResponse object was created. Useful for logging, caching,
   * and temporal analysis of API calls.
   * 
   * Usage Examples:
   * - Audit logging: Track when each API call was made
   * - Cache validation: Determine if cached data is still fresh
   * - Performance analysis: Correlate response times with time of day
   * - Rate limiting: Track request frequency over time
   * - Debugging: Timeline reconstruction for issue investigation
   * 
   * Time Zone: Uses system default timezone. For distributed systems,
   * consider using UTC timestamps for consistency.
   */
  private LocalDateTime timestamp;
  
  /**
   * Indicates if the response represents a successful operation.
   * 
   * Convenience field that determines success based on HTTP status code.
   * Generally, status codes 200-299 are considered successful.
   * 
   * Success Criteria:
   * - 200-299: Successful responses
   * - 300-399: Redirection (may or may not be considered success)
   * - 400-499: Client errors (not successful)
   * - 500-599: Server errors (not successful)
   * 
   * Usage: Allows quick checking without parsing status codes:
   * if (response.isSuccessful()) {
   *     processData(response.getData());
   * } else {
   *     handleError(response);
   * }
   */
  private boolean successful;
  
  /**
   * Error message extracted from the response if applicable.
   * 
   * When the API returns an error (4xx or 5xx status codes), this field
   * contains a human-readable error message extracted from the response body.
   * This helps the AI Service provide meaningful error explanations to users.
   * 
   * Error Message Sources:
   * - JSON: response.error, response.message, response.detail
   * - XML: <error>, <message>, <description> elements
   * - Plain text: Direct error message
   * - HTTP: Standard status text as fallback
   * 
   * Examples:
   * - "User not found with ID 12345"
   * - "Invalid API key provided"
   * - "Rate limit exceeded. Try again in 60 seconds"
   * - "Internal server error occurred"
   * - "Required field 'email' is missing"
   * 
   * Usage: The AI Service uses this to provide user-friendly error explanations
   * instead of technical HTTP status codes.
   */
  private String errorMessage;
  
  /**
   * Size of the response body in bytes.
   * 
   * Total size of the response data, useful for monitoring data transfer,
   * performance analysis, and billing/quota management.
   * 
   * Size Categories:
   * - Small: 0-1KB (simple responses, errors)
   * - Medium: 1KB-100KB (typical API responses)
   * - Large: 100KB-1MB (detailed reports, large lists)
   * - Very Large: 1MB+ (bulk data exports, file downloads)
   * 
   * Usage Examples:
   * - Performance monitoring: Large responses may indicate inefficient queries
   * - Caching decisions: Small responses are good candidates for caching
   * - User feedback: Warn users about large data transfers
   * - Quota tracking: Monitor API usage by data volume
   */
  private Long responseSizeBytes;
  
  /**
   * Creates a successful APIResponse with basic information.
   * 
   * Convenience method for creating responses from successful API calls.
   * Sets the timestamp automatically and marks as successful.
   * 
   * @param statusCode HTTP status code (should be 2xx for success)
   * @param data Response data from the API
   * @return A new APIResponse instance marked as successful
   * 
   * @see #error(int, String) for error responses
   * @see #builder() for full customization
   */
  public static APIResponse success(int statusCode, Object data) {
      return APIResponse.builder()
              .statusCode(statusCode)
              .data(data)
              .successful(statusCode >= 200 && statusCode < 300)
              .timestamp(LocalDateTime.now())
              .build();
  }
  
  /**
   * Creates an error APIResponse with status code and error message.
   * 
   * Convenience method for creating responses from failed API calls.
   * Sets the timestamp automatically and marks as unsuccessful.
   * 
   * @param statusCode HTTP error status code (4xx or 5xx)
   * @param errorMessage Human-readable error description
   * @return A new APIResponse instance marked as unsuccessful
   * 
   * @see #success(int, Object) for successful responses
   * @see #builder() for full customization
   */
  public static APIResponse error(int statusCode, String errorMessage) {
      return APIResponse.builder()
              .statusCode(statusCode)
              .errorMessage(errorMessage)
              .successful(false)
              .timestamp(LocalDateTime.now())
              .build();
  }
  
  /**
   * Checks if the response indicates a client error (4xx status codes).
   * 
   * Client errors typically indicate problems with the request itself,
   * such as invalid parameters, authentication issues, or malformed data.
   * 
   * @return true if status code is in the 400-499 range
   */
  public boolean isClientError() {
      return statusCode >= 400 && statusCode < 500;
  }
  
  /**
   * Checks if the response indicates a server error (5xx status codes).
   * 
   * Server errors indicate problems on the API server side, such as
   * internal errors, service unavailability, or upstream failures.
   * 
   * @return true if status code is in the 500-599 range
   */
  public boolean isServerError() {
      return statusCode >= 500 && statusCode < 600;
  }
  
  /**
   * Gets a header value by name (case-insensitive lookup).
   * 
   * Convenience method for retrieving specific header values without
   * worrying about case sensitivity issues.
   * 
   * @param headerName Name of the header to retrieve
   * @return Header value if found, null otherwise
   */
  public String getHeader(String headerName) {
      if (headers == null || headerName == null) {
          return null;
      }
      
      return headers.entrySet().stream()
              .filter(entry -> entry.getKey().equalsIgnoreCase(headerName))
              .map(Map.Entry::getValue)
              .findFirst()
              .orElse(null);
  }
}
