package es.alesqui.intelligence.service.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import es.alesqui.intelligence.config.ChatConfig;
import es.alesqui.intelligence.dto.chat.request.ChatRequest;
import es.alesqui.intelligence.dto.chat.response.ChartData;
import es.alesqui.intelligence.dto.chat.response.ChatResponse;
import es.alesqui.intelligence.dto.chat.response.ClassificationResponse;
import es.alesqui.intelligence.security.SecurityUtils;
import es.alesqui.intelligence.service.chat.DynamicApiQueryClassifierService.QueryType;
import es.alesqui.intelligence.service.conversation.ChatMemoryService;
import es.alesqui.intelligence.service.conversation.ConversationService;

import java.time.Duration;
import java.time.Instant;

/**
 * Asynchronous orchestration service implementing the ReAct pattern for chat-based API interactions.
 * Handles query classification, API execution, and natural language response generation in a non-blocking manner.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChatOrchestrationService {

    private final DynamicApiQueryClassifierService classifierService;
    private final SpringAIService springAIService;
    private final ChatConfig chatConfig;
    private final ConversationService conversationService;
    private final ChatMemoryService chatMemoryService; // INYECTAR EL NUEVO SERVICIO
    private final ChatMemory chatMemory;

    /**
     * Main entry point for processing chat requests asynchronously.
     * This method is fully non-blocking and returns a Mono that will emit the ChatResponse upon completion.
     *
     * @param request The incoming chat request.
     * @return A Mono<ChatResponse> representing the asynchronous operation.
     */
    public Mono<ChatResponse> processQuery(ChatRequest request) {
        log.info("🚀 Processing chat request: '{}' for conversation: {}", request.getQuery(),
                request.getConversationId());

        Instant startTime = Instant.now();
        
        Mono<Void> ensureMemoryLoaded = Mono.defer(() -> {
            if (chatMemory.get(request.getConversationId()).isEmpty()) {
                return chatMemoryService.loadHistoryIntoMemory(request.getConversationId());
            } else {
                log.debug("Memory for conversation '{}' is already populated.", request.getConversationId());
                return Mono.empty();
            }
        });

        Mono<ChatResponse> processingMono = Mono.just(request)
                .doOnNext(this::validateRequest)
                .flatMap(this::classifyQuery)
                .flatMap(classification -> routeQuery(request, classification));

        return ensureMemoryLoaded.then(processingMono).flatMap(response -> {

            Mono<Void> saveSuccessOperation = SecurityUtils.getCurrentUsername()
                    .flatMap(username -> conversationService.saveInteraction(request, response, username))
                    .then(); 

            return saveSuccessOperation
                    .thenReturn(response)
                    .doOnSuccess(r -> logSuccess(r, Duration.between(startTime, Instant.now()).toMillis()));

        }).onErrorResume(error -> {
            logError(request, error);

            Mono<Void> saveFailureOperation = SecurityUtils.getCurrentUsername()
                    .flatMap(username -> conversationService.saveFailedInteraction(request, error, username));

            return saveFailureOperation
                    .then(Mono.just(handleGlobalError(error, request.getConversationId())));
        });
    }

    /**
     * Validates the incoming chat request.
     *
     * @param request The chat request to validate.
     */
    private void validateRequest(ChatRequest request) {
        if (!request.isValid()) {
            throw new IllegalArgumentException("Invalid chat request parameters");
        }
    }

    /**
     * Classifies the query reactively to determine if API calls are needed.
     *
     * @param request The chat request.
     * @return A Mono emitting the ClassificationResponse.
     */
    private Mono<ClassificationResponse> classifyQuery(ChatRequest request) {
        if (request.isForceReAct()) {
            log.info("🔄 Forcing ReAct processing for query");
            return Mono.just(ClassificationResponse.toolQuery(
                "Forced ReAct processing", request.getConversationId(), QueryType.DATA_QUERY));
        }

        return classifierService.classifyQuery(request.getQuery(), request.getConversationId())
            .timeout(chatConfig.getClassificationTimeout())
            .doOnError(e -> log.warn("Classification failed or timed out, defaulting to ReAct: {}", e.getMessage()))
            .onErrorResume(e -> Mono.just(ClassificationResponse.uncertain(
                "Classification failed - defaulting to ReAct", request.getConversationId())));
    }

    /**
     * Routes the query based on the classification result, returning a Mono<ChatResponse>.
     *
     * @param request        The original chat request.
     * @param classification The result of the classification step.
     * @return A Mono that will resolve to the final ChatResponse.
     */
    private Mono<ChatResponse> routeQuery(ChatRequest request, ClassificationResponse classification) {
        if (classification.shouldUseReAct()) {
            log.info("✅ Query requires API calls - Starting ReAct flow");
            return executeToolBasedResponse(request);
        } else {
            log.info("💬 Query doesn't require API calls - Using direct response");
            return generateDirectResponse(request);
        }
    }
    
    /**
     * Executes the tool-based (ReAct) chat flow.
     * This involves calling an AI model that can use a predefined set of tools
     * (like API calls or chart generation) to answer the user's query.
     *
     * @param request The original chat request.
     * @return A Mono emitting the final ChatResponse after tool execution.
     */
    private Mono<ChatResponse> executeToolBasedResponse(ChatRequest request) {
        Instant startTime = Instant.now();
        String systemPrompt = """
    		You are a highly skilled AI assistant designed to interact with APIs. Your primary goal is to answer user questions by intelligently using a set of available tools.

    		**Your Guiding Principles:**
    		1.  **Think Step-by-Step:** Before acting, break down the user's request into a logical sequence of steps.
    		2.  **Use Tools Intelligently:** Always use the `list_apis` tool first to discover the available operations before attempting to call an API. Do not guess endpoint names or parameters.
    		3.  **Be Resourceful:** If a tool call fails, analyze the error, correct your approach, and try again. If it persists, inform the user clearly.
    		4.  **Stay Focused:** Only use the provided tools. Do not invent tools.

    		**Tool Reference:**
    		- `list_apis()`: **Lists all configured and available APIs in the system. Use this to answer any questions about "what APIs are available", "which APIs are configured", or "what APIs I can use". This should always be your first step.**
    		- `list_endpoints(apiName)`: Lists all operations for a specific API. Use this to find out what a specific API can do.
    		- `call_api(apiName, operationId, parameters)`: Executes a specific API operation.
    		- `create_excel_file(jsonData, filename)`: Generates an Excel file from a JSON array.
    		- `create_chart(chartType, jsonData, labelKey, dataKey, datasetLabel)`: Generates a chart configuration object.

    		**Workflow for Creating Files (Excel):**
    		1.  Obtain the necessary data by calling an API using `call_api`.
    		2.  Ensure the result is a valid JSON array.
    		3.  Pass the JSON data and a descriptive filename to `create_excel_file`.

    		**Workflow for Creating Charts:**
    		1.  Obtain the necessary data using `call_api`.
    		2.  Analyze the JSON result to identify the correct keys for labels (`labelKey`) and data values (`dataKey`).
    		3.  Call `create_chart` with all required parameters.
    		4.  **CRITICAL:** After the `create_chart` tool is called successfully, your task is complete. Your final answer must be a brief summary of the data and a confirmation that the chart is ready.
    		5.  **DO NOT** include the raw JSON chart configuration in your final response.
    		""";
            
        return springAIService.chatWithTools(systemPrompt, request.getQuery(), request.getConversationId(), request.isIncludeReasoning())
            .timeout(chatConfig.getToolsTimeout())
            .map(chatWithReasoningResponse -> {

            	String responseContent = chatWithReasoningResponse.getChatResponse().getResult().getOutput().getText();
                String formattedReasoning = null;

                if (request.isIncludeReasoning()) {
                    formattedReasoning = chatWithReasoningResponse.getFormattedReasoning();
                }
                
                ChartData chartData = chatWithReasoningResponse.getChart();
                
                long processingTime = Duration.between(startTime, Instant.now()).toMillis();
                
                return ChatResponse.builder()
                        .content(responseContent)
                        .reasoning(formattedReasoning)
                        .chart(chartData)
                        .conversationId(request.getConversationId())
                        .success(true)
                        .processingType("TOOLS")
                        .processingTimeMs(processingTime)
                        .build();
            });
    }
    
    /**
     * Generates a direct conversational response without using any external tools.
     * This is used when the query classifier determines that no API calls are necessary.
     *
     * @param request The original chat request.
     * @return A Mono emitting the direct ChatResponse from the AI model.
     */
    private Mono<ChatResponse> generateDirectResponse(ChatRequest request) {
        Instant startTime = Instant.now();
        
        return springAIService.chat(
            "You are a helpful AI assistant. Provide clear, accurate answers.",
            request.getQuery(),
            request.getConversationId()
        ).timeout(chatConfig.getProcessingTimeout())
        .map(responseContent -> {
            long processingTime = Duration.between(startTime, Instant.now()).toMillis();
            return ChatResponse.builder()
                .content(responseContent)
                .conversationId(request.getConversationId())
                .success(true)
                .processingType("DIRECT")
                .processingTimeMs(processingTime)
                .build();
        });
    }

    /**
     * Creates a standardized error response when an unrecoverable exception occurs.
     * This method is the final step in the .onErrorResume() chain.
     *
     * @param error The throwable that was caught.
     * @param conversationId The ID of the conversation that failed.
     * @return A user-friendly ChatResponse object detailing the error.
     */
    private ChatResponse handleGlobalError(Throwable error, String conversationId) {
        log.error("❌ Global error in chat processing: {}", error.getMessage(), error);
        
        return ChatResponse.error(
            "An unexpected error occurred during processing: " + error.getMessage(), conversationId)
            .addMetadata("errorType", error.getClass().getSimpleName());
    }
    
    /**
     * Logs the details of a successfully processed chat response.
     * Also triggers checks for performance or token usage warnings.
     *
     * @param response The successful ChatResponse.
     * @param processingTime The total time taken for processing in milliseconds.
     */
    private void logSuccess(ChatResponse response, long processingTime) {
        log.debug("🔍 Processing Information:");
        log.debug("   Conversation ID: {}", response.getConversationId());
        log.debug("   Processing Type: {}", response.getProcessingType());
        log.debug("   Total Time: {} ms", processingTime);
        logWarningsIfAny(response);
    }

    /**
     * Checks a successful response for potential issues, like slow processing time
     * or high token usage, and logs a warning if they exceed configured thresholds.
     *
     * @param response The successful ChatResponse to inspect.
     */
    private void logWarningsIfAny(ChatResponse response) {
        if (response.getProcessingTimeMs() != null && response.getProcessingTimeMs() > chatConfig.getTimeoutWarning()) {
            log.warn("⚠️ Slow processing detected: {} ms", response.getProcessingTimeMs());
        }
        
        Object totalTokens = response.getMetadata().get("totalTokens");
        if (totalTokens instanceof Integer && (Integer) totalTokens > chatConfig.getTokensWarning()) {
            log.warn("⚠️ High token usage: {} tokens", totalTokens);
        }
    }

    /**
     * Logs the details of a failed request.
     * This is typically called from an .onErrorResume() or .doOnError() block.
     *
     * @param request The original ChatRequest that failed.
     * @param error The throwable that was caught.
     */
    private void logError(ChatRequest request, Throwable error) {
        log.error("❌ Error processing query '{}' for conversation {}: {}", request.getQuery(),
                request.getConversationId(), error.getMessage());
    }
}