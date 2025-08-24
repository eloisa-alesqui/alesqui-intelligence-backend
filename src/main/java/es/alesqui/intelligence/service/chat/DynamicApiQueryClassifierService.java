package es.alesqui.intelligence.service.chat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

import es.alesqui.intelligence.config.ChatConfiguration;
import es.alesqui.intelligence.dto.chat.response.ClassificationResponse;
import es.alesqui.intelligence.model.unified.UnifiedApiDocument;
import es.alesqui.intelligence.service.UnifiedApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Service responsible for the initial classification of user queries.
 *
 * This service acts as the central "brain" of the orchestration layer, determining the
 * user's intent before any complex processing begins. It analyzes the user's query
 * against the known capabilities of all configured APIs and classifies it into one of
 * three categories:
 *
 * 1.  **DATA_QUERY**: The user is asking for specific business data that requires
 * calling one or more API endpoints (e.g., "show me last week's sales").
 * 2.  **META_QUERY**: The user is asking a question about the APIs themselves, such as
 * their structure, available endpoints, or parameters (e.g., "what endpoints
 * does the ecommerce API have?").
 * 3.  **DIRECT_ANSWER**: The query is a general question, a greeting, or a topic
 * that does not require any knowledge of the configured APIs (e.g., "hello",
 * "what is a REST API?").
 *
 * To achieve this, the service dynamically constructs a prompt for an AI model,
 * enriching it with the high-level capabilities of each active API (using the
 * pre-calculated 'capabilitiesSummary' field for efficiency). The prompt and its
 * structure are cached to minimize latency on subsequent requests.
 *
 * The final classification (`DATA_QUERY` / `META_QUERY` vs. `DIRECT_ANSWER`) is used by the
 * ChatOrchestrationService to decide whether to route the request to the tool-based
 * ReAct flow or to a direct, conversational response flow.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DynamicApiQueryClassifierService {

    private final SpringAIService springAIService;
    private final UnifiedApiService unifiedApiService;
    private final ChatConfiguration chatConfig;

    private String cachedClassificationPrompt;
    private Instant lastPromptUpdate;

    /**
     * Enum representing the three possible types of user queries.
     */
    public enum QueryType {
        /**
         * The user is asking for specific business data that requires calling an API.
         * Example: "show me last week's sales"
         */
        DATA_QUERY,
        /**
         * The user is asking about the APIs themselves (structure, endpoints, parameters).
         * Example: "what endpoints are available in the ecommerce API?"
         */
        META_QUERY,
        /**
         * The query is a general question or greeting that doesn't require API knowledge.
         * Example: "hello, how are you?"
         */
        DIRECT_ANSWER
    }

    /**
     * Classifies the user's query to determine if it requires API interaction.
     * The method is fully reactive and non-blocking.
     *
     * @param query The user's query string.
     * @param conversationId The unique identifier for the conversation.
     * @return A Mono emitting a ClassificationResponse containing the query type.
     */
    public Mono<ClassificationResponse> classifyQuery(String query, String conversationId) {
        Instant startTime = Instant.now();

        return getOrBuildClassificationPrompt()
            .flatMap(prompt -> classifyWithAI(query, prompt))
            .map(queryType -> {
                long processingTime = Duration.between(startTime, Instant.now()).toMillis();
                boolean requiresTools = (queryType == QueryType.DATA_QUERY || queryType == QueryType.META_QUERY);

                if (requiresTools) {
                    return ClassificationResponse.toolQuery("Query requires API tool access", conversationId, queryType)
                            .withProcessingTime(processingTime);
                } else {
                    return ClassificationResponse.directAnswer("Query can be answered directly", conversationId, queryType)
                            .withProcessingTime(processingTime);
                }
            })
            .doOnNext(response -> log.debug("Query '{}' classified as requiring tools: {}", query, response.shouldUseReAct()));
    }

    /**
     * Retrieves the classification prompt from cache or rebuilds it if it's stale.
     *
     * @return A Mono emitting the classification prompt string.
     */
    private Mono<String> getOrBuildClassificationPrompt() {
        if (cachedClassificationPrompt != null && lastPromptUpdate != null
                && lastPromptUpdate.isAfter(Instant.now().minus(chatConfig.getPromptCacheTime()))) {
            log.trace("Using cached classification prompt.");
            return Mono.just(cachedClassificationPrompt);
        }

        return buildDynamicClassificationPrompt().doOnNext(prompt -> {
            cachedClassificationPrompt = prompt;
            lastPromptUpdate = Instant.now();
            log.debug("Classification prompt updated (cache time: {})", chatConfig.getPromptCacheTime());
        });
    }

    /**
     * Builds the dynamic classification prompt using the capabilities of the available APIs.
     *
     * @return A Mono emitting the fully constructed prompt.
     */
    private Mono<String> buildDynamicClassificationPrompt() {
        return unifiedApiService.findActiveApis().collectList().map(this::generatePromptFromApis);
    }

    /**
     * Generates the main prompt text from the list of available API documents.
     * This prompt instructs the AI on how to perform the three-way classification.
     *
     * @param apis The list of active UnifiedApiDocument objects.
     * @return The complete prompt string.
     */
    private String generatePromptFromApis(List<UnifiedApiDocument> apis) {
        StringBuilder prompt = new StringBuilder();

        prompt.append(
            """
            You are an expert query classifier. Your task is to classify the user's query into one of three categories: DATA_QUERY, META_QUERY, or DIRECT_ANSWER.
            Respond with ONLY the category name.

            1.  **DATA_QUERY**: The user is asking for specific business data that requires calling an API to fetch information.
                (Examples: "show me last week's sales", "who is the user with email test@test.com?", "get product details for ID 123").

            2.  **META_QUERY**: The user is asking ABOUT the APIs themselves. They want to know about the structure, capabilities, endpoints, or parameters.
                (Examples: "what endpoints are available in the ecommerce API?", "what parameters does the searchUsers endpoint take?", "which APIs can I use?").

            3.  **DIRECT_ANSWER**: The query is a general question, a greeting, or something that does not require any API knowledge.
                (Examples: "hello", "what is your purpose?", "explain what a REST API is").

            Here are the available APIs to help you classify:
            """
        );

        for (UnifiedApiDocument api : apis) {
            prompt.append("\n---");
            prompt.append("\nAPI Name: ").append(api.getName());
            prompt.append("\nDescription: ").append(api.getDescription());
            prompt.append("\nCapabilities: ").append(api.getCapabilitiesSummary());
        }

        prompt.append("\n\n---");
        prompt.append("\nUser Query: \"{}\"");
        prompt.append("\nCategory:");

        return prompt.toString();
    }

    /**
     * Calls the AI model to perform the classification.
     *
     * @param query The user's query.
     * @param promptTemplate The prompt template to use.
     * @return A Mono emitting the classified QueryType.
     */
    private Mono<QueryType> classifyWithAI(String query, String promptTemplate) {
        String finalPrompt = promptTemplate.replace("{}", query);

        return springAIService
                .chatWithoutMemory("You are a precise query classifier. Respond with only the category name.", finalPrompt)
                .map(this::parseResponse);
    }

    /**
     * Parses the raw string response from the AI into a QueryType enum.
     *
     * @param response The AI's raw response.
     * @return The corresponding QueryType, defaulting to DIRECT_ANSWER on failure.
     */
    private QueryType parseResponse(String response) {
        String cleanResponse = response.trim().toUpperCase();
        try {
            return QueryType.valueOf(cleanResponse);
        } catch (IllegalArgumentException e) {
            log.warn("Classifier returned an unknown type '{}'. Defaulting to DIRECT_ANSWER.", cleanResponse);
            return QueryType.DIRECT_ANSWER; // Safe fallback
        }
    }
}