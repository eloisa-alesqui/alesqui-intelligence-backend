package es.alesqui.postmangpt.dto.chat.response;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

/**
 * Response DTO for query classification operations. Contains the classification
 * result and confidence metrics.
 */
@Data
@Builder
public class ClassificationResponse {

	/**
	 * Flag indicating if the query requires API data access. True means ReAct
	 * processing should be used.
	 */
	private boolean isDataQuery;

	/**
	 * Explanation of why the query was classified this way. Provides transparency
	 * in the classification decision.
	 */
	private String reasoning;

	/**
	 * Confidence score of the classification (0.0 to 1.0). Higher values indicate
	 * more certain classifications.
	 */
	private double confidence;

	/**
	 * Unique identifier for the conversation session.
	 */
	private String conversationId;

	/**
	 * Timestamp when the classification was performed.
	 */
	@Builder.Default
	private Instant timestamp = Instant.now();

	/**
	 * Processing time for the classification in milliseconds.
	 */
	private Long classificationTimeMs;

	/**
	 * Creates a classification response indicating data query.
	 *
	 * @param reasoning      explanation for the classification
	 * @param confidence     classification confidence score
	 * @param conversationId unique conversation identifier
	 * @return ClassificationResponse for data query
	 */
	public static ClassificationResponse dataQuery(String reasoning, double confidence, String conversationId) {
		return ClassificationResponse.builder().isDataQuery(true).reasoning(reasoning).confidence(confidence)
				.conversationId(conversationId).timestamp(Instant.now()).build();
	}

	/**
	 * Creates a classification response indicating direct answer.
	 *
	 * @param reasoning      explanation for the classification
	 * @param confidence     classification confidence score
	 * @param conversationId unique conversation identifier
	 * @return ClassificationResponse for direct answer
	 */
	public static ClassificationResponse directAnswer(String reasoning, double confidence, String conversationId) {
		return ClassificationResponse.builder().isDataQuery(false).reasoning(reasoning).confidence(confidence)
				.conversationId(conversationId).timestamp(Instant.now()).build();
	}

	/**
	 * Creates an uncertain classification response.
	 *
	 * @param reasoning      explanation for the uncertainty
	 * @param conversationId unique conversation identifier
	 * @return ClassificationResponse with low confidence
	 */
	public static ClassificationResponse uncertain(String reasoning, String conversationId) {
		return ClassificationResponse.builder().isDataQuery(true) // Default to ReAct when uncertain
				.reasoning("Uncertain classification: " + reasoning).confidence(0.5).conversationId(conversationId)
				.timestamp(Instant.now()).build();
	}

	/**
	 * Sets the classification processing time.
	 *
	 * @param timeMs processing time in milliseconds
	 * @return this response for method chaining
	 */
	public ClassificationResponse withProcessingTime(long timeMs) {
		this.classificationTimeMs = timeMs;
		return this;
	}

	/**
	 * Checks if this classification recommends ReAct processing.
	 *
	 * @return true if ReAct should be used
	 */
	public boolean shouldUseReAct() {
		return isDataQuery;
	}
}