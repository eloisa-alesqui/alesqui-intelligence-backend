package es.alesqui.intelligence.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

/**
 * Data Transfer Object (DTO) representing detailed information about an API
 * endpoint.
 * 
 * This class encapsulates comprehensive metadata about an API endpoint
 * extracted from a Postman collection, including URL structure, HTTP method,
 * parameters, headers, and example data.
 * 
 * <p>
 * This DTO serves as a bridge between the raw Postman collection data and the
 * application's endpoint matching and execution logic. It provides all
 * necessary information to construct and execute HTTP requests.
 * 
 * <p>
 * Example usage:
 * 
 * <pre>
 * EndpointInfo endpoint = EndpointInfo.builder().name("Get User by ID").method("GET")
 * 		.url("https://api.example.com/users/{{userId}}")
 * 		.description("Retrieves a specific user by their unique identifier").pathParameters(List.of("userId"))
 * 		.build();
 * </pre>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EndpointInfo {

	/**
	 * The human-readable name of the API endpoint.
	 * 
	 * This field contains the descriptive name assigned to the endpoint in the
	 * Postman collection, typically describing the endpoint's purpose or
	 * functionality in business terms.
	 * 
	 * <p>
	 * Examples:
	 * <ul>
	 * <li>"Get All Users"</li>
	 * <li>"Create New Product"</li>
	 * <li>"Update Customer Profile"</li>
	 * <li>"Delete Order by ID"</li>
	 * </ul>
	 * 
	 * @apiNote This name is often used for display purposes and endpoint selection
	 */
	private String name;

	/**
	 * The HTTP method for this endpoint.
	 * 
	 * Specifies the HTTP verb that should be used when making requests to this
	 * endpoint. This determines the type of operation being performed.
	 * 
	 * <p>
	 * Standard HTTP methods:
	 * <ul>
	 * <li>GET - Retrieve data</li>
	 * <li>POST - Create new resources</li>
	 * <li>PUT - Update/replace entire resources</li>
	 * <li>PATCH - Partial updates</li>
	 * <li>DELETE - Remove resources</li>
	 * <li>HEAD - Get headers only</li>
	 * <li>OPTIONS - Get allowed methods</li>
	 * </ul>
	 * 
	 * @apiNote Should always be in uppercase format
	 */
	private String method;

	/**
	 * The complete URL for the API endpoint.
	 * 
	 * This field contains the full URL including protocol, host, path, and any
	 * template variables (typically in Postman's {{variable}} format). Environment
	 * variables and path parameters are preserved as-is.
	 * 
	 * <p>
	 * URL formats:
	 * <ul>
	 * <li>Complete: "https://api.example.com/v1/users"</li>
	 * <li>With variables: "{{baseUrl}}/users/{{userId}}"</li>
	 * <li>With query params: "https://api.example.com/users?limit={{limit}}"</li>
	 * </ul>
	 * 
	 * @apiNote Variables in {{}} format need to be resolved before making actual
	 *          requests
	 */
	private String url;

	/**
	 * Detailed description of the endpoint's functionality.
	 * 
	 * This field provides comprehensive information about what the endpoint does,
	 * its expected behavior, and any important usage notes. Often sourced from the
	 * Postman collection's documentation.
	 * 
	 * <p>
	 * Description content may include:
	 * <ul>
	 * <li>Functional overview</li>
	 * <li>Business logic explanation</li>
	 * <li>Usage examples</li>
	 * <li>Important constraints or limitations</li>
	 * <li>Authentication requirements</li>
	 * </ul>
	 * 
	 * @apiNote This field is optional but highly valuable for endpoint matching
	 *          accuracy
	 */
	private String description;

	/**
	 * List of path parameters required by this endpoint.
	 * 
	 * Contains the names of all path variables that need to be substituted in the
	 * URL before making a request. These are typically enclosed in curly braces or
	 * double curly braces in the URL template.
	 * 
	 * <p>
	 * Examples:
	 * <ul>
	 * <li>URL: "/users/{id}" → pathParameters: ["id"]</li>
	 * <li>URL: "/orders/{orderId}/items/{itemId}" → pathParameters: ["orderId",
	 * "itemId"]</li>
	 * <li>URL: "{{baseUrl}}/users/{{userId}}" → pathParameters: ["userId"]</li>
	 * </ul>
	 * 
	 * @apiNote Parameter names should match exactly with the variable names in the
	 *          URL
	 */
	private List<String> pathParameters;

	/**
	 * Query parameters supported by this endpoint with their metadata.
	 * 
	 * This map contains query parameter names as keys and their descriptions/types
	 * as values. These parameters can be appended to the request URL and are
	 * typically optional, used for filtering, pagination, or configuration.
	 * 
	 * <p>
	 * Parameter format: "paramName" → "type - description"
	 * <ul>
	 * <li>"page" → "integer - Page number for pagination (default: 1)"</li>
	 * <li>"limit" → "integer - Number of items per page (max: 100)"</li>
	 * <li>"status" → "string - Filter by status (active|inactive|pending)"</li>
	 * <li>"search" → "string - Search term for filtering results"</li>
	 * <li>"sort" → "string - Field name to sort by"</li>
	 * <li>"order" → "string - Sort order (asc|desc)"</li>
	 * </ul>
	 *
	 * @apiNote Values can contain type, description, defaults, and validation rules
	 */
	private Map<String, String> queryParameters;

	/**
	 * HTTP headers required or supported by this endpoint.
	 * 
	 * This map contains header names as keys and their expected values or
	 * descriptions as values. Includes both required headers (like authentication)
	 * and optional headers (like content negotiation).
	 * 
	 * <p>
	 * Common headers:
	 * <ul>
	 * <li>Authentication: "Authorization" → "Bearer {{token}}"</li>
	 * <li>Content Type: "Content-Type" → "application/json"</li>
	 * <li>Accept: "Accept" → "application/json"</li>
	 * <li>Custom: "X-API-Version" → "v1"</li>
	 * </ul>
	 * 
	 * @apiNote Values may contain template variables that need resolution
	 */
	private Map<String, String> headers;

	/**
	 * Example request body for this endpoint.
	 * 
	 * Contains a sample JSON or other format request body that demonstrates the
	 * expected structure and data types for POST, PUT, or PATCH requests. This is
	 * particularly useful for endpoints that accept complex data structures.
	 * 
	 * <p>
	 * Example formats:
	 * <ul>
	 * <li>JSON: {"name": "John", "email": "john@example.com"}</li>
	 * <li>Form data: "name=John&email=john@example.com"</li>
	 * <li>XML: "&lt;user&gt;&lt;name&gt;John&lt;/name&gt;&lt;/user&gt;"</li>
	 * </ul>
	 * 
	 * @apiNote This field is typically null for GET and DELETE requests
	 */
	private String requestBodyExample;

	/**
	 * Example response body from this endpoint.
	 * 
	 * Contains a sample response that shows the expected structure and data types
	 * returned by the endpoint. Useful for understanding the API contract and for
	 * testing purposes.
	 * 
	 * <p>
	 * Response examples help with:
	 * <ul>
	 * <li>Understanding response structure</li>
	 * <li>Data type identification</li>
	 * <li>Error response formats</li>
	 * <li>Success response patterns</li>
	 * </ul>
	 * 
	 * @apiNote May contain multiple examples for different response scenarios
	 */
	private String responseExample;

	/**
	 * Relevance score for query matching.
	 * 
	 * This numeric value indicates how well this endpoint matches a given natural
	 * language query. Calculated by the matching algorithm based on various factors
	 * like semantic similarity, parameter compatibility, and method matching.
	 * 
	 * <p>
	 * Scoring factors:
	 * <ul>
	 * <li>HTTP method compatibility (exact match gets higher score)</li>
	 * <li>Semantic similarity of descriptions</li>
	 * <li>Parameter name and type matching</li>
	 * <li>URL path similarity</li>
	 * </ul>
	 * 
	 * @apiNote Higher scores indicate better matches; typically ranges from 0.0 to
	 *          1.0
	 */
	private Double matchScore;

	/**
	 * Tags or categories associated with this endpoint.
	 * 
	 * Contains classification labels that help organize and filter endpoints. These
	 * tags can be used for grouping related endpoints or for improving search and
	 * matching capabilities.
	 * 
	 * <p>
	 * Example tags:
	 * <ul>
	 * <li>Functional: "user-management", "authentication", "reporting"</li>
	 * <li>Technical: "public-api", "admin-only", "deprecated"</li>
	 * <li>Business: "customer-facing", "internal", "partner-api"</li>
	 * </ul>
	 * 
	 * @apiNote Tags can be used to improve query matching accuracy
	 */
	private List<String> tags;
}