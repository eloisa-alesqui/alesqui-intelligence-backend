package es.alesqui.postmangpt.service.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import es.alesqui.postmangpt.service.UnifiedApiService;
import es.alesqui.postmangpt.service.api.ApiConfigurationService;
import es.alesqui.postmangpt.service.api.ApiExecutionService;
import es.alesqui.postmangpt.dto.chat.react.*;
import es.alesqui.postmangpt.dto.chat.request.ChatRequest;
import es.alesqui.postmangpt.dto.chat.request.ApiCallRequest;
import es.alesqui.postmangpt.dto.chat.response.ChatResponse;
import es.alesqui.postmangpt.dto.chat.response.ClassificationResponse;
import es.alesqui.postmangpt.dto.chat.response.ApiCallResponse;
import es.alesqui.postmangpt.model.unified.UnifiedApiDocument;
import es.alesqui.postmangpt.model.unified.UnifiedEndpoint;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    private final UnifiedApiService unifiedApiService;
    private final ApiExecutionService apiExecutionService;
    private final ApiConfigurationService apiConfigurationService;
    private final ObjectMapper objectMapper;

    private static final Duration CLASSIFICATION_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration THOUGHT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration ACTION_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration OBSERVATION_TIMEOUT = Duration.ofSeconds(8);
    private static final int MAX_RETRIES = 2;

    /**
     * Main entry point for processing chat requests synchronously.
     */
    public ChatResponse processQuery(ChatRequest request) {
        log.info("🚀 Processing chat request: '{}' for conversation: {}", 
            request.getQuery(), request.getConversationId());

        Instant startTime = Instant.now();

        try {
            // Validate request
            validateRequest(request);
            
            // Classify query
            ClassificationResponse classification = classifyQuery(request);
            
            // Route query based on classification
            ChatResponse response = routeQuery(request, classification);
            
            logSuccess(request, startTime);
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
                .block(CLASSIFICATION_TIMEOUT);
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
            return executeReActFlow(request, classification);
        } else {
            log.info("💬 Query doesn't require API calls - Using direct response");
            return generateDirectResponse(request);
        }
    }

    // ========== REACT FLOW ==========

    /**
     * Executes the ReAct flow for queries requiring API interactions.
     */
    private ChatResponse executeReActFlow(ChatRequest request, ClassificationResponse classification) {
        ReActContext context = new ReActContext(request.getQuery(), request.getConversationId());
        Instant startTime = Instant.now();

        try {
            // Perform ReAct cycle
            performReActCycle(context, request.getMaxIterations());
            
            // Build response
            return buildReActResponse(context, startTime, classification);
            
        } catch (Exception error) {
            return handleReActError(error, context, request.getConversationId());
        }
    }

    /**
     * Performs the complete ReAct cycle with iteration control.
     */
    private void performReActCycle(ReActContext context, int maxIterations) {
        for (int iteration = 1; iteration <= maxIterations; iteration++) {
            if (context.isComplete()) {
                log.info("✅ ReAct cycle completed after {} iterations", iteration - 1);
                break;
            }
            
            if (context.getMetadata().getOrDefault("finalAnswerProvided", false)) {
                log.info("🏁 Final answer already provided, ending ReAct cycle after {} iterations", iteration - 1);
                break;
            }
            
            performSingleReActStep(context, iteration);
            
            if (context.getMetadata().getOrDefault("finalAnswerProvided", false)) {
                log.info("🏁 Task completed with final answer, ending ReAct cycle after {} iterations", iteration);
                break;
            }
        }
        
        log.info("✅ ReAct cycle finished after {} iterations", context.getIterations());
    }

    /**
     * Performs a single ReAct step: Thought -> Action -> Observation.
     */
    private void performSingleReActStep(ReActContext context, int iteration) {
        if (context.isComplete() || context.getFinalAnswer() != null) {
        	log.info("🏁 Final answer already provided, skipping step {}", iteration);
            return;
        }
        
        if (context.getMetadata().getOrDefault("finalAnswerProvided", false)) {
            log.info("🏁 Final answer already provided, skipping step {}", iteration);
            return;
        }

        log.debug("🔄 Starting ReAct step {}", iteration);
        Instant stepStart = Instant.now();
        ReActStep step = ReActStep.create(iteration);

        try {
            // Step 1: Generate Thought
            generateThought(context, step);
            
            if (context.getMetadata().getOrDefault("finalAnswerProvided", false)) {
                log.info("🏁 Final answer provided during thought generation, completing step");
                step.complete();
                context.incrementIteration();
                return;
            }
            
            // Step 2: Parse and Execute Action
            parseAndExecuteAction(context, step);
            
            if (context.getMetadata().getOrDefault("finalAnswerProvided", false)) {
                log.info("🏁 Final answer provided during action execution, skipping observation");
                step.complete();
                context.incrementIteration();
                return;
            }
            
            // Step 3: Generate Observation
            generateObservation(context, step);
            
            // Complete step
            step.complete();
            context.incrementIteration();
            
            log.debug("✅ Completed ReAct step {} in {}ms", 
                iteration, Duration.between(stepStart, Instant.now()).toMillis());
                
        } catch (Exception error) {
            handleStepError(error, context, step);
        }
    }

    // ========== THOUGHT PHASE ==========

    /**
     * Generates AI thought for the current context.
     */
    private void generateThought(ReActContext context, ReActStep step) {
        log.debug("💭 Generating thought for iteration {}", step.getStepNumber());

        try {
            String prompt = buildThoughtPrompt(context);
            String thought = generateAIThought(prompt, context.getConversationId());
            
            log.debug("💭 THOUGHT: {}", thought);
            step.withThought(thought);
            context.addThought(thought);
            
        } catch (Exception error) {
            String fallbackThought = "Unable to generate thought: " + error.getMessage();
            step.withThought(fallbackThought);
            context.addThought(fallbackThought);
        }
    }

    /**
     * Builds a structured prompt for thought generation.
     */
    private String buildThoughtPrompt(ReActContext context) {
        List<UnifiedApiDocument> apis = unifiedApiService.findActiveApis()
            .collectList()
            .block(Duration.ofSeconds(5));
            
        return createStructuredPrompt(context, apis);
    }

    private String createStructuredPrompt(ReActContext context, List<UnifiedApiDocument> apis) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append(String.format("""
            🎯 OBJECTIVE: Answer this query: "%s"
            
            📊 AVAILABLE APIs:
            """, context.getUserQuery()));
        
        // Add concise API information
        apis.forEach(api -> {
            prompt.append(String.format("• %s: %s\n", api.getName(), api.getDescription()));
        });
        
        // Add previous steps if any
        if (!context.getStepHistory().isEmpty()) {
            prompt.append("\n📝 PREVIOUS STEPS:\n");
            context.getStepHistory().forEach(step -> 
                prompt.append("   ").append(step).append("\n"));
        }
        
        // Check if we have API data
        boolean hasApiData = context.getMetadata().getOrDefault("hasApiData", false);
        
        prompt.append("""
            
            🎬 AVAILABLE ACTIONS:
            • list_endpoints <api_name> - List endpoints for an API
            • call_api <api_name> <endpoint_name> {} - Call endpoint without parameters
            • call_api <api_name> <endpoint_name> {"param": "value"} - Call with parameters
            • final_answer <api_response> - Provide final answer (ONLY after getting data)
            
            """);
        
        // 🆕 CORRECTED GUIDANCE - NO CONTENT IN THOUGHT PHASE
        if (hasApiData) {
            prompt.append("""
                ✅ You have API data available. 
                
                NEXT STEP: Use ACTION: final_answer to provide the response.
                
                IMPORTANT: In this THOUGHT phase, only decide the next action. 
                Do NOT write the actual response content here.
                The response content will be generated when the final_answer action is executed.
                """);
        } else {
            prompt.append("""
                🤔 Analyze what the user needs and determine the appropriate action.
                """);
        }
        
        prompt.append("""
            
            📋 INSTRUCTIONS:
            - In this THOUGHT phase, only analyze and decide the next action
            - Do NOT provide the actual answer content in the thought
            - Format your response as: ACTION: <action_name> <parameters_if_needed>
            - Keep thoughts concise and action-focused
            """);
        
        return prompt.toString();
    }

    private String generateAIThought(String prompt, String conversationId) {
        return springAIService.chat(
            "You are an expert AI agent using the ReAct pattern. Be concise and action-oriented.",
            prompt, 
            conversationId
        ).block(THOUGHT_TIMEOUT);
    }

    // ========== ACTION PHASE ==========

    /**
     * Parses and executes the action from the thought.
     */
    private void parseAndExecuteAction(ReActContext context, ReActStep step) {
        String thought = context.getLastThought();
        
        try {
            ActionCommand action = parseActionFromThought(thought);
            executeActionWithRetry(action, context, step);
        } catch (Exception error) {
            handleActionError(error, context, step);
        }
    }

    /**
     * Parses action from thought using regex pattern.
     */
    private ActionCommand parseActionFromThought(String thought) {
        Pattern actionPattern = Pattern.compile(
            "ACTION:\\s*([\\w_]+)(?:\\s+(.*))?", 
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
        );
        
        Matcher matcher = actionPattern.matcher(thought);
        
        if (matcher.find()) {
            String actionType = matcher.group(1).toLowerCase().trim();
            String parameters = matcher.group(2) != null ? matcher.group(2).trim() : "";
            
            // Validate action type
            if (isValidAction(actionType)) {
                return new ActionCommand(actionType, parameters);
            }
            throw new IllegalArgumentException("Unknown action type: " + actionType);
        }
        
        throw new IllegalArgumentException("No valid ACTION found in thought");
    }

    private boolean isValidAction(String action) {
        return Set.of("list_apis", "list_endpoints", "call_api", "final_answer").contains(action);
    }

    /**
     * Executes action with retry logic.
     */
    private void executeActionWithRetry(ActionCommand action, ReActContext context, ReActStep step) {
        log.debug("🎬 Executing ACTION: {} with params: {}", action.getType(), action.getParameters());
        
        int retries = 0;
        Exception lastError = null;
        
        while (retries <= MAX_RETRIES) {
            try {
                executeSpecificAction(action, context, step);
                log.debug("✅ Action executed successfully: {}", action.getType());
                return;
            } catch (Exception e) {
                lastError = e;
                if (isRetryableError(e) && retries < MAX_RETRIES) {
                    retries++;
                    log.warn("Retrying action {} (attempt {})", action.getType(), retries + 1);
                    try {
                        Thread.sleep(500 * retries); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    break;
                }
            }
        }
        
        throw new RuntimeException("Action failed after " + retries + " retries", lastError);
    }

    /**
     * Routes to specific action implementation.
     */
    private void executeSpecificAction(ActionCommand action, ReActContext context, ReActStep step) {
        String result = switch (action.getType()) {
            case "list_apis" -> executeListApis(context);
            case "list_endpoints" -> executeListEndpoints(action.getParameters(), context);
            case "call_api" -> executeCallApi(action.getParameters(), context);
            case "final_answer" -> executeFinalAnswer(action.getParameters(), context);
            default -> "❌ Unknown action: " + action.getType();
        };
        
        step.withAction(action);
        context.addActionResult(result);
    }

    // ========== ACTION IMPLEMENTATIONS ==========

    private String executeListApis(ReActContext context) {
        List<UnifiedApiDocument> apis = unifiedApiService.findActiveApis()
            .collectList()
            .block(Duration.ofSeconds(5));
            
        if (apis.isEmpty()) {
            return "⚠️ No APIs available";
        }
        
        StringBuilder result = new StringBuilder("📊 Available APIs:\n");
        apis.forEach(api -> result.append(
            String.format("• %s: %s\n", api.getName(), api.getDescription())));
        
        return result.toString();
    }

    private String executeListEndpoints(String apiName, ReActContext context) {
        String cleanApiName = apiName.trim();
        
        try {
            UnifiedApiDocument api = unifiedApiService.findByName(cleanApiName)
                .block(Duration.ofSeconds(5));
                
            if (api == null) {
                return "❌ API not found: " + cleanApiName;
            }
            
            StringBuilder result = new StringBuilder(
                String.format("🔗 Endpoints for API '%s':\n", api.getName()));
            
            if (api.getEndpoints() != null && !api.getEndpoints().isEmpty()) {
                api.getEndpoints().stream()
                    .filter(e -> "GET".equalsIgnoreCase(e.getMethod()))
                    .forEach(endpoint -> {
                        result.append(String.format("• %s - %s", 
                            endpoint.getOperationId(),
                            endpoint.getSummary() != null ? endpoint.getSummary() : endpoint.getPath()));
                        result.append("\n");
                    });
            } else {
                result.append("⚠️ No endpoints available\n");
            }
            
            return result.toString();
            
        } catch (Exception e) {
            return "❌ Error listing endpoints: " + e.getMessage();
        }
    }

    /**
     * Enhanced API call execution for simple cases.
     */
    private String executeCallApi(String parameters, ReActContext context) {
        try {
            ApiCallRequest request = parseApiCallParameters(parameters, context);
            
            log.info("🔄 Executing API call: {} - {}", 
                request.getApiName(), request.getEndpoint());
                
            ApiCallResponse response = executeApiCall(request);
            return formatApiCallResult(response, context);
            
        } catch (Exception error) {
            log.error("API call error: {}", error.getMessage());
            return "❌ API call failed: " + error.getMessage();
        }
    }

    /**
     * Simplified API call parameter parsing.
     */
    private ApiCallRequest parseApiCallParameters(String parameters, ReActContext context) {
        log.debug("Parsing API call parameters: {}", parameters);
        
        // Split into max 3 parts to preserve JSON
        String[] parts = parameters.trim().split("\\s+", 3);
        
        if (parts.length < 2) {
            throw new IllegalArgumentException(
                "Format: call_api <api_name> <endpoint_name> [json_params]");
        }
        
        String apiName = parts[0];
        String endpointName = parts[1];
        String jsonParams = parts.length > 2 ? parts[2] : "{}";
        
        // Ensure JSON format
        if (!jsonParams.startsWith("{")) {
            jsonParams = "{}";
        }
        
        log.debug("API: {}, Endpoint: {}, Params: {}", apiName, endpointName, jsonParams);
        
        // For simple GET requests without parameters
        if ("{}".equals(jsonParams)) {
            return createSimpleApiCallRequest(apiName, endpointName, context.getConversationId());
        }
        
        // For requests with parameters
        Map<String, Object> paramMap = parseJsonParameters(jsonParams);
        return createApiCallRequestWithParams(apiName, endpointName, paramMap, context.getConversationId());
    }

    /**
     * Creates a simple API call request for endpoints without parameters.
     */
    private ApiCallRequest createSimpleApiCallRequest(String apiName, String endpointName, String conversationId) {
        UnifiedApiDocument api = unifiedApiService.findByName(apiName)
            .block(Duration.ofSeconds(5));
            
        if (api == null) {
            throw new IllegalArgumentException("API not found: " + apiName);
        }
        
        UnifiedEndpoint endpoint = findEndpointInApi(api, endpointName);
        
        return ApiCallRequest.builder()
            .apiName(apiName)
            .endpoint(endpointName)
            .path(endpoint.getPath())
            .httpMethod("GET")
            .conversationId(conversationId)
            .parameters(new HashMap<>())
            .build();
    }

    /**
     * Creates API call request with parameters.
     */
    private ApiCallRequest createApiCallRequestWithParams(
            String apiName, String endpointName, Map<String, Object> paramMap, String conversationId) {
        
        UnifiedApiDocument api = unifiedApiService.findByName(apiName)
            .block(Duration.ofSeconds(5));
            
        if (api == null) {
            throw new IllegalArgumentException("API not found: " + apiName);
        }
        
        UnifiedEndpoint endpoint = findEndpointInApi(api, endpointName);
        
        return ApiCallRequest.builder()
            .apiName(apiName)
            .endpoint(endpointName)
            .path(endpoint.getPath())
            .httpMethod(endpoint.getMethod())
            .conversationId(conversationId)
            .parameters(paramMap)
            .build();
    }

    private Map<String, Object> parseJsonParameters(String jsonParams) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> paramMap = objectMapper.readValue(jsonParams, Map.class);
            return paramMap;
        } catch (Exception e) {
            log.warn("Failed to parse JSON parameters: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    private UnifiedEndpoint findEndpointInApi(UnifiedApiDocument api, String endpointName) {
        if (api.getEndpoints() == null || api.getEndpoints().isEmpty()) {
            throw new IllegalArgumentException("No endpoints in API: " + api.getName());
        }
        
        Optional<UnifiedEndpoint> endpointOpt = api.getEndpoints().stream()
            .filter(e -> e.getOperationId().equals(endpointName))
            .findFirst();
        
        if (endpointOpt.isEmpty()) {
            String availableEndpoints = api.getEndpoints().stream()
                .map(UnifiedEndpoint::getOperationId)
                .limit(10)
                .collect(Collectors.joining(", "));
            
            throw new IllegalArgumentException(String.format(
                "Endpoint '%s' not found. Available: [%s]", endpointName, availableEndpoints));
        }
        
        return endpointOpt.get();
    }

    /**
     * Executes the actual API call.
     */
    private ApiCallResponse executeApiCall(ApiCallRequest request) {
        try {
            if (apiConfigurationService.isLoggingEnabled(request.getApiName())) {
                log.info("Starting API call: {} {}", 
                    request.getApiName(), request.getEndpoint());
            }
            
            ApiCallResponse response = apiExecutionService.executeApiCall(request)
                .block(ACTION_TIMEOUT);
            
            if (apiConfigurationService.isLoggingEnabled(request.getApiName())) {
                log.info("API call completed: {} in {}ms", 
                    request.getApiName(), response.getExecutionTimeMs());
            }
            
            return response;
            
        } catch (Exception error) {
            log.warn("API call failed, using fallback: {}", error.getMessage());
            return createFallbackResponse(request, error);
        }
    }

    private ApiCallResponse createFallbackResponse(ApiCallRequest request, Throwable error) {
        return ApiCallResponse.failure(
            "API execution failed: " + error.getMessage(),
            503,
            request.getConversationId()
        ).withApiDetails(request.getApiName(), request.getEndpoint(), request.getHttpMethod())
         .withExecutionTime(0L);
    }

    /**
     * Formats API call result and marks that we have data.
     */
    private String formatApiCallResult(ApiCallResponse response, ReActContext context) {
        StringBuilder result = new StringBuilder();
        
        if (response.isSuccess()) {
            result.append("✅ API call successful:\n");
            result.append(String.format("📊 Data received:\n%s\n", 
                formatResponseData(response.getResponseAsString())));
            
            // Mark that we have real API data
            context.addMetadata("hasApiData", true);
        } else {
            result.append(String.format("❌ API error: %s (status: %d)\n", 
                response.getErrorMessage(), response.getStatusCode()));
        }
        
        return result.toString();
    }

    private String formatResponseData(String jsonResponse) {
        try {
            // Pretty print JSON for readability
            Object json = objectMapper.readValue(jsonResponse, Object.class);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
        } catch (Exception e) {
            return jsonResponse;
        }
    }

    /**
     * Validates and executes final answer action.
     */
    private String executeFinalAnswer(String answer, ReActContext context) {
        String cleanAnswer = answer.trim();
        
        // Check if API data was needed and obtained
        boolean hasApiData = context.getMetadata().getOrDefault("hasApiData", false);
        
        if (!hasApiData) {
            return "⚠️ Cannot provide final answer without getting data first. Use call_api.";
        }
        
        if (cleanAnswer.isEmpty()) {
            return "❌ Final answer cannot be empty";
        }
        
        String naturalResponse = generateNaturalLanguageResponse(cleanAnswer, context);
        context.setFinalAnswer(naturalResponse).markComplete();
        context.getMetadata().put("finalAnswerProvided", true);
        return "✅ Final answer provided successfully. Task completed.";
    }

    /**
     * Generate natural language response from JSON data
     */
    private String generateNaturalLanguageResponse(String jsonData, ReActContext context) {
        String prompt = String.format("""
            Convert this JSON data into a natural, user-friendly response in Spanish.
            
            Original user query: "%s"
            
            JSON data to format:
            %s
            
            Instructions:
            - Provide a clear, conversational response
            - Format the data in an easy-to-read way
            - Use bullet points or numbered lists where appropriate
            - Include all relevant information from the JSON
            - Do NOT return JSON, return natural language
            """, 
            context.getUserQuery(), 
            jsonData
        );
        
        try {
            return springAIService.chat(
                "You are a helpful assistant that converts technical data into user-friendly responses.",
                prompt,
                context.getConversationId()
            ).block(Duration.ofSeconds(10));
        } catch (Exception e) {
            // Fallback to a basic formatted response
            return formatResponseData(jsonData);
        }
    }

    // ========== OBSERVATION PHASE ==========

    /**
     * Generates observation based on action result.
     */
    private void generateObservation(ReActContext context, ReActStep step) {
        log.debug("👁️ Generating observation for step {}", step.getStepNumber());
        
        // 🆕 CHECK IF FINAL ANSWER WAS ALREADY PROVIDED
        if (context.getMetadata().getOrDefault("finalAnswerProvided", false)) {
            log.info("🏁 Final answer already provided, skipping observation generation");
            step.setObservation("Task completed - Final answer provided");
            return;
        }
        
        String actionResult = step.getActionResult();
        if (actionResult == null) {
            step.setObservation("⚠️ No action result available");
            return;
        }
        
        try {      	
        	String observationPrompt = createObservationPrompt(context);
        	
            String observation = springAIService.chat(
                "You are analyzing action results. Be decisive about next steps.",
                observationPrompt,
                context.getConversationId()
            ).block(OBSERVATION_TIMEOUT);
            
            if (observation == null) {
                throw new RuntimeException("Failed to generate observation");
            }
            
            step.setObservation(observation);
            log.debug("👁️ OBSERVATION: {}", observation);
            
        } catch (Exception e) {
            log.error("❌ Error generating observation: {}", e.getMessage());
            step.setObservation("Error generating observation: " + e.getMessage());
        }
    }

    private String createObservationPrompt(ReActContext context) {
        boolean hasApiData = context.getMetadata().getOrDefault("hasApiData", false);
        
        return String.format("""
            🔍 ANALYZE RESULT:
            
            📋 Action taken: %s
            📊 Result: %s
            🎯 Original query: "%s"
            
            ❓ DECISION:
            %s
            
            If you have the complete data, use: ACTION: final_answer <response with data>
            If you need more information, specify the next action.
            """, 
            context.getLastAction(), 
            context.getLastActionResult(), 
            context.getUserQuery(),
            hasApiData ? 
                "✅ You have API data. Provide the final answer with this data." :
                "⚠️ You haven't obtained real data yet. You need to call an API."
        );
    }

    // ========== RESPONSE BUILDING ==========

    /**
     * Builds the final ReAct response.
     */
    private ChatResponse buildReActResponse(
            ReActContext context, Instant startTime, ClassificationResponse classification) {
        long processingTime = Duration.between(startTime, Instant.now()).toMillis();
        
        String content = formatReActContent(context);
        List<String> reasoning = context.getStepHistory();
        
        return ChatResponse.builder()
            .content(content)
            .conversationId(context.getConversationId())
            .reasoning(reasoning)
            .iterations(context.getIterations())
            .success(context.isComplete())
            .processingType("REACT")
            .processingTimeMs(processingTime)
            .confidenceScore(classification.getConfidence())
            .metadata(Map.of(
                "classificationReasoning", classification.getReasoning(),
                "totalSteps", reasoning.size(),
                "finalAnswerProvided", context.getFinalAnswer() != null,
                "apiDataObtained", context.getMetadata().getOrDefault("hasApiData", false)
            ))
            .build();
    }

    private String formatReActContent(ReActContext context) {
        StringBuilder content = new StringBuilder();
        
        if (context.getFinalAnswer() != null) {
            content.append(context.getFinalAnswer());
        } else {
            content.append("⚠️ I couldn't complete the analysis within the iteration limit.\n\n");
            content.append("Partial results:\n");
            context.getStepHistory().forEach(step -> content.append("• ").append(step).append("\n"));
        }
        
        return content.toString();
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
            ).block(Duration.ofSeconds(30));
            
            long processingTime = Duration.between(startTime, Instant.now()).toMillis();
            
            return ChatResponse.builder()
                .content(response)
                .conversationId(request.getConversationId())
                .reasoning(List.of("Direct response - no API calls needed"))
                .iterations(0)
                .success(true)
                .processingType("DIRECT")
                .processingTimeMs(processingTime)
                .confidenceScore(0.9)
                .build();
                
        } catch (Exception e) {
            return ChatResponse.error(
                "Failed to generate direct response: " + e.getMessage(), 
                request.getConversationId());
        }
    }

    // ========== ERROR HANDLING ==========

    private boolean isRetryableError(Throwable error) {
        return error instanceof java.net.ConnectException ||
               error instanceof java.util.concurrent.TimeoutException ||
               (error.getMessage() != null && error.getMessage().contains("timeout"));
    }

    private void handleStepError(Throwable error, ReActContext context, ReActStep step) {
        log.warn("❌ ReAct step {} failed: {}", step.getStepNumber(), error.getMessage());
        step.withAction(new ActionCommand("ERROR", error.getMessage()));
        step.withObservation("Step failed: " + error.getMessage());
        context.addActionResult("❌ Step failed: " + error.getMessage());
    }

    private void handleActionError(Throwable error, ReActContext context, ReActStep step) {
        log.warn("❌ Action execution failed: {}", error.getMessage());
        step.withAction(new ActionCommand("ERROR", error.getMessage()));
        context.addActionResult("❌ Action failed: " + error.getMessage());
    }

    private ChatResponse handleReActError(
            Throwable error, ReActContext context, String conversationId) {
        log.error("❌ ReAct flow failed: {}", error.getMessage(), error);
        
        return ChatResponse.error(
            "ReAct processing failed: " + error.getMessage(), conversationId)
            .addMetadata("partialReasoning", context.getStepHistory())
            .addMetadata("completedIterations", context.getIterations());
    }

    private ChatResponse handleGlobalError(Throwable error, String conversationId) {
        log.error("❌ Global error in chat processing: {}", error.getMessage(), error);
        
        return ChatResponse.error(
            "An unexpected error occurred during processing", conversationId)
            .addMetadata("errorType", error.getClass().getSimpleName())
            .addMetadata("errorMessage", error.getMessage());
    }

    // ========== LOGGING HELPERS ==========

    private void logSuccess(ChatRequest request, Instant startTime) {
		long duration = Duration.between(startTime, Instant.now()).toMillis();
		log.info("✅ Query processed successfully in {}ms for conversation: {}", duration, request.getConversationId());
	}

	private void logError(ChatRequest request, Throwable error) {
		log.error("❌ Error processing query '{}' for conversation {}: {}", request.getQuery(),
				request.getConversationId(), error.getMessage());
	}
	
}
