package es.alesqui.intelligence.dto.chat.request;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

/**
 * Request DTO for chat operations containing user query and processing
 * preferences. Encapsulates all necessary information to initiate a chat
 * conversation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

	/**
	 * User's natural language query or question.
	 */
	@NotBlank(message = "Query cannot be empty")
	@Size(max = 2000, message = "Query too long")
	private String query;

	/**
	 * Unique identifier for the conversation session.
	 */
	private String conversationId;

	/**
	 * Maximum number of ReAct iterations allowed before termination. Prevents
	 * infinite loops in the reasoning process.
	 */
	@Min(value = 1, message = "Max iterations must be at least 1")
	@Max(value = 20, message = "Max iterations cannot exceed 20")
	private int maxIterations = 5;

	/**
	 * Timeout in seconds for the entire chat processing. Zero or negative values
	 * indicate no timeout.
	 */
	@Min(value = 0, message = "Timeout cannot be negative")
	private int timeoutSeconds = 120;

	/**
	 * Preferred language for the response. Uses ISO 639-1 language codes (e.g.,
	 * "en", "es", "fr").
	 */
	@Size(max = 5, message = "Language code too long")
	private String language = "en";

	/**
	 * Flag to include detailed reasoning steps in the response. When true, returns
	 * the complete ReAct thought process.
	 */
	private boolean includeReasoning = true;

	/**
	 * Checks if this request has a timeout configured.
	 *
	 * @return true if timeout is greater than zero
	 */
	public boolean hasTimeout() {
		return timeoutSeconds > 0;
	}

	/**
	 * Validates if the request parameters are within acceptable ranges.
	 *
	 * @return true if all parameters are valid
	 */
	public boolean isValid() {
		return query != null && !query.trim().isEmpty() && conversationId != null && !conversationId.trim().isEmpty()
				&& maxIterations >= 1 && maxIterations <= 20 && timeoutSeconds >= 0;
	}
}