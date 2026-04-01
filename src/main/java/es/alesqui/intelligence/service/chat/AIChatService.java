package es.alesqui.intelligence.service.chat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Service;

import es.alesqui.intelligence.dto.chat.reasoning.ReasoningStep;
import es.alesqui.intelligence.dto.chat.response.ChartData;
import es.alesqui.intelligence.dto.chat.response.ChatWithReasoningResponse;
import es.alesqui.intelligence.dto.chat.response.SseEvent;
import es.alesqui.intelligence.dto.chat.response.ToolResponseProcessingResult;
import es.alesqui.intelligence.service.chat.tools.ApiDiscoveryTools;
import es.alesqui.intelligence.service.chat.tools.ApiInvocationTools;
import es.alesqui.intelligence.service.chat.tools.ChartTools;
import es.alesqui.intelligence.service.chat.tools.DataTools;
import es.alesqui.intelligence.service.chat.tools.ExportTools;
import es.alesqui.intelligence.service.chat.tools.ToolContextKeys;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

/**
 * Service for managing interactions with an AI chat model. Provides
 * functionalities for maintaining conversation history, extracting structured
 * data, and interacting with the model with or without memory.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AIChatService {

	private final ChatClient chatClient;
	private final ApiDiscoveryTools apiDiscoveryTools;
	private final ApiInvocationTools apiInvocationTools;
	private final DataTools dataTools;
	private final ExportTools exportTools;
	private final ChartTools chartTools;
	private final ChatMemory chatMemory;
	private final MeterRegistry meterRegistry;
	private final es.alesqui.intelligence.service.identity.UserService userService;
	private final ChatPromptBuilder chatPromptBuilder;
	private final ToolResponseProcessor toolResponseProcessor;
	private final HallucinationCorrector hallucinationCorrector;

	/**
	 * Validates input parameters to ensure they are not null or empty.
	 *
	 * @param inputs Array of input strings to validate.
	 * @throws IllegalArgumentException if any input is null or empty.
	 */
	private void validateInputs(String... inputs) {
		for (String input : inputs) {
			if (StringUtils.isBlank(input)) {
				throw new IllegalArgumentException("Input cannot be null or empty");
			}
		}
	}

	/**
	 * Initiates a reactive, stateful chat conversation turn with the AI model.
	 *
	 * This method leverages the configured ChatMemory to provide the AI with the
	 * context of previous messages for the given conversation ID. The process is
	 * fully non-blocking from start to finish.
	 *
	 * As a side effect, this method updates the conversation history by first
	 * adding the user's message, and then adding the assistant's response upon
	 * successful completion of the AI call.
	 *
	 * @param systemPrompt   The system-level instructions that define the AI's
	 *                       behavior and persona.
	 * @param userPrompt     The user's current message for this turn of the
	 *                       conversation.
	 * @param conversationId The unique identifier for the conversation, used to
	 *                       retrieve and update its history.
	 * @return A reactive Mono that, upon completion, will emit the AI model's
	 *         textual response as a String.
	 */
	public Mono<String> chat(String systemPrompt, String userPrompt, String conversationId) {
		validateInputs(userPrompt, conversationId);
		log.debug("Processing reactive chat request for conversation: {}", conversationId);
		long startTime = System.currentTimeMillis();

		chatMemory.add(conversationId, new UserMessage(userPrompt));
		List<Message> history = chatMemory.get(conversationId);

		return chatClient.prompt().system(systemPrompt).messages(history).stream()
				.content()
				.collect(Collectors.joining())
				.doOnNext(responseContent -> {
					chatMemory.add(conversationId, new AssistantMessage(responseContent));
				}).doOnSuccess(response -> {
					log.info("Successfully processed chat for conversation: {}", conversationId);
					meterRegistry.timer("chat.execution.time").record(System.currentTimeMillis() - startTime,
							TimeUnit.MILLISECONDS);
				}).doOnError(error -> log.error("Failed to process chat for conversation {}: {}", conversationId,
						error.getMessage()));
	}

	/**
	 * Extracts structured data from an AI model response in a fully reactive and
	 * non-blocking manner.
	 *
	 * @param <T>         The generic type of the structured data to be extracted.
	 * @param systemPrompt The system-level instructions that guide the AI's behavior.
	 * @param userPrompt   The user's query or instructions for the data extraction task.
	 * @param targetClass  The class type to which the AI's response should be mapped.
	 * @return A reactive Mono that will emit the structured data as an instance of
	 *         the target class.
	 */
	public <T> Mono<T> extractStructuredData(String systemPrompt, String userPrompt, Class<T> targetClass) {
		validateInputs(systemPrompt, userPrompt);
		long startTime = System.currentTimeMillis();
		log.info("Extracting structured data for class: {}", targetClass.getSimpleName());

		var converter = new BeanOutputConverter<>(targetClass);
		String finalUserPrompt = String.format("%s\n\n%s", userPrompt, converter.getFormat());

		return chatClient.prompt().system(systemPrompt).user(finalUserPrompt).stream().content()
				.collect(Collectors.joining()).map(rawResponse -> {
					return converter.convert(rawResponse);
				}).doOnSuccess(result -> {
					long duration = System.currentTimeMillis() - startTime;
					log.info("Successfully extracted data for class: {} in {}ms", targetClass.getSimpleName(),
							duration);
					meterRegistry
							.timer("ai.extraction.duration", "class", targetClass.getSimpleName(), "status", "success")
							.record(duration, TimeUnit.MILLISECONDS);
				}).doOnError(error -> {
					long duration = System.currentTimeMillis() - startTime;
					log.error("Error extracting structured data for class: {}", targetClass.getSimpleName(), error);
					meterRegistry
							.timer("ai.extraction.duration", "class", targetClass.getSimpleName(), "status", "error")
							.record(duration, TimeUnit.MILLISECONDS);
				});
	}

	/**
	 * Initiates a stateless chat session without memory, ideal for utility tasks.
	 * This method is fully non-blocking.
	 *
	 * @param systemPrompt Instructions for the AI model that define its behavior
	 *                     for this specific task.
	 * @param userPrompt   The user's message or data to be processed by the AI.
	 * @return A reactive Mono containing the single, aggregated AI model's response
	 *         as a string.
	 */
	public Mono<String> chatWithoutMemory(String systemPrompt, String userPrompt) {
		validateInputs(systemPrompt, userPrompt);
		long start = System.currentTimeMillis();
		return chatClient.prompt()
				.system(systemPrompt)
				.user(userPrompt)
				.stream()
				.content()
				.collect(Collectors.joining())
				.doOnSuccess(response -> {
					long duration = System.currentTimeMillis() - start;
					log.info("chatWithoutMemory completed in {} ms", duration);
					meterRegistry.timer("ai.chat.without.memory.duration", "status", "success")
							.record(duration, TimeUnit.MILLISECONDS);
				})
				.timeout(java.time.Duration.ofSeconds(30))
				.doOnError(err -> {
					long duration = System.currentTimeMillis() - start;
					log.error("chatWithoutMemory error after {} ms: {}", duration, err.getMessage());
					meterRegistry.timer("ai.chat.without.memory.duration", "status", "error")
							.record(duration, TimeUnit.MILLISECONDS);
				});
	}

	/**
	 * Manages a multi-turn conversation with the AI and external tools.
	 * This method implements the ReAct (Reason-Act) pattern by manually controlling
	 * a loop that alternates between AI reasoning and tool execution.
	 *
	 * The entire process is wrapped in a reactive Mono but executes its blocking
	 * logic on a dedicated scheduler to avoid blocking the main application threads.
	 *
	 * @param baseSystemPrompt The core system instructions defining the AI's behavior and goals.
	 * @param userPrompt       The user's current message to be processed.
	 * @param conversationId   A unique identifier to retrieve and update the conversation's history.
	 * @param includeReasoning A flag to determine if the structured reasoning steps
	 *                         should be included in the response.
	 * @param statusSink       A sink to push real-time status updates (SseEvent) to the client.
	 * @param username         The username of the authenticated user (used to resolve userId for tool context).
	 * @return A reactive Mono that emits a ChatWithReasoningResponse containing the
	 *         final AI message, an optional list of structured reasoning steps, and any generated chart data.
	 */
	public Mono<ChatWithReasoningResponse> chatWithTools(String baseSystemPrompt, String userPrompt,
			String conversationId, boolean includeReasoning, Sinks.Many<SseEvent> statusSink, String username) {

		validateInputs(userPrompt);

		return ReactiveSecurityContextHolder.getContext()
				.defaultIfEmpty(new org.springframework.security.core.context.SecurityContextImpl())
				.map(securityContext -> chatPromptBuilder.buildPromptWithRole(baseSystemPrompt, securityContext.getAuthentication()))
				.flatMap(systemPrompt -> Mono.fromCallable(() -> {
					String userId = userService.getUserIdByUsernameBlocking(username, java.time.Duration.ofSeconds(5));

					if (userId != null) {
						log.debug("Resolved userId {} for username: {}", userId, username);
					} else {
						log.warn("User not found for username: {}", username);
					}

					log.info("Processing chat with tools for conversation: {} with userId: {}", conversationId,
							userId != null ? userId : "null");
					long startTime = System.currentTimeMillis();

					ReasoningStepCollector reasoningCollector = new ReasoningStepCollector();
					ChartData capturedChartData = null;

					try {
						statusSink.tryEmitNext(SseEvent.status("Analyzing request and planning steps..."));

						// --- Setup for Tool Calling ---
						ToolCallback[] toolCallbacks = ToolCallbacks.from(
								apiDiscoveryTools, apiInvocationTools, dataTools, exportTools, chartTools);
						ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder().build();

						ChatOptions chatOptions = ToolCallingChatOptions.builder()
								.toolCallbacks(toolCallbacks)
								.internalToolExecutionEnabled(false)
								.build();

						// --- Conversation History Management ---
						chatMemory.add(conversationId, new UserMessage(userPrompt));
						List<Message> history = chatMemory.get(conversationId);

						List<Message> turnHistory = new ArrayList<>();
						turnHistory.add(new SystemMessage(systemPrompt));
						turnHistory.addAll(history);

						// --- Tool Context ---
						Map<String, Object> contextMap = new HashMap<>();
						contextMap.put(ToolContextKeys.SSE_SINK, statusSink);
						contextMap.put(ToolContextKeys.CONVERSATION_ID, conversationId);
						contextMap.put(ToolContextKeys.USERNAME, username);
						if (userId != null && !userId.isEmpty()) {
							contextMap.put(ToolContextKeys.USER_ID, userId);
							log.debug("Added userId to tool context: {}", userId);
						} else {
							log.warn("No userId available for tool context in conversation: {}", conversationId);
						}
						ToolContext toolContext = new ToolContext(contextMap);

						statusSink.tryEmitNext(SseEvent.status("Thinking..."));

						// --- First AI Call ---
						Prompt currentPrompt = new Prompt(new ArrayList<>(turnHistory), chatOptions);
						ChatResponse chatResponse = chatClient.prompt(currentPrompt)
								.toolContext(toolContext.getContext())
								.call()
								.chatResponse();

						chatResponse = ensureChatResponse(chatResponse, "initial call");
						turnHistory.add(chatResponse.getResult().getOutput());
						chatResponse = hallucinationCorrector.correctIfHallucinated(
								chatResponse, turnHistory, chatClient, chatOptions, toolContext, statusSink);

						// --- Main ReAct (Reason-Act) Loop ---
						while (chatResponse.hasToolCalls()) {
							log.debug("AI requested tool execution.");

							// Capture reasoning: thought and tool calls
							reasoningCollector.captureThought(chatResponse);
							reasoningCollector.captureToolCalls(chatResponse.getResult().getOutput());

							// Execute tools
							long startTimeTool = System.currentTimeMillis();
							ToolExecutionResult toolExecutionResult = toolCallingManager
									.executeToolCalls(currentPrompt, chatResponse);
							Message originalToolResponseMessage = toolExecutionResult.conversationHistory()
									.get(toolExecutionResult.conversationHistory().size() - 1);
							long totalTimeTool = System.currentTimeMillis() - startTimeTool;

							statusSink.tryEmitNext(SseEvent.status("Tool execution finished. Analyzing results..."));

							// Process tool responses
							ToolResponseProcessingResult processed = toolResponseProcessor.processToolResponses(
									originalToolResponseMessage, reasoningCollector.getPendingToolCalls(), totalTimeTool);
							if (processed.chartData() != null) {
								capturedChartData = processed.chartData();
							}
							turnHistory.add(processed.cleanedMessageForHistory());

							// Next AI call
							currentPrompt = new Prompt(new ArrayList<>(turnHistory), chatOptions);
							chatResponse = chatClient.prompt(currentPrompt)
									.toolContext(toolContext.getContext())
									.call()
									.chatResponse();
							chatResponse = ensureChatResponse(chatResponse, "loop next call");
							turnHistory.add(chatResponse.getResult().getOutput());
							chatResponse = hallucinationCorrector.correctIfHallucinated(
									chatResponse, turnHistory, chatClient, chatOptions, toolContext, statusSink);
						}

						// --- Finalize the Turn ---
						chatMemory.add(conversationId, chatResponse.getResult().getOutput());
						statusSink.tryEmitNext(SseEvent.status("Formatting final answer..."));
						reasoningCollector.captureFinalResponse(chatResponse);

						long totalTime = System.currentTimeMillis() - startTime;
						log.info("Successfully processed chat with tools for conversation: {} in {}ms",
								conversationId, totalTime);
						meterRegistry.timer("ai.chat.with.tools.duration", "status", "success").record(totalTime,
								TimeUnit.MILLISECONDS);

						List<ReasoningStep> reasoningSteps = reasoningCollector.getSteps();
						return new ChatWithReasoningResponse(chatResponse, includeReasoning ? reasoningSteps : null,
								capturedChartData);

					} catch (Exception e) {
						log.error("Error processing chat with tools for conversation {}: {}", conversationId,
								e.getMessage(), e);
						meterRegistry.timer("ai.chat.with.tools.duration", "status", "error")
								.record(System.currentTimeMillis() - startTime, TimeUnit.MILLISECONDS);
						statusSink.tryEmitNext(SseEvent.error("An error occurred during tool processing."));
						String fallbackMessage = "Sorry, an error occurred: " + e.getMessage();
						if (includeReasoning) {
							reasoningCollector.captureError(fallbackMessage);
						}
						List<ReasoningStep> reasoningSteps = reasoningCollector.getSteps();
						ChatWithReasoningResponse response = new ChatWithReasoningResponse(null,
								includeReasoning ? reasoningSteps : null, capturedChartData);
						response.setFallbackContent(fallbackMessage);
						return response;
					}
				}).subscribeOn(Schedulers.boundedElastic()));
	}

	/**
	 * Tests the connection to the AI model by sending a predefined prompt in a
	 * non-blocking way.
	 *
	 * @return A reactive Mono containing the AI model's response as a string.
	 */
	public Mono<String> testConnection() {
		return chatClient.prompt().user("Say 'Connection test successful'").stream().content()
				.collect(Collectors.joining());
	}

	/**
	 * Ensures the AI model returned a complete response structure.
	 * Throws an IllegalStateException if any critical field is null.
	 */
	private ChatResponse ensureChatResponse(ChatResponse response, String stage) {
		Objects.requireNonNull(response, "AI returned null ChatResponse at " + stage);
		Objects.requireNonNull(response.getResult(), "AI returned null result at " + stage);
		Objects.requireNonNull(response.getResult().getOutput(), "AI returned null output at " + stage);
		return response;
	}
}
