package es.alesqui.intelligence.dto.chat.response;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;

/**
 * Response DTO for error conditions during chat processing. Provides detailed
 * error information and context for debugging.
 */
@Data
@Builder
public class ErrorResponse {

	/**
	 * Primary error message describing what went wrong.
	 */
	private String message;

	/**
	 * Specific error code for programmatic handling.
	 */
	private String errorCode;

	/**
	 * Detailed error description for debugging purposes.
	 */
	private String details;

	/**
	 * Unique identifier for the conversation session.
	 */
	private String conversationId;

	/**
	 * Timestamp when the error occurred.
	 */
	@Builder.Default
	private Instant timestamp = Instant.now();

	/**
	 * Stack trace or additional debugging information.
	 */
	@Builder.Default
	private List<String> stackTrace = new ArrayList<>();

	/**
	 * Context information about what was being processed when error occurred.
	 */
	private String processingContext;

	/**
	 * Suggested actions for resolving the error.
	 */
	@Builder.Default
	private List<String> suggestions = new ArrayList<>();

	/**
	 * Flag indicating if this is a recoverable error.
	 */
	@Builder.Default
	private boolean recoverable = false;

	/**
	 * Creates a general error response.
	 *
	 * @param message        error description
	 * @param conversationId unique conversation identifier
	 * @return ErrorResponse for general error
	 */
	public static ErrorResponse general(String message, String conversationId) {
		return ErrorResponse.builder().message(message).errorCode("GENERAL_ERROR").conversationId(conversationId)
				.recoverable(true).build();
	}

	/**
	 * Creates an error response for validation failures.
	 *
	 * @param message        validation error description
	 * @param details        specific validation details
	 * @param conversationId unique conversation identifier
	 * @return ErrorResponse for validation error
	 */
	public static ErrorResponse validation(String message, String details, String conversationId) {
		return ErrorResponse.builder().message(message).errorCode("VALIDATION_ERROR").details(details)
				.conversationId(conversationId).recoverable(true)
				.suggestions(List.of("Check input parameters", "Verify request format")).build();
	}

	/**
	 * Creates an error response for API call failures.
	 *
	 * @param message        API error description
	 * @param apiName        name of the failed API
	 * @param conversationId unique conversation identifier
	 * @return ErrorResponse for API error
	 */
	public static ErrorResponse apiCall(String message, String apiName, String conversationId) {
		return ErrorResponse.builder().message(message).errorCode("API_CALL_ERROR")
				.details("Failed to call API: " + apiName).conversationId(conversationId)
				.processingContext("API interaction").recoverable(true)
				.suggestions(List.of("Check API availability", "Verify API credentials", "Try again later")).build();
	}

	/**
	 * Creates an error response for timeout conditions.
	 *
	 * @param operation      description of the timed-out operation
	 * @param conversationId unique conversation identifier
	 * @return ErrorResponse for timeout error
	 */
	public static ErrorResponse timeout(String operation, String conversationId) {
		return ErrorResponse.builder().message("Operation timed out: " + operation).errorCode("TIMEOUT_ERROR")
				.conversationId(conversationId).processingContext(operation).recoverable(true)
				.suggestions(List.of("Try with simpler query", "Increase timeout limit")).build();
	}

	/**
	 * Adds a suggestion for error resolution.
	 *
	 * @param suggestion recommended action
	 * @return this response for method chaining
	 */
	public ErrorResponse addSuggestion(String suggestion) {
		this.suggestions.add(suggestion);
		return this;
	}

	/**
	 * Sets the processing context where error occurred.
	 *
	 * @param context description of processing context
	 * @return this response for method chaining
	 */
	public ErrorResponse withContext(String context) {
		this.processingContext = context;
		return this;
	}

	/**
	 * Checks if this error can be recovered from.
	 *
	 * @return true if error is recoverable
	 */
	public boolean canRecover() {
		return recoverable;
	}

	/**
	 * Checks if suggestions are available for this error.
	 *
	 * @return true if suggestions list is not empty
	 */
	public boolean hasSuggestions() {
		return suggestions != null && !suggestions.isEmpty();
	}
}