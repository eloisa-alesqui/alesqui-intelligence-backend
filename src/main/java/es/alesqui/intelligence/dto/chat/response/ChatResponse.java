package es.alesqui.intelligence.dto.chat.response;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.Map;
import java.util.HashMap;

/**
 * Enhanced response DTO containing the final answer and detailed metadata about the chat
 * processing. Encapsulates both direct responses and ReAct-processed results
 * with execution details and tool usage information.
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
   * Creates a direct response without ReAct processing.
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
   * Creates a tool-enhanced response with detailed tool call information.
   */
  public static ChatResponse withTools(String content, String conversationId) {
      ChatResponse response = ChatResponse.builder()
              .content(content)
              .conversationId(conversationId)
              .success(true)
              .timestamp(Instant.now())
              .processingType("TOOLS")
              .build();
      
      return response;
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