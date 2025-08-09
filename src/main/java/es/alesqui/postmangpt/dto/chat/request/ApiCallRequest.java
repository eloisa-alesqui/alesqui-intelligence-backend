package es.alesqui.postmangpt.dto.chat.request;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.HashMap;

/**
 * Request DTO for API call operations within the ReAct process. Contains all
 * necessary information to execute an API endpoint call.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiCallRequest {

	/**
	 * Name or identifier of the API to call.
	 */
	@NotBlank(message = "API name is required")
	private String apiName;

	/**
	 * Specific endpoint path within the API.
	 */
	@NotBlank(message = "Endpoint is required")
	private String endpoint;
	
	/**
     * The URL path template for this endpoint.
     */
	@NotBlank(message = "Path is required")
    private String path;

	/**
	 * HTTP method for the API call (GET, POST, PUT, DELETE, etc.).
	 */
	@NotBlank(message = "HTTP method is required")
	@Builder.Default
	private String httpMethod = "GET";

	/**
	 * Parameters to be sent with the API request. Can include query parameters,
	 * path variables, or request body data.
	 */
	@NotNull(message = "Parameters map cannot be null")
	@Builder.Default
	private Map<String, Object> parameters = new HashMap<>();

	/**
	 * HTTP headers to include in the API request. Contains authentication tokens,
	 * content types, etc.
	 */
	@NotNull(message = "Headers map cannot be null")
	@Builder.Default
	private Map<String, String> headers = new HashMap<>();

	/**
	 * Timeout in milliseconds for the API call. Zero or negative values use default
	 * timeout.
	 */
	@Builder.Default
	private int timeoutMs = 5000;

	/**
	 * Unique identifier for the conversation session making this call.
	 */
	@NotBlank(message = "Conversation ID is required")
	private String conversationId;

	/**
	 * Creates a simple GET request to an API endpoint.
	 *
	 * @param apiName        name of the API
	 * @param endpoint       endpoint path
	 * @param conversationId conversation identifier
	 * @return new ApiCallRequest for GET operation
	 */
	public static ApiCallRequest get(String apiName, String endpoint, String path, String conversationId) {
		return new ApiCallRequest(apiName, endpoint, path, "GET", new HashMap<>(), new HashMap<>(), 5000, conversationId);
	}

	/**
	 * Creates a POST request with parameters to an API endpoint.
	 *
	 * @param apiName        name of the API
	 * @param endpoint       endpoint path
	 * @param parameters     request parameters
	 * @param conversationId conversation identifier
	 * @return new ApiCallRequest for POST operation
	 */
	public static ApiCallRequest post(String apiName, String endpoint, String path, Map<String, Object> parameters,
			String conversationId) {
		return new ApiCallRequest(apiName, endpoint, path, "POST", parameters, new HashMap<>(), 5000, conversationId);
	}

	/**
	 * Adds a parameter to the request.
	 *
	 * @param key   parameter name
	 * @param value parameter value
	 * @return this request for method chaining
	 */
	public ApiCallRequest addParameter(String key, Object value) {
		this.parameters.put(key, value);
		return this;
	}

	/**
	 * Adds a header to the request.
	 *
	 * @param key   header name
	 * @param value header value
	 * @return this request for method chaining
	 */
	public ApiCallRequest addHeader(String key, String value) {
		this.headers.put(key, value);
		return this;
	}

	/**
	 * Checks if this request has any parameters.
	 *
	 * @return true if parameters map is not empty
	 */
	public boolean hasParameters() {
		return !parameters.isEmpty();
	}

	/**
	 * Checks if this request has custom headers.
	 *
	 * @return true if headers map is not empty
	 */
	public boolean hasHeaders() {
		return !headers.isEmpty();
	}

	/**
	 * Validates if the HTTP method is supported.
	 *
	 * @return true if method is GET, POST, PUT, DELETE, or PATCH
	 */
	public boolean hasValidHttpMethod() {
		return httpMethod != null && (httpMethod.equalsIgnoreCase("GET") 
				|| httpMethod.equalsIgnoreCase("POST")
				|| httpMethod.equalsIgnoreCase("PUT") || httpMethod.equalsIgnoreCase("DELETE")
				|| httpMethod.equalsIgnoreCase("PATCH"));
	}
}