package es.alesqui.intelligence.dto.chat.response;

import es.alesqui.intelligence.dto.chat.reasoning.ReasoningStep; // Added import
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List; // Added import
import java.util.Map;
import java.util.HashMap;

/**
 * Enhanced response DTO containing the final answer and detailed metadata about
 * the chat processing. This is the final object sent to the frontend.
 * It encapsulates the answer, execution details, and the structured
 * reasoning trace for visualization.
 */
@Data
@Builder
@NoArgsConstructor // Added for full Builder compatibility
@AllArgsConstructor // Added for full Builder compatibility
@JsonInclude(JsonInclude.Include.NON_NULL) // Ensures null fields are not sent in JSON
public class ChatResponse {

	/**
	 * The unique database ID for this ConversationRecord.
	 */
	private String recordId;

	/**
	 * Final answer content provided to the user.
	 */
	private String content;

	/**
	 * A structured list of steps detailing the AI's thought process.
	 * This trace includes thoughts, tool calls (with requests and responses),
	 * and the final answer, allowing for interactive visualization and debugging.
	 * This field will be populated only if reasoning is requested and available.
	 */
	private List<ReasoningStep> reasoningSteps; // <-- Replaced 'String reasoning'

	/**
	 * Unique identifier for the conversation session.
	 */
	private String conversationId;

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
	 * Type of processing used (DIRECT, TOOLS, HYBRID).
	 */
	private String processingType;

	/**
	 * The configuration data for a chart to be displayed in the frontend. This
	 * field will be populated if the AI's response includes a visual representation
	 * of data. It will be null if no chart was generated.
	 */
	private ChartData chart;

	/**
	 * Creates a direct response without tool processing.
	 */
	public static ChatResponse direct(String content, String conversationId) {
		return ChatResponse.builder()
				.content(content)
				.conversationId(conversationId)
				.success(true)
				.timestamp(Instant.now())
				.processingType("DIRECT")
				.build();
	}

	/**
	 * Creates a tool-enhanced response.
	 * Note: The 'reasoningSteps' and 'chart' are set by the orchestration service.
	 */
	public static ChatResponse withTools(String content, String conversationId) {
		return ChatResponse.builder()
				.content(content)
				.conversationId(conversationId)
				.success(true)
				.timestamp(Instant.now())
				.processingType("TOOLS")
				.build();
	}

	/**
	 * Creates an error response for failed processing.
	 */
	public static ChatResponse error(String errorMessage, String conversationId) {
		return ChatResponse.builder()
				.content("I apologize, but I encountered an error: " + errorMessage)
				.conversationId(conversationId)
				.success(false)
				.timestamp(Instant.now())
				.processingType("ERROR")
				.build();
	}

	// Utility methods
	public ChatResponse addMetadata(String key, Object value) {
		this.metadata.put(key, value);
		return this;
	}

	public ChatResponse withProcessingTime(long processingTimeMs) {
		this.processingTimeMs = processingTimeMs;
		this.metadata.put("processingTimeMs", processingTimeMs);
		return this;
	}
}