package es.alesqui.intelligence.service.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import es.alesqui.intelligence.config.ChatConfig;
import es.alesqui.intelligence.dto.chat.request.ChatRequest;
import es.alesqui.intelligence.dto.chat.response.ChartData;
import es.alesqui.intelligence.dto.chat.response.ChatResponse;
import es.alesqui.intelligence.dto.chat.response.ClassificationResponse;
import es.alesqui.intelligence.security.SecurityUtils;
import es.alesqui.intelligence.service.chat.DynamicApiQueryClassifierService.QueryType;
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

        Mono<ChatResponse> processingMono = Mono.just(request)
                .doOnNext(this::validateRequest)
                .flatMap(this::classifyQuery)
                .flatMap(classification -> routeQuery(request, classification));

        return processingMono.flatMap(response -> {

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
            You are an AI assistant with access to API tools.
            Use the available tools to gather information and answer user questions.
            
            Available tools:
            - listApis(): Get all available APIs
            - listEndpoints(apiName): Get endpoints for a specific API  
            - callApi(apiName, endpoint, parametersJson): Call an API endpoint
            - createExcelFile(jsonData, filename): Creates an Excel file from JSON data and returns a download link.
            - createChart(chartType, jsonData, labelKey, dataKey, datasetLabel): Creates a chart configuration from JSON data.
            
            **Workflow for creating files:**
            1. First, use other tools like 'listEndpoints' to gather the data the user wants.
            2. Second, structure this data into a valid JSON array format.
            3. Finally, call 'createExcelFile' with the JSON data to get the download link for the user.
            
            **Workflow for creating CHARTS:**
            1. Use 'callApi' to get the necessary data.
            2. Analyze the JSON result to identify the keys for labels and data.
            3. Call 'createChart' with the data and keys to get a chart configuration object.
            4. **IMPORTANT:** After calling createChart, your job is done. Your final answer should be a brief summary of the data, informing the user that the chart has been generated.
            5. **Do NOT include the raw chart JSON configuration in your final response to the user.**
            
            Think step by step and use tools when needed to provide accurate answers.
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