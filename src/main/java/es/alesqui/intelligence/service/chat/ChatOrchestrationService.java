package es.alesqui.intelligence.service.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.ai.chat.model.Generation;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import es.alesqui.intelligence.config.ChatConfiguration;
import es.alesqui.intelligence.dto.chat.request.ChatRequest;
import es.alesqui.intelligence.dto.chat.response.ChartData;
import es.alesqui.intelligence.dto.chat.response.ChatResponse;
import es.alesqui.intelligence.dto.chat.response.ClassificationResponse;
import es.alesqui.intelligence.service.chat.DynamicApiQueryClassifierService.QueryType;

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
    private final ChatConfiguration chatConfig;

    /**
     * Main entry point for processing chat requests asynchronously.
     * This method is fully non-blocking and returns a Mono that will emit the ChatResponse upon completion.
     *
     * @param request The incoming chat request.
     * @return A Mono<ChatResponse> representing the asynchronous operation.
     */
    public Mono<ChatResponse> processQuery(ChatRequest request) {
        log.info("🚀 Processing chat request: '{}' for conversation: {}",
            request.getQuery(), request.getConversationId());

        Instant startTime = Instant.now();

        // The entire process is now a reactive chain, starting from the request.
        return Mono.just(request)
            .doOnNext(this::validateRequest)
            .flatMap(this::classifyQuery) // Chain to the classification step
            .flatMap(classification -> routeQuery(request, classification)) // Chain to the routing step
            .doOnSuccess(response -> logSuccess(response, Duration.between(startTime, Instant.now()).toMillis()))
            .doOnError(error -> logError(request, error))
            .onErrorResume(error -> Mono.just(handleGlobalError(error, request.getConversationId()))); // Graceful error handling
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
     * Executes the tool-based chat flow.
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
            	org.springframework.ai.chat.model.ChatResponse aiResponse = chatWithReasoningResponse.getChatResponse();
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
     * Generates a direct response without API calls, fully reactively.
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

    private ChatResponse handleGlobalError(Throwable error, String conversationId) {
        log.error("❌ Global error in chat processing: {}", error.getMessage(), error);
        
        return ChatResponse.error(
            "An unexpected error occurred during processing: " + error.getMessage(), conversationId)
            .addMetadata("errorType", error.getClass().getSimpleName());
    }

    private void logSuccess(ChatResponse response, long processingTime) {
        log.debug("🔍 Processing Information:");
        log.debug("   Conversation ID: {}", response.getConversationId());
        log.debug("   Processing Type: {}", response.getProcessingType());
        log.debug("   Total Time: {} ms", processingTime);
        logWarningsIfAny(response);
    }

    private void logWarningsIfAny(ChatResponse response) {
        if (response.getProcessingTimeMs() != null && response.getProcessingTimeMs() > chatConfig.getTimeoutWarning()) {
            log.warn("⚠️ Slow processing detected: {} ms", response.getProcessingTimeMs());
        }
        
        Object totalTokens = response.getMetadata().get("totalTokens");
        if (totalTokens instanceof Integer && (Integer) totalTokens > chatConfig.getTokensWarning()) {
            log.warn("⚠️ High token usage: {} tokens", totalTokens);
        }
    }

    private void logError(ChatRequest request, Throwable error) {
        log.error("❌ Error processing query '{}' for conversation {}: {}", request.getQuery(),
                request.getConversationId(), error.getMessage());
    }
}