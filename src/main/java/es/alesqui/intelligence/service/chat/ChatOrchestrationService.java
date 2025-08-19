package es.alesqui.intelligence.service.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import es.alesqui.intelligence.config.ChatConfiguration;
import es.alesqui.intelligence.dto.chat.request.ChatRequest;
import es.alesqui.intelligence.dto.chat.response.ChatResponse;
import es.alesqui.intelligence.dto.chat.response.ChatWithReasoningResponse;
import es.alesqui.intelligence.dto.chat.response.ClassificationResponse;

import java.time.Duration;
import java.time.Instant;

/**
 * Synchronous orchestration service implementing the ReAct pattern for chat-based API interactions.
 * Handles query classification, API execution, and natural language response generation.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChatOrchestrationService {

    private final DynamicApiQueryClassifierService classifierService;
    private final SpringAIService springAIService;
    private final ChatConfiguration chatConfig;

    /**
     * Main entry point for processing chat requests synchronously.
     */
    public ChatResponse processQuery(ChatRequest request) {
        log.info("🚀 Processing chat request: '{}' for conversation: {}", 
            request.getQuery(), request.getConversationId());

        long startTime = System.currentTimeMillis();

        try {
            // Validate request
            validateRequest(request);
            
            // Classify query
            ClassificationResponse classification = classifyQuery(request);
            
            // Route query based on classification
            ChatResponse response = routeQuery(request, classification);
            
            long processingTime = System.currentTimeMillis() - startTime;
            logSuccess(response, processingTime);
            
            return response;
            
        } catch (Exception error) {
            logError(request, error);
            return handleGlobalError(error, request.getConversationId());
        }
    }

    /**
     * Validates the incoming chat request.
     */
    private void validateRequest(ChatRequest request) {
        if (!request.isValid()) {
            throw new IllegalArgumentException("Invalid chat request parameters");
        }
    }

    /**
     * Classifies the query to determine if API calls are needed.
     */
    private ClassificationResponse classifyQuery(ChatRequest request) {
        if (request.isForceReAct()) {
            log.info("🔄 Forcing ReAct processing for query");
            return ClassificationResponse.dataQuery(
                "Forced ReAct processing", 1.0, request.getConversationId());
        }

        try {
            return classifierService.classifyQuery(request.getQuery(), request.getConversationId())
                .block(chatConfig.getClassificationTimeout());
        } catch (Exception e) {
            log.warn("Classification failed, defaulting to ReAct: {}", e.getMessage());
            return ClassificationResponse.uncertain(
                "Classification failed - defaulting to ReAct", request.getConversationId());
        }
    }

    /**
     * Routes the query based on classification result.
     */
    private ChatResponse routeQuery(ChatRequest request, ClassificationResponse classification) {
        if (classification.shouldUseReAct()) {
            log.info("✅ Query requires API calls - Starting ReAct flow");
            
            Instant startTime = Instant.now();
            
            String systemPrompt = """
            You are an AI assistant with access to API tools.
            Use the available tools to gather information and answer user questions.
            
            Available tools:
            - listApis(): Get all available APIs
            - listEndpoints(apiName): Get endpoints for a specific API  
            - callApi(apiName, endpoint, parametersJson): Call an API endpoint
            
            Think step by step and use tools when needed to provide accurate answers.
            """;
            
            String userPrompt = request.getQuery();
            String conversationId = request.getConversationId();
            
            ChatWithReasoningResponse chatWithReasoningResponse = 
                springAIService.chatWithTools(systemPrompt, userPrompt, conversationId, request.isIncludeReasoning())
                    .block(chatConfig.getProcessingTimeout());
            
            String response = chatWithReasoningResponse.getChatResponse().getResult().getOutput().getText();
            if(request.isIncludeReasoning()) {
            	response += "\n\n";
            	response += chatWithReasoningResponse.getFormattedReasoning();
            }
            
            long processingTime = Duration.between(startTime, Instant.now()).toMillis();
            
            return ChatResponse.builder()
                    .content(response)
                    .conversationId(conversationId)
                    .success(true)
                    .processingType("TOOLS")
                    .processingTimeMs(processingTime)
                    .build();

        } else {
            log.info("💬 Query doesn't require API calls - Using direct response");
            return generateDirectResponse(request);
        }
    }
    
    /**
     * Generates direct response without API calls.
     */
    private ChatResponse generateDirectResponse(ChatRequest request) {
        Instant startTime = Instant.now();
        
        try {
            String response = springAIService.chat(
                "You are a helpful AI assistant. Provide clear, accurate answers.",
                request.getQuery(),
                request.getConversationId()
            ).block(chatConfig.getProcessingTimeout());
            
            long processingTime = Duration.between(startTime, Instant.now()).toMillis();
            
            return ChatResponse.builder()
                .content(response)
                .conversationId(request.getConversationId())
                .success(true)
                .processingType("DIRECT")
                .processingTimeMs(processingTime)
                .build();
                
        } catch (Exception e) {
            return ChatResponse.error(
                "Failed to generate direct response: " + e.getMessage(), 
                request.getConversationId());
        }
    }
    
    private ChatResponse handleGlobalError(Throwable error, String conversationId) {
        log.error("❌ Global error in chat processing: {}", error.getMessage(), error);
        
        return ChatResponse.error(
            "An unexpected error occurred during processing", conversationId)
            .addMetadata("errorType", error.getClass().getSimpleName())
            .addMetadata("errorMessage", error.getMessage());
    }

    private void logSuccess(ChatResponse response, long processingTime) {
    	log.debug("🔍 Processing Information:");
        log.debug("   Conversation ID: {}", response.getConversationId());
        log.debug("   Processing Type: {}", response.getProcessingType());
        log.debug("   All Metadata: {}", response.getMetadata());
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
