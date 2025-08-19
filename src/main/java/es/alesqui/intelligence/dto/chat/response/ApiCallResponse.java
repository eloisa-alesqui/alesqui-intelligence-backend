package es.alesqui.intelligence.dto.chat.response;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.Map;
import java.util.HashMap;

/**
 * Response DTO for API call operations within the ReAct process. Contains the
 * API response data and execution metadata.
 */
@Data
@Builder
public class ApiCallResponse {

	/**
	 * Flag indicating if the API call was successful.
	 */
	@Builder.Default
	private boolean success = false;

	/**
	 * Response data returned by the API call. Can be JSON object, string, or other
	 * data types.
	 */
	private Object responseData;

	/**
	 * Error message if the API call failed. Null or empty if the call was
	 * successful.
	 */
	private String errorMessage;

	/**
	 * HTTP status code returned by the API.
	 */
	private int statusCode;

	/**
	 * Response headers returned by the API.
	 */
	@Builder.Default
	private Map<String, String> responseHeaders = new HashMap<>();

	/**
	 * Time taken to execute the API call in milliseconds.
	 */
	private Long executionTimeMs;

	/**
	 * Timestamp when the API call was made.
	 */
	@Builder.Default
	private Instant timestamp = Instant.now();

	/**
	 * Name of the API that was called.
	 */
	private String apiName;

	/**
	 * Endpoint path that was accessed.
	 */
	private String endpoint;

	/**
	 * HTTP method used for the call.
	 */
	private String httpMethod;

	/**
	 * Unique identifier for the conversation session.
	 */
	private String conversationId;

	/**
	 * Creates a successful API call response.
	 *
	 * @param responseData   data returned by the API
	 * @param statusCode     HTTP status code
	 * @param conversationId unique conversation identifier
	 * @return ApiCallResponse for successful call
	 */
	public static ApiCallResponse success(Object responseData, int statusCode, String conversationId) {
		return ApiCallResponse.builder().success(true).responseData(responseData).statusCode(statusCode)
				.conversationId(conversationId).timestamp(Instant.now()).build();
	}

	/**
	 * Creates a failed API call response.
	 *
	 * @param errorMessage   description of the error
	 * @param statusCode     HTTP status code
	 * @param conversationId unique conversation identifier
	 * @return ApiCallResponse for failed call
	 */
	public static ApiCallResponse failure(String errorMessage, int statusCode, String conversationId) {
		return ApiCallResponse.builder().success(false).errorMessage(errorMessage).statusCode(statusCode)
				.conversationId(conversationId).timestamp(Instant.now()).build();
	}
	
	/**
	 * Creates a failed API call response.
	 *
	 * @param errorMessage   description of the error
	 * @param statusCode     HTTP status code
	 * @return ApiCallResponse for failed call
	 */
	public static ApiCallResponse failure(String errorMessage, int statusCode) {
		return ApiCallResponse.builder().success(false).errorMessage(errorMessage).statusCode(statusCode)
				.timestamp(Instant.now()).build();
	}

	/**
	 * Creates an API call response for connection timeout.
	 *
	 * @param conversationId unique conversation identifier
	 * @return ApiCallResponse for timeout error
	 */
	public static ApiCallResponse timeout(String conversationId) {
		return ApiCallResponse.builder().success(false).errorMessage("API call timed out").statusCode(408)
				.conversationId(conversationId).timestamp(Instant.now()).build();
	}

	/**
	 * Sets the API call details.
	 *
	 * @param apiName    name of the API
	 * @param endpoint   endpoint path
	 * @param httpMethod HTTP method used
	 * @return this response for method chaining
	 */
	public ApiCallResponse withApiDetails(String apiName, String endpoint, String httpMethod) {
		this.apiName = apiName;
		this.endpoint = endpoint;
		this.httpMethod = httpMethod;
		return this;
	}
	
	/**
	 * Sets the API call details.
	 *
	 * @param apiName    name of the API
	 * @param endpoint   endpoint path
	 * @return this response for method chaining
	 */
	public ApiCallResponse withApiDetails(String apiName, String endpoint) {
		this.apiName = apiName;
		this.endpoint = endpoint;
		return this;
	}

	/**
	 * Sets the execution time for the API call.
	 *
	 * @param timeMs execution time in milliseconds
	 * @return this response for method chaining
	 */
	public ApiCallResponse withExecutionTime(long timeMs) {
		this.executionTimeMs = timeMs;
		return this;
	}

	/**
	 * Adds a response header to the collection.
	 *
	 * @param key   header name
	 * @param value header value
	 * @return this response for method chaining
	 */
	public ApiCallResponse addResponseHeader(String key, String value) {
		this.responseHeaders.put(key, value);
		return this;
	}

	/**
	 * Checks if the API call was successful based on status code.
	 *
	 * @return true if status code indicates success (200-299)
	 */
	public boolean isHttpSuccess() {
		return statusCode >= 200 && statusCode < 300;
	}

	/**
	 * Gets the response data as a string.
	 *
	 * @return string representation of response data
	 */
	public String getResponseAsString() {
		return responseData != null ? responseData.toString() : "";
	}

	/**
	 * Checks if the response contains data.
	 *
	 * @return true if response data is not null
	 */
	public boolean hasResponseData() {
		return responseData != null;
	}
	
	/**
	 * Sets the raw response body for debugging purposes.
	 *
	 * @param rawResponse raw response body as string
	 * @return this response for method chaining
	 */
	public ApiCallResponse withRawResponse(String rawResponse) {
	  // Puedes agregar un campo rawResponse si quieres almacenarlo
	  // o simplemente usar responseData para almacenar la respuesta raw
	  if (rawResponse != null && !rawResponse.trim().isEmpty()) {
	      // Si ya hay responseData, crear un mapa que incluya ambos
	      if (this.responseData != null) {
	          Map<String, Object> combinedData = new HashMap<>();
	          combinedData.put("parsedData", this.responseData);
	          combinedData.put("rawResponse", rawResponse);
	          this.responseData = combinedData;
	      } else {
	          // Si no hay responseData, usar la respuesta raw
	          this.responseData = rawResponse;
	      }
	  }
	  return this;
	}

	/**
	 * Creates a failed API call response with timeout.
	 *
	 * @param conversationId unique conversation identifier
	 * @param timeoutSeconds timeout duration in seconds
	 * @return ApiCallResponse for timeout error
	 */
	public static ApiCallResponse timeoutError(String conversationId, int timeoutSeconds) {
	  return ApiCallResponse.builder()
	          .success(false)
	          .errorMessage("API call timed out after " + timeoutSeconds + " seconds")
	          .statusCode(408)
	          .conversationId(conversationId)
	          .timestamp(Instant.now())
	          .build();
	}

	/**
	 * Creates a failed API call response for connection errors.
	 *
	 * @param errorMessage   description of the connection error
	 * @param conversationId unique conversation identifier
	 * @return ApiCallResponse for connection error
	 */
	public static ApiCallResponse connectionError(String errorMessage, String conversationId) {
	  return ApiCallResponse.builder()
	          .success(false)
	          .errorMessage("Connection error: " + errorMessage)
	          .statusCode(503)
	          .conversationId(conversationId)
	          .timestamp(Instant.now())
	          .build();
	}
}