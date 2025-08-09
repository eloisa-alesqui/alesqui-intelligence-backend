package es.alesqui.postmangpt.dto.chat.request;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for query classification operations. Used to determine if a query
 * requires API interaction or can be answered directly.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClassificationRequest {

	/**
	 * User query to be classified.
	 */
	@NotBlank(message = "Query cannot be empty")
	@Size(max = 1000, message = "Query too long")
	private String query;

	/**
	 * Unique identifier for the conversation session.
	 */
	@NotBlank(message = "Conversation ID is required")
	private String conversationId;

	/**
	 * Previous conversation context for better classification accuracy. Helps the
	 * classifier understand the conversation flow.
	 */
	@Size(max = 2000, message = "Previous context too long")
	private String previousContext;

	/**
	 * Confidence threshold for classification decisions. Classifications below this
	 * threshold are considered uncertain.
	 */
	private double confidenceThreshold = 0.7;

	/**
	 * Creates a basic classification request.
	 *
	 * @param query          user's question to classify
	 * @param conversationId unique conversation identifier
	 * @return new ClassificationRequest instance
	 */
	public static ClassificationRequest of(String query, String conversationId) {
		return new ClassificationRequest(query, conversationId, null, 0.7);
	}

	/**
	 * Creates a classification request with conversation context.
	 *
	 * @param query           user's question to classify
	 * @param conversationId  unique conversation identifier
	 * @param previousContext prior conversation history
	 * @return new ClassificationRequest instance
	 */
	public static ClassificationRequest withContext(String query, String conversationId, String previousContext) {
		return new ClassificationRequest(query, conversationId, previousContext, 0.7);
	}

	/**
	 * Checks if this request includes previous conversation context.
	 *
	 * @return true if previous context is available
	 */
	public boolean hasPreviousContext() {
		return previousContext != null && !previousContext.trim().isEmpty();
	}

	/**
	 * Validates if the confidence threshold is within acceptable range.
	 *
	 * @return true if threshold is between 0.0 and 1.0
	 */
	public boolean hasValidThreshold() {
		return confidenceThreshold >= 0.0 && confidenceThreshold <= 1.0;
	}
}