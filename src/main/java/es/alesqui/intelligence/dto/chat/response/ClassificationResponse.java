package es.alesqui.intelligence.dto.chat.response;

import es.alesqui.intelligence.service.chat.DynamicApiQueryClassifierService.QueryType;
import lombok.Builder;
import lombok.Data;
import java.time.Instant;

/**
 * Response DTO for query classification operations. Contains the specific
 * classification result (QueryType) and metadata.
 */
@Data
@Builder
public class ClassificationResponse {

    /**
     * Flag indicating if the query requires API tool access. This is derived
     * from the queryType.
     */
    private boolean isToolQuery; // Renamed from isDataQuery

    /**
     * The specific category the query was classified into.
     */
    private QueryType queryType;

    /**
     * Explanation of why the query was classified this way.
     */
    private String reasoning;

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
     * Creates a classification response for queries that require tool usage.
     * This indicates that the ReAct flow should be initiated.
     *
     * @param reasoning      Explanation for the classification.
     * @param conversationId Unique conversation identifier.
     * @param type           The specific type of query (DATA_QUERY or META_QUERY).
     * @return ClassificationResponse configured for tool usage.
     */
    public static ClassificationResponse toolQuery(String reasoning, String conversationId, QueryType type) {
        return ClassificationResponse.builder()
                .isToolQuery(true) // Updated to use the new field name
                .queryType(type)
                .reasoning(reasoning)
                .conversationId(conversationId)
                .build();
    }

    /**
     * Creates a classification response for queries that can be answered directly.
     *
     * @param reasoning      Explanation for the classification.
     * @param conversationId Unique conversation identifier.
     * @param type           The query type (DIRECT_ANSWER).
     * @return ClassificationResponse configured for a direct answer.
     */
    public static ClassificationResponse directAnswer(String reasoning, String conversationId, QueryType type) {
        return ClassificationResponse.builder()
                .isToolQuery(false) // Updated to use the new field name
                .queryType(type)
                .reasoning(reasoning)
                .conversationId(conversationId)
                .build();
    }

    /**
     * Creates a classification response for uncertain cases, defaulting to tool usage.
     *
     * @param reasoning      Explanation for the uncertainty.
     * @param conversationId Unique conversation identifier.
     * @return ClassificationResponse with a safe fallback to tool usage.
     */
    public static ClassificationResponse uncertain(String reasoning, String conversationId) {
        return ClassificationResponse.builder()
                .isToolQuery(true) // Updated to use the new field name
                .queryType(QueryType.DATA_QUERY)
                .reasoning("Uncertain classification: " + reasoning)
                .conversationId(conversationId)
                .build();
    }

    /**
     * Sets the classification processing time.
     *
     * @param timeMs Processing time in milliseconds.
     * @return This response for method chaining.
     */
    public ClassificationResponse withProcessingTime(long timeMs) {
        this.classificationTimeMs = timeMs;
        return this;
    }

    /**
     * Checks if this classification recommends using the tool-based ReAct flow.
     *
     * @return True if the ReAct flow should be used.
     */
    public boolean shouldUseReAct() {
        return isToolQuery; 
    }
}