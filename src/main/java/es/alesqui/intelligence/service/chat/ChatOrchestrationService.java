package es.alesqui.intelligence.service.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import es.alesqui.intelligence.config.ChatConfig;
import es.alesqui.intelligence.dto.chat.request.ChatRequest;
import es.alesqui.intelligence.dto.chat.response.ChartData;
import es.alesqui.intelligence.dto.chat.response.ChatResponse;
import es.alesqui.intelligence.dto.chat.response.SseEvent;
import es.alesqui.intelligence.security.SecurityUtils;
import es.alesqui.intelligence.service.conversation.ChatMemoryService;
import es.alesqui.intelligence.service.conversation.ConversationService;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Asynchronous orchestration service implementing the ReAct pattern for chat-based API interactions.
 * Handles query classification, API execution, and natural language response generation in a non-blocking manner.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChatOrchestrationService {

    private final SpringAIService springAIService;
    private final ChatConfig chatConfig;
    private final ConversationService conversationService;
    private final ChatMemoryService chatMemoryService;
    private final ChatMemory chatMemory;

    /**
     * Main entry point for processing chat requests using a streaming approach.
     *
     * This method is fully non-blocking and orchestrates the entire chat workflow. It returns
     * a Flux of Server-Sent Events (SseEvent) that the client can subscribe to. The stream
     * is carefully constructed to first deliver all real-time status updates and then, only
     * after those are complete, deliver the final response or a final error message.
     *
     * This is achieved by using Flux.concat to sequentially chain the status update stream
     * with the final response stream, which robustly prevents race conditions where the final
     * message could arrive before all status updates have been sent.
     *
     * @param request The incoming chat request, containing the user's query and conversation ID.
     * @return A Flux of SseEvent that emits real-time status updates, followed by either a
     * single final response event or a single error event before completing.
     */
	public Flux<SseEvent> processQuery(ChatRequest request) {
		log.info("🚀 Processing chat request: '{}' for conversation: {}", request.getQuery(),
				request.getConversationId());

		return SecurityUtils.getCurrentUsername().switchIfEmpty(Mono.just("anonymous_fallback"))
				.flatMapMany(username -> {
					Sinks.Many<SseEvent> sink = Sinks.many().multicast().onBackpressureBuffer();

					// 1. Prepare the Mono that performs all the background work and returns the
					// final response.
					Mono<ChatResponse> processingChain = processWithUsername(request, username, sink)
							.subscribeOn(Schedulers.boundedElastic()) // Run the entire processing chain on a separate
																		// scheduler.
							.cache();

					// 2. Define the stream for the status update events from the sink.
					// A delay is applied to each element to ensure they are readable in the client
					// UI.
					Flux<SseEvent> statusUpdates = sink.asFlux().delayElements(Duration.ofMillis(700));

					// 3. Define the stream that will contain only the single, final response.
					// This is created by mapping the result of the processing chain.
					Flux<SseEvent> finalResponse = processingChain.map(SseEvent::finalResponse).flux();

					// 4. Merge the streams to run them in parallel.
					// `merge` is the key to breaking the deadlock. It subscribes to `statusUpdates`
					// and `finalResponse` at the same time, allowing the background process to
					// start immediately while the status stream is already listening for events.
					return Flux.merge(statusUpdates, finalResponse).onErrorResume(error -> {
						// If an error occurs at ANY point in either the processing or status streams,
						// gracefully resume with a single, user-friendly error event.
						return Flux.just(SseEvent.error("An unexpected error occurred: " + error.getMessage()));
					});
				});
	}

    /**
     * Orchestrates the core logic for processing a chat request for a specific user.
     *
     * This method is responsible for the entire reactive chain that generates a response.
     * It ensures chat history is loaded, classifies the query, routes it to the appropriate
     * handler (tools or direct), and saves the final interaction to the database.
     *
     * Crucially, it does not emit the final response to the sink itself. Instead, it returns
     * a Mono containing the final ChatResponse and signals the completion of the status sink
     * via the doOnTerminate operator. This allows the calling method to concatenate the
     * status stream and the final response stream safely.
     *
     * @param request The incoming chat request from the user.
     * @param username The username of the user making the request.
     * @param sink The Sinks.Many instance used to emit real-time status updates (SseEvent).
     * @return A Mono<ChatResponse> that will emit the final, generated chat response
     * upon successful completion of all processing steps. It will emit an error
     * if any part of the chain fails.
     */
    private Mono<ChatResponse> processWithUsername(ChatRequest request, String username, Sinks.Many<SseEvent> sink) {
        return Mono.defer(() -> {
            // First, ensure the chat history is loaded into memory if it's not already present.
            Mono<Void> memoryLoader = chatMemory.get(request.getConversationId()).isEmpty()
                    ? chatMemoryService.loadHistoryIntoMemory(request.getConversationId())
                    : Mono.empty();

            // This Mono represents the core logic for generating a response.
            Mono<ChatResponse> responseGenerator = Mono.just(request)
                    .doOnNext(this::validateRequest)
                    .flatMap(req -> executeToolBasedResponse(request, sink));

            return memoryLoader
                .then(responseGenerator)
                // WHEN THE MONO TERMINATES (on success or error), COMPLETE THE STATUS SINK.
                // This is a critical step to signal that the status update stream is finished,
                // allowing Flux.concat in the calling method to proceed to the final response stream.
                .doOnTerminate(() -> {
                    log.debug("Processing finished, completing status sink.");
                    sink.tryEmitComplete();
                })
                .flatMap(response -> {
                    // SUCCESS PATH: Log the success, save the interaction, and then pass the response through.
                    logSuccess(response, response.getProcessingTimeMs());
                    return conversationService.saveInteraction(request, response, username)
                        .thenReturn(response); // Important: We return the original response to continue the chain.
                })
                .doOnError(error -> {
                    // Log the error as soon as it occurs in the chain.
                    logError(request, error);
                })
                .onErrorResume(error -> {
                    // ERROR PATH: Save the failed interaction to the database, and then propagate the
                    // error so the main stream handler can convert it into a final error event.
                    return conversationService.saveFailedInteraction(request, error, username)
                        .then(Mono.error(error));
                });
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
     * Executes the tool-based (ReAct) chat flow.
     *
     * This involves calling an AI model that can use a predefined set of tools
     * to answer the user's query. It passes a sink to the underlying AI service
     * to emit real-time status updates.
     *
     * @param request The original chat request.
     * @return A Mono emitting the final ChatResponse after tool execution.
     */
    private Mono<ChatResponse> executeToolBasedResponse(ChatRequest request, Sinks.Many<SseEvent> sink) {
        Instant startTime = Instant.now();
        
        // Get the current date and format it.
        String currentDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE); // e.g., "2025-10-12"
        
        String systemPromptTemplate = """
            You are a friendly, conversational, and highly efficient AI assistant named 'Alesqui'. Your purpose is to help users by interacting with the available APIs.
            
            **CRITICAL CONTEXT:**
        	- **Today's Date is: %s**. You MUST use this date to resolve any relative date queries like 'today', 'yesterday', 'last quarter', etc.
           
            **Your Personality and Communication Style:**
            1.  **Friendly Tone:** Start with a suitable greeting and maintain a helpful, approachable tone.
            2.  **Clarity:** Summarize results in a clear and friendly manner.
            3.  **Polite Closing:** End your response in a helpful way.
            4.  **Language:** Always communicate in the user's language.
            
            ---
            
            **Your Guiding Principles for Using Tools (Your Internal Logic):**
            1.  **Think Step-by-Step:** Break down the request into a logical sequence of steps.
            2.  **Use Tools Intelligently:** Always use `list_apis` first to discover available APIs and endpoints before trying to call them.
            3.  **Be Resourceful:** If a tool call fails, analyze the error, correct your approach, and try again.
            4.  **Stay Focused:** Only use the provided tools. Do not invent tools or parameters.
            
            ---

            **Tool Reference:**
            - `list_apis()`: Lists all available APIs.
            - `list_endpoints(apiName)`: Lists all operations for a specific API.
            - `call_api(apiName, operationId, parameters)`: Executes a specific API operation.
            - `process_data(jsonData, operation, filterExpression, groupByKey, valueKey, dateKey, datePart)`: Analyzes JSON data.
                - `operation`: 'COUNT', 'FILTER', 'GROUP_BY_COUNT', 'GROUP_BY_DATE_PART_COUNT', 'SUM', 'AVERAGE'.
                - `filterExpression`: Supports nested keys (e.g., "shippingAddress.city=='Madrid'") and operators (==, !=, >, <, >=, <=).
                - `groupByKey`: The key to group by. Also used for SUM/AVERAGE.
                - `valueKey`: The key containing the number to be summed or averaged.
            - `create_excel_file(jsonData, filename)`: Generates an Excel file.
            - `create_chart(chartType, jsonData, labelKey, dataKey, datasetLabel)`: Generates a chart configuration.
            
            **Workflow for Data Analysis:**
            1.  First, obtain the raw data using `call_api`.
            2.  Then, use `process_data` on the result of `call_api` to answer the user's specific question (e.g., counting, filtering, averaging). You can filter and group in a single step.
            
            **Workflow for Creating Charts:**
            1.  Obtain the necessary data using `call_api`.
            2.  If needed, transform the data using `process_data` to group it correctly.
            3.  Call `create_chart` with the transformed data.
            4.  **CRITICAL:** After `create_chart` is called, your task is complete. Your final answer must be a brief summary.
            5.  **DO NOT** include the raw JSON chart configuration in your final response.
            """;
        
        String systemPrompt = String.format(systemPromptTemplate, currentDate);
            
        return springAIService.chatWithTools(systemPrompt, request.getQuery(), request.getConversationId(), request.isIncludeReasoning(), sink)
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