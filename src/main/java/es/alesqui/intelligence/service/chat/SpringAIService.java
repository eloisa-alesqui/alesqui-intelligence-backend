package es.alesqui.intelligence.service.chat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import es.alesqui.intelligence.dto.chat.reasoning.ReasoningStep;
import es.alesqui.intelligence.dto.chat.reasoning.StepType;
import es.alesqui.intelligence.dto.chat.reasoning.ToolCallData;
import es.alesqui.intelligence.dto.chat.reasoning.ToolCallStatus;
import es.alesqui.intelligence.dto.chat.response.ApiCallResponse;
import es.alesqui.intelligence.dto.chat.response.ChartData;
import es.alesqui.intelligence.dto.chat.response.ChatWithReasoningResponse;
import es.alesqui.intelligence.dto.chat.response.SseEvent;
import es.alesqui.intelligence.service.chat.tools.ApiDiscoveryTools;
import es.alesqui.intelligence.service.chat.tools.ApiInvocationTools;
import es.alesqui.intelligence.service.chat.tools.ChartTools;
import es.alesqui.intelligence.service.chat.tools.DataTools;
import es.alesqui.intelligence.service.chat.tools.ExportTools;
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
public class SpringAIService {

	private final ChatClient chatClient;
	private final ApiDiscoveryTools apiDiscoveryTools;
	private final ApiInvocationTools apiInvocationTools;
	private final DataTools dataTools;
	private final ExportTools exportTools;
	private final ChartTools chartTools;
	private final ChatMemory chatMemory;
	private final MeterRegistry meterRegistry;
	private final ObjectMapper objectMapper;
	private final es.alesqui.intelligence.service.identity.UserService userService;

	private static final String ROLE_IT_INSTRUCTION = """
			IMPORTANT: You are speaking to a technical user (IT role). Be precise and use technical terminology.
			You can refer to APIs by name, endpoints by their operationId, and parameters by their technical type (e.g., string, integer).
			""";

	private static final String ROLE_BUSINESS_INSTRUCTION = """
			IMPORTANT: You are speaking to a business user. Use simple, non-technical language.
			Avoid jargon like 'API', 'endpoint', 'parameter', or data types.
			Translate technical concepts into business actions (e.g., instead of 'parameter name', say 'you need to provide a name').
			""";

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

		return chatClient.prompt().system(systemPrompt).messages(history).stream() // Use stream() for a reactive
																					// response
				.content() // This returns a Flux<String>
				.collect(Collectors.joining()) // Collect all parts of the stream into a Mono<String>
				.doOnNext(responseContent -> {
					// Add the AI's response to the history
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
	 * This method follows the documented Spring AI pattern for structured output
	 * with reactive streams. It makes a single call to the AI, instructing it to
	 * generate a JSON response that conforms to the schema of the target class. The
	 * resulting string is then parsed into the entity.
	 *
	 * As a side effect, this method updates the conversation history in ChatMemory
	 * with both the user's prompt and the raw string response from the AI before
	 * parsing.
	 *
	 * @param <T>            The generic type of the structured data to be
	 *                       extracted.
	 * @param systemPrompt   The system-level instructions that guide the AI's
	 *                       behavior.
	 * @param userPrompt     The user's query or instructions for the data
	 *                       extraction task.
	 * @param conversationId The unique identifier for the conversation context.
	 * @param targetClass    The class type to which the AI's response should be
	 *                       mapped.
	 * @return A reactive Mono that will emit the structured data as an instance of
	 *         the target class.
	 */
	public <T> Mono<T> extractStructuredData(String systemPrompt, String userPrompt, Class<T> targetClass) {
		validateInputs(systemPrompt, userPrompt);
		long startTime = System.currentTimeMillis();
		log.info("Extracting structured data for class: {}", targetClass.getSimpleName());

		// 1. Create a BeanOutputConverter for the target class.
		var converter = new BeanOutputConverter<>(targetClass);

		// 2. Prepare the user prompt, injecting the format instructions from the
		// converter.
		String finalUserPrompt = String.format("%s\n\n%s", userPrompt, converter.getFormat());

		// Note: For structured extraction, we typically don't send the whole history
		// to avoid confusing the model. We send only the specific request.

		// 3. Make a single, reactive call to get the raw string response.
		return chatClient.prompt().system(systemPrompt).user(finalUserPrompt).stream().content()
				.collect(Collectors.joining()).map(rawResponse -> {
					// 4. Use the converter to parse the string into the target entity.
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
	 * a loop
	 * that alternates between AI reasoning and tool execution.
	 *
	 * The entire process is wrapped in a reactive Mono but executes its blocking
	 * logic
	 * on a dedicated scheduler to avoid blocking the main application threads. It
	 * builds a
	 * structured, step-by-step trace of the AI's reasoning process for detailed
	 * frontend visualization.
	 *
	 * @param baseSystemPrompt The core system instructions defining the AI's
	 *                         behavior and goals.
	 * @param userPrompt       The user's current message to be processed.
	 * @param conversationId   A unique identifier to retrieve and update the
	 *                         conversation's history.
	 * @param includeReasoning A flag to determine if the structured reasoning steps
	 *                         should be included in the response.
	 * @param statusSink       A sink to push real-time status updates (SseEvent) to
	 *                         the client.
	 * @param username         The username of the authenticated user (used to
	 *                         resolve userId for tool context).
	 * @return A reactive Mono that emits a ChatWithReasoningResponse containing the
	 *         final AI message,
	 *         an optional list of structured reasoning steps, and any generated
	 *         chart data.
	 */
	public Mono<ChatWithReasoningResponse> chatWithTools(String baseSystemPrompt, String userPrompt,
			String conversationId, boolean includeReasoning, Sinks.Many<SseEvent> statusSink, String username) {

		// --- 1. Input Validation ---
		validateInputs(userPrompt);

		// --- 2. Offload Blocking Logic to a Separate Thread ---
		return ReactiveSecurityContextHolder.getContext()
				.defaultIfEmpty(new org.springframework.security.core.context.SecurityContextImpl())
				.map(securityContext -> buildPromptWithRole(baseSystemPrompt, securityContext.getAuthentication()))
				.flatMap(systemPrompt -> Mono.fromCallable(() -> {
					// Resolve userId from username (blocking operation, but we're in boundedElastic)
					String userId = userService.getUserIdByUsernameBlocking(username, java.time.Duration.ofSeconds(5));
					
					if (userId != null) {
						log.debug("Resolved userId {} for username: {}", userId, username);
					} else {
						log.warn("User not found for username: {}", username);
					}

					log.info("Processing chat with tools for conversation: {} with userId: {}", conversationId,
							userId != null ? userId : "null");
					long startTime = System.currentTimeMillis();

					// Capture reasoning and chart across success and failure paths
					List<ReasoningStep> reasoningSteps = new ArrayList<>();
					ChartData capturedChartData = null;

					try {
						// Emit an initial status update to the client.
						statusSink.tryEmitNext(SseEvent.status("Analyzing request and planning steps..."));

						// --- 3. Setup for Tool Calling ---
						// Prepare the tool callbacks and manager for manual execution.
						ToolCallback[] toolCallbacks = ToolCallbacks.from(
								apiDiscoveryTools,
								apiInvocationTools,
								dataTools,
								exportTools,
								chartTools);
						ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder().build();

						// Configure chat options to disable Spring AI's automatic tool execution,
						// as we are controlling the loop manually.
						ChatOptions chatOptions = ToolCallingChatOptions.builder()
								.toolCallbacks(toolCallbacks)
								.internalToolExecutionEnabled(false)
								.build();

						// --- 4. Conversation History Management ---
						// Add the current user message to persistent memory.
						chatMemory.add(conversationId, new UserMessage(userPrompt));
						// Retrieve the full, updated history for this turn.
						List<Message> history = chatMemory.get(conversationId);

						// Create a temporary message list for this turn's interaction with the AI.
						List<Message> turnHistory = new ArrayList<>();
						turnHistory.add(new SystemMessage(systemPrompt));
						turnHistory.addAll(history);

						// --- 5. Initialize Loop Variables ---
						ChatResponse chatResponse;

						// Prepare the context to be passed to the tools, including the SSE sink.
						Map<String, Object> contextMap = new HashMap<>();
						contextMap.put("sseSink", statusSink);
						contextMap.put("conversationId", conversationId);
						if (userId != null && !userId.isEmpty()) {
							contextMap.put("userId", userId); // Add userId for tool access
							log.debug("Added userId to tool context: {}", userId);
						} else {
							log.warn("No userId available for tool context in conversation: {}", conversationId);
						}
						ToolContext toolContext = new ToolContext(contextMap);

						statusSink.tryEmitNext(SseEvent.status("Thinking..."));

						// --- 6. First AI Call ---
						// Make the initial call to the AI model with the system prompt and full
						// history.
						Prompt currentPrompt = new Prompt(new ArrayList<>(turnHistory), chatOptions);
						chatResponse = chatClient.prompt(currentPrompt)
								.toolContext(toolContext.getContext())
								.call()
								.chatResponse();

						// Validate response and add assistant message to turn history
						chatResponse = ensureChatResponse(chatResponse, "initial call");
						turnHistory.add(chatResponse.getResult().getOutput());

						// If the model wrote an Act in text but did not emit a tool call, nudge once to
						// produce the tool call.
						chatResponse = enforceToolCallIfHallucinated(chatResponse, turnHistory, chatClient, chatOptions, toolContext, statusSink);

						// --- 7. Main ReAct (Reason-Act) Loop ---
						while (chatResponse.hasToolCalls()) {
							log.debug("AI requested tool execution.");
							AssistantMessage aiMsg = chatResponse.getResult().getOutput();

							// CAPTURE REASONING STEP 1: The AI's brief plan (fallback if model omits text)
							String planText = StringUtils.trimToEmpty(aiMsg.getText());
							if (StringUtils.isBlank(planText) && aiMsg.getToolCalls() != null
									&& !aiMsg.getToolCalls().isEmpty()) {
								// Model emitted only tool calls without a textual preamble. Synthesize a
								// concise plan.
								String toolNames = aiMsg.getToolCalls().stream()
										.map(ToolCall::name)
										.filter(Objects::nonNull)
										.distinct()
										.collect(Collectors.joining(", "));
								planText = "Plan: I'll call " + toolNames
										+ " to gather the necessary data, then answer concisely.";
							}
							if (StringUtils.isNotBlank(planText)) {
								reasoningSteps.add(new ReasoningStep(StepType.THOUGHT, planText));
							}

							// CAPTURE REASONING STEP 2: The tool call request.
							List<ToolCallData> pendingToolCalls = new ArrayList<>();
							if (aiMsg.getToolCalls() != null && !aiMsg.getToolCalls().isEmpty()) {
								for (ToolCall toolCall : aiMsg.getToolCalls()) {
									// Create a record of the tool call with its arguments (the request).
									pendingToolCalls.add(new ToolCallData(toolCall.name(), toolCall.arguments()));
								}
								reasoningSteps.add(new ReasoningStep(StepType.TOOL_CALL, pendingToolCalls));
							}

							// Execute the requested tools.

							long startTimeTool = System.currentTimeMillis();
							ToolExecutionResult toolExecutionResult = toolCallingManager
									.executeToolCalls(currentPrompt, chatResponse);
							Message originalToolResponseMessage = toolExecutionResult.conversationHistory()
									.get(toolExecutionResult.conversationHistory().size() - 1);
							long totalTimeTool = System.currentTimeMillis() - startTimeTool;

							statusSink
									.tryEmitNext(SseEvent.status("Tool execution finished. Analyzing results..."));

							List<ToolResponseMessage.ToolResponse> cleanedResponsesForAI = new ArrayList<>();
							Message finalMessageForHistory; // This will hold the message added to turnHistory

							// Process the results to update ToolCallData (for frontend) and prepare cleaned
							// response for AI
							if (originalToolResponseMessage instanceof ToolResponseMessage toolResponseMsg
									&& !pendingToolCalls.isEmpty()) {

								// Iterate through each tool response returned by the manager
								for (int i = 0; i < toolResponseMsg.getResponses().size(); i++) {
									ToolResponseMessage.ToolResponse toolResponse = toolResponseMsg.getResponses()
											.get(i);
									// Find the corresponding pending call data based on index or ID/Name if
									// possible
									// Assuming order is preserved for simplicity here
									ToolCallData callToUpdate = pendingToolCalls.get(i);
									String toolName = callToUpdate.getToolName();

									String fullResponseJson = toolResponse.responseData();
									Object payloadObjectForAI = fullResponseJson; // Default payload is the full
																					// JSON string

									// 1. Store the FULL JSON in ToolCallData for the frontend modal
									callToUpdate.setResponseDataJson(fullResponseJson);

									// 2. Try to parse and extract metadata for ToolCallData AND determine clean
									// payload for AI
									try {
										// Attempt to parse as ApiCallResponse (most common for call_api, and now
										// others too)
										ApiCallResponse apiResponse = objectMapper.readValue(fullResponseJson,
												ApiCallResponse.class);

										// Update ToolCallData status and time
										callToUpdate.setStatus(apiResponse.isSuccess() ? ToolCallStatus.SUCCESS
												: ToolCallStatus.ERROR);
										callToUpdate.setExecutionTimeMs(apiResponse.getExecutionTimeMs());

										// Determine the payload FOR THE AI
										if (apiResponse.isSuccess()) {
											// For success, check if there's a nested 'response' or 'rawResponse'
											if (apiResponse.getResponseData() instanceof Map) {
												Map<?, ?> responseMap = (Map<?, ?>) apiResponse.getResponseData();
												if (responseMap.containsKey("rawResponse")) {
													payloadObjectForAI = responseMap.get("rawResponse");
												} else if (responseMap.containsKey("response")) {
													payloadObjectForAI = responseMap.get("response");
												} else {
													// If no specific field, send the whole responseData map
													payloadObjectForAI = apiResponse.getResponseData();
												}
											} else {
												// If responseData isn't a map, send it as is
												payloadObjectForAI = apiResponse.getResponseData();
											}
										} else {
											// For failure, send the structured error or the message
											if (apiResponse.getResponseData() != null) { // Structured error (e.g.,
																							// INVALID_PARAMETERS)
												payloadObjectForAI = apiResponse.getResponseData();
											} else { // Simple error message
												payloadObjectForAI = apiResponse.getErrorMessage();
											}
										}

									} catch (Exception e) {
										// Parsing failed - it's likely a simple String result (or maybe ChartData
										// JSON)
										callToUpdate.setStatus(ToolCallStatus.SUCCESS); // Assume success if no tool
																						// exception
										callToUpdate.setExecutionTimeMs(totalTimeTool); // Use approximate time

										// Try to determine if it's an error string
										try {
											String stringValue = objectMapper.readValue(fullResponseJson,
													String.class);
											if (stringValue.startsWith("Error:")) {
												callToUpdate.setStatus(ToolCallStatus.ERROR);
											}
											payloadObjectForAI = stringValue; // Payload for AI is the string
										} catch (Exception e2) {
											// It's not a String (maybe ChartData?). Keep SUCCESS status.
											// The payload for AI remains the original fullResponseJson in this edge
											// case.
											// We could try parsing as ChartData here if needed.
											try {
												// If it's the chart tool, capture chart data separately
												if ("create_chart".equals(toolName)) {
													capturedChartData = objectMapper.readValue(fullResponseJson,
															ChartData.class);
													payloadObjectForAI = "Chart data generated successfully."; // Simple
																												// confirmation
																												// for
																												// AI
													log.info("ChartData object captured successfully!");
												}
											} catch (Exception e3) {
												log.warn("Could not parse non-ApiCallResponse tool output: {}",
														fullResponseJson, e2);
												payloadObjectForAI = fullResponseJson; // Fallback payload
											}
										}
									}

									// 3. Serialize the CLEAN payload for the AI's next turn
									String payloadJsonForAI = objectMapper.writeValueAsString(payloadObjectForAI);
									cleanedResponsesForAI.add(new ToolResponseMessage.ToolResponse(
											toolResponse.id(), toolName, payloadJsonForAI));
								}

								// Construct the message containing ONLY the cleaned responses for the next AI
								// prompt
								finalMessageForHistory = new ToolResponseMessage(cleanedResponsesForAI);

							} else {
								// If the response wasn't a ToolResponseMessage or there were no pending calls,
								// use the original message (should ideally not happen in a valid flow)
								log.warn(
										"Unexpected state: Tool response message type mismatch or no pending calls.");
								finalMessageForHistory = originalToolResponseMessage;
							}

							// Add the tool execution results to the turn's history.
							turnHistory.add(finalMessageForHistory);

							// --- 8. Next AI Call within the Loop ---
							// Call the AI again with the updated history, including tool results.
							currentPrompt = new Prompt(new ArrayList<>(turnHistory), chatOptions);
							chatResponse = chatClient.prompt(currentPrompt)
									.toolContext(toolContext.getContext())
									.call()
									.chatResponse();
							chatResponse = ensureChatResponse(chatResponse, "loop next call");

							// Add the next assistant response to the turn history.
							turnHistory.add(chatResponse.getResult().getOutput());

							// If the model wrote an Act in text but did not emit a tool call, nudge once to
							// produce the tool call.
							chatResponse = enforceToolCallIfHallucinated(chatResponse, turnHistory, chatClient, chatOptions, toolContext, statusSink);
						}

						// --- 9. Finalize the Turn ---
						// Once the loop finishes, save the final assistant message to persistent
						// memory.
						chatMemory.add(conversationId, chatResponse.getResult().getOutput());

						statusSink.tryEmitNext(SseEvent.status("Formatting final answer..."));

						// CAPTURE REASONING STEP 4: The final natural language response.
						if (StringUtils.isNotBlank(chatResponse.getResult().getOutput().getText())) {
							reasoningSteps.add(new ReasoningStep(StepType.FINAL_RESPONSE,
									chatResponse.getResult().getOutput().getText()));
						}

						// --- 10. Logging and Metrics ---
						long totalTime = System.currentTimeMillis() - startTime;
						log.info("Successfully processed chat with tools for conversation: {} in {}ms",
								conversationId, totalTime);
						meterRegistry.timer("ai.chat.with.tools.duration", "status", "success").record(totalTime,
								TimeUnit.MILLISECONDS);

						// Return the complete response, including the final AI message and the
						// structured reasoning steps.
						return new ChatWithReasoningResponse(chatResponse, includeReasoning ? reasoningSteps : null,
								capturedChartData);

					} catch (Exception e) {
						log.error("Error processing chat with tools for conversation {}: {}", conversationId,
								e.getMessage(), e);
						meterRegistry.timer("ai.chat.with.tools.duration", "status", "error")
								.record(System.currentTimeMillis() - startTime, TimeUnit.MILLISECONDS);
						statusSink.tryEmitNext(SseEvent.error("An error occurred during tool processing."));
						// Build a graceful fallback response including any captured reasoning.
						String fallbackMessage = "Sorry, an error occurred: " + e.getMessage();
						if (includeReasoning) {
							reasoningSteps.add(new ReasoningStep(StepType.FINAL_RESPONSE, fallbackMessage));
						}
						ChatWithReasoningResponse response = new ChatWithReasoningResponse(null,
								includeReasoning ? reasoningSteps : null, capturedChartData);
						response.setFallbackContent(fallbackMessage);
						return response;
					}
				}).subscribeOn(Schedulers.boundedElastic())); // Ensure the callable runs on the correct thread pool.
	}

	/**
	 * Checks if the response contains a text-based tool call without a technical tool execution.
	 * If so, sends a system message to force the model to execute the tool properly.
	 */
	private ChatResponse enforceToolCallIfHallucinated(
			ChatResponse initialResponse, 
			List<Message> turnHistory, 
			ChatClient chatClient, 
			ChatOptions chatOptions,
			ToolContext toolContext,
			Sinks.Many<SseEvent> statusSink) {

		if (!initialResponse.hasToolCalls()) {
			String txt = StringUtils.defaultIfEmpty(initialResponse.getResult().getOutput().getText(), "");
			if (txt.contains("Act:") || txt.matches("(?s).*(?i)call `.+`.*")) {
				
				statusSink.tryEmitNext(SseEvent.status("Aligning tool execution..."));
				
				turnHistory.add(new SystemMessage(
					"You indicated an Act step. Emit the tool call now using one of the registered tools. Do not include any other text."));
				
				Prompt prompt = new Prompt(new ArrayList<>(turnHistory), chatOptions);
				ChatResponse correctedResponse = chatClient.prompt(prompt)
						.toolContext(toolContext.getContext())
						.call()
						.chatResponse();
				
				// Add the corrected response to history so the conversation stays consistent
				turnHistory.add(correctedResponse.getResult().getOutput());
				
				return correctedResponse;
			}
		}
		return initialResponse;
	}


	/**
	 * Tests the connection to the AI model by sending a predefined prompt in a
	 * non-blocking way.
	 *
	 * @return A reactive Mono containing the AI model's response as a string.
	 */
	public Mono<String> testConnection() {
		// Use the same reactive pattern for consistency.
		return chatClient.prompt().user("Say 'Connection test successful'").stream().content()
				.collect(Collectors.joining());
	}

	/**
	 * Ensures the AI model returned a complete response structure.
	 * Throws an IllegalStateException if any critical field is null.
	 * This helps keep the main loop free from repetitive null checks.
	 */
	private ChatResponse ensureChatResponse(ChatResponse response, String stage) {
		Objects.requireNonNull(response, "AI returned null ChatResponse at " + stage);
		Objects.requireNonNull(response.getResult(), "AI returned null result at " + stage);
		Objects.requireNonNull(response.getResult().getOutput(), "AI returned null output at " + stage);
		return response;
	}

	/**
	 * Builds a role-aware system prompt by prepending a concise persona instruction
	 * derived from the user's authorities.
	 *
	 * Behavior:
	 * - If authentication is null or unauthenticated, returns the basePrompt
	 * unchanged.
	 * - If the user has ROLE_IT, prepends instructions tailored to a technical
	 * audience.
	 * - If the user has ROLE_BUSINESS, prepends instructions tailored to a
	 * non-technical audience.
	 * - If no recognized role is present, returns the basePrompt unchanged.
	 *
	 * The final prompt format when a persona is applied is:
	 * personaInstruction + "\n\n---\n\n" + basePrompt
	 *
	 * This method has no side effects and is thread-safe.
	 *
	 * @param basePrompt     The original system prompt to use as the core
	 *                       instruction.
	 * @param authentication The Spring Security authentication from which roles are
	 *                       derived.
	 * @return The basePrompt optionally prefixed with a role-specific persona
	 *         instruction.
	 */
	private String buildPromptWithRole(String basePrompt, Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()) {
			return basePrompt;
		}

		Set<String> roles = authentication.getAuthorities().stream()
				.map(GrantedAuthority::getAuthority)
				.collect(Collectors.toSet());

		String personaInstruction = "";
		if (roles.contains("ROLE_IT")) {
			personaInstruction = ROLE_IT_INSTRUCTION;
		} else if (roles.contains("ROLE_BUSINESS")) {
			personaInstruction = ROLE_BUSINESS_INSTRUCTION;
		}

		if (personaInstruction.isEmpty()) {
			return basePrompt;
		}

		return personaInstruction + "\n\n---\n\n" + basePrompt;
	}
}
