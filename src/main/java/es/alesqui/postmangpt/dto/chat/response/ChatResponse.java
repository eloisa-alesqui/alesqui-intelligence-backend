package es.alesqui.postmangpt.dto.chat.response;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Response DTO containing the final answer and metadata about the chat
 * processing. Encapsulates both direct responses and ReAct-processed results
 * with execution details.
 */
@Data
@Builder
public class ChatResponse {

	/**
	 * Final answer content provided to the user.
	 */
	private String content;

	/**
	 * Unique identifier for the conversation session.
	 */
	private String conversationId;

	/**
	 * List of reasoning steps taken during processing. Contains the complete
	 * thought process for ReAct responses.
	 */
	@Builder.Default
	private List<String> reasoning = new ArrayList<>();

	/**
	 * Number of ReAct iterations performed. Zero for direct responses that didn't
	 * require API interaction.
	 */
	@Builder.Default
	private int iterations = 0;

	/**
	 * Flag indicating if the processing completed successfully.
	 */
	@Builder.Default
	private boolean success = true;

	/**
	 * Timestamp when the response was generated.
	 */
	@Builder.Default
	private Instant timestamp = Instant.now();

	/**
	 * Additional metadata about the processing. Can include execution times, API
	 * calls made, confidence scores, etc.
	 */
	@Builder.Default
	private Map<String, Object> metadata = new HashMap<>();

	/**
	 * Total processing time in milliseconds.
	 */
	private Long processingTimeMs;

	/**
	 * Type of processing used (DIRECT, REACT, HYBRID).
	 */
	private String processingType;

	/**
	 * Confidence score of the final answer (0.0 to 1.0).
	 */
	private Double confidenceScore;

	/**
	 * Number of API calls made during processing.
	 */
	@Builder.Default
	private int apiCallsCount = 0;

	/**
	 * Creates a direct response without ReAct processing.
	 *
	 * @param content        answer content
	 * @param conversationId unique conversation identifier
	 * @return ChatResponse for direct answer
	 */
	public static ChatResponse direct(String content, String conversationId) {
		return ChatResponse.builder().content(content).conversationId(conversationId)
				.reasoning(List.of("Direct response - no API calls needed")).iterations(0).success(true)
				.timestamp(Instant.now()).processingType("DIRECT").apiCallsCount(0).build();
	}

	/**
	 * Creates a ReAct-processed response with reasoning details.
	 *
	 * @param content        final answer content
	 * @param conversationId unique conversation identifier
	 * @param reasoning      list of reasoning steps
	 * @param iterations     number of ReAct iterations
	 * @param success        processing success flag
	 * @return ChatResponse for ReAct answer
	 */
	public static ChatResponse react(String content, String conversationId, List<String> reasoning, int iterations,
			boolean success) {
		return ChatResponse.builder().content(content).conversationId(conversationId)
				.reasoning(reasoning != null ? reasoning : new ArrayList<>()).iterations(iterations).success(success)
				.timestamp(Instant.now()).processingType("REACT").build();
	}

	/**
	 * Creates an error response for failed processing.
	 *
	 * @param errorMessage   error description
	 * @param conversationId unique conversation identifier
	 * @return ChatResponse indicating failure
	 */
	public static ChatResponse error(String errorMessage, String conversationId) {
		return ChatResponse.builder().content("I apologize, but I encountered an error: " + errorMessage)
				.conversationId(conversationId).reasoning(List.of("Error occurred during processing")).iterations(0)
				.success(false).timestamp(Instant.now()).processingType("ERROR").build();
	}

	/**
	 * Adds metadata entry to the response.
	 *
	 * @param key   metadata key
	 * @param value metadata value
	 * @return this response for method chaining
	 */
	public ChatResponse addMetadata(String key, Object value) {
		this.metadata.put(key, value);
		return this;
	}

	/**
	 * Sets the processing time and updates metadata.
	 *
	 * @param processingTimeMs execution time in milliseconds
	 * @return this response for method chaining
	 */
	public ChatResponse withProcessingTime(long processingTimeMs) {
		this.processingTimeMs = processingTimeMs;
		this.metadata.put("processingTimeMs", processingTimeMs);
		return this;
	}

	/**
	 * Sets the confidence score for the answer.
	 *
	 * @param confidence score between 0.0 and 1.0
	 * @return this response for method chaining
	 */
	public ChatResponse withConfidence(double confidence) {
		this.confidenceScore = Math.max(0.0, Math.min(1.0, confidence));
		this.metadata.put("confidence", this.confidenceScore);
		return this;
	}

	/**
	 * Checks if this response was processed using ReAct.
	 *
	 * @return true if ReAct processing was used
	 */
	public boolean isReActResponse() {
		return iterations > 0 || "REACT".equals(processingType);
	}

	/**
	 * Checks if the response includes reasoning information.
	 *
	 * @return true if reasoning steps are available
	 */
	public boolean hasReasoning() {
		return reasoning != null && !reasoning.isEmpty();
	}
}