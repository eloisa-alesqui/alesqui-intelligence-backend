package es.alesqui.intelligence.service.chat;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
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
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import es.alesqui.intelligence.dto.chat.response.ChartData;
import es.alesqui.intelligence.dto.chat.response.ChatWithReasoningResponse;
import es.alesqui.intelligence.service.chat.tools.ApiActionTools;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
	private final ApiActionTools apiActionTools;
	private final ReasoningFormatterService reasoningFormatterService;
	private final ChatMemory chatMemory;
	private final MeterRegistry meterRegistry;
	private final ObjectMapper objectMapper;

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
		return chatClient.prompt().system(systemPrompt).user(userPrompt).stream().content()
				.collect(Collectors.joining());
	}

	/**
	 * Manages a multi-turn conversation with AI and external tools using a
	 * synchronous, blocking loop. This method is a robust and easier-to-debug
	 * alternative to a fully reactive chain. It safely offloads all blocking
	 * operations to a dedicated scheduler to protect the main application threads.
	 *
	 * @param systemPrompt     Instructions for the AI model to define its behavior.
	 * @param userPrompt       The user's message to be processed by the AI model.
	 * @param conversationId   Unique identifier for the conversation to maintain
	 *                         its history.
	 * @param includeReasoning Whether to include reasoning in the response.
	 * @return A reactive Mono containing the AI model's ChatResponse and optional
	 *         reasoning.
	 */
	public Mono<ChatWithReasoningResponse> chatWithTools(String systemPrompt, String userPrompt, String conversationId,
			boolean includeReasoning) {

		// Input validation to ensure the user prompt is not empty.
		validateInputs(userPrompt);

		// Offload the entire blocking sequence to a dedicated scheduler to avoid blocking main threads.
		return Mono.fromCallable(() -> {
			log.debug("Processing chat with tools for conversation: {}", conversationId);
			long startTime = System.currentTimeMillis();
			try {
				// 1. Set up the tool-calling infrastructure for Spring AI.
				ToolCallback[] toolCallbacks = ToolCallbacks.from(apiActionTools);
				ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder().build();
				ChatOptions chatOptions = ToolCallingChatOptions.builder().toolCallbacks(toolCallbacks)
						// Disable automatic tool execution by Spring AI; we will manage the loop manually.
						.internalToolExecutionEnabled(false).build();

				// 2. Correctly manage conversation history.
				// First, add the current user message to the persistent memory.
				chatMemory.add(conversationId, new UserMessage(userPrompt));
				// Then, retrieve the full, updated history for this turn.
				List<Message> history = chatMemory.get(conversationId);

				// 3. Construct the prompt for the current turn.
				// This temporary list will be sent to the AI. It includes the system prompt and the full history.
				List<Message> turnHistory = new ArrayList<>();
				turnHistory.add(new SystemMessage(systemPrompt)); // Add system instructions at the beginning.
				turnHistory.addAll(history); // Add the complete conversation history.

				// 4. Initialize variables for the ReAct loop
				ChatResponse chatResponse;
				String reasoning = null;
				ChartData capturedChartData = null;
				ToolContext toolContext = new ToolContext(Map.of("conversationId", conversationId));

				// 5. Make the first call to the AI model.
				// We use the "all-in-one" prompt structure that has proven to be effective.
				Prompt currentPrompt = new Prompt(new ArrayList<>(turnHistory), chatOptions);
				chatResponse = chatClient.prompt(currentPrompt)
						.toolContext(toolContext.getContext())
						.call()
						.chatResponse();

				// Add the assistant's first response to the turn's history (not the persistent memory yet).
				turnHistory.add(chatResponse.getResult().getOutput());

				// 6. Start the main ReAct loop: continue as long as the AI requests tool calls.
				while (chatResponse.hasToolCalls()) {
					log.debug("AI requested tools, executing...");

					// Execute the requested tools using the manager.
					ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(currentPrompt,
							chatResponse);
					// The result of the tool execution is a new message to add to the turn's history.
					Message toolResponseMessage = toolExecutionResult.conversationHistory()
							.get(toolExecutionResult.conversationHistory().size() - 1);

					// Check if a chart was created by a tool and capture its data.
					if (toolResponseMessage instanceof ToolResponseMessage toolResponse) {
						for (ToolResponseMessage.ToolResponse detailedResponse : toolResponse.getResponses()) {
							if ("createChart".equals(detailedResponse.name())) {
								try {
									capturedChartData = objectMapper.readValue(detailedResponse.responseData(),
											ChartData.class);
									log.info("📊 ChartData object captured successfully!");
									break; // Stop searching once chart data is found
								} catch (Exception e) {
									log.error("Error deserializing ChartData from tool response JSON: {}",
											detailedResponse.responseData(), e);
								}
							}
						}
					}
					turnHistory.add(toolResponseMessage);

					// Prepare and make the next call to the AI with the updated turn history (including tool results).
					currentPrompt = new Prompt(new ArrayList<>(turnHistory), chatOptions);
					chatResponse = chatClient.prompt(currentPrompt)
							.toolContext(toolContext.getContext())
							.call()
							.chatResponse();
					
					// Add the next assistant response to the turn history.
					turnHistory.add(chatResponse.getResult().getOutput());
				}

				// 7. Finalize the turn and save to persistent memory.
				// Now that the loop is finished, save the final assistant message to the long-term memory.
				chatMemory.add(conversationId, chatResponse.getResult().getOutput());

				// Generate the step-by-step reasoning narrative if requested.
				if (includeReasoning) {
					reasoning = generateReasoningNarrative(turnHistory);
				}

				long totalTime = System.currentTimeMillis() - startTime;
				log.info("Successfully processed chat with tools for conversation: {} in {}ms", conversationId,
						totalTime);
				meterRegistry.timer("ai.chat.with.tools.duration", "status", "success").record(totalTime,
						TimeUnit.MILLISECONDS);
				
				return new ChatWithReasoningResponse(chatResponse, reasoning, capturedChartData);

			} catch (Exception e) {
				log.error("Error processing chat with tools for conversation {}: {}", conversationId, e.getMessage(),
						e);
				meterRegistry.timer("ai.chat.with.tools.duration", "status", "error")
						.record(System.currentTimeMillis() - startTime, TimeUnit.MILLISECONDS);
				throw new RuntimeException("Failed to process chat request with tools", e);
			}
		}).subscribeOn(Schedulers.boundedElastic()) // Ensure the blocking code runs on a separate thread pool.
		  .flatMap(response -> {
			  // 8. Asynchronously format the reasoning text (if it exists).
			  if (response.getFormattedReasoning() != null) {
				  return reasoningFormatterService.formatReasoning(response.getFormattedReasoning())
						  .map(formattedReasoning -> {
							  response.setFormattedReasoning(formattedReasoning);
							  return response;
						  });
			  } else {
				  return Mono.just(response);
			  }
		});
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
	 * Generates a simple, unformatted reasoning narrative from a list of
	 * conversation messages. This raw text is intended to be processed by the
	 * ReasoningFormatterService to apply the final visual styling.
	 *
	 * @param messages The list of messages from the conversation history.
	 * @return A raw string detailing the AI's reasoning process.
	 */
	private String generateReasoningNarrative(List<Message> messages) {
		if (messages == null || messages.isEmpty()) {
			return "No messages were available to generate a reasoning narrative.";
		}
		StringBuilder narrative = new StringBuilder();
		int stepCounter = 1;

		// Generate a very plain text, without Markdown, for the formatter to process.

		for (Message message : messages) {
			if (message instanceof SystemMessage sysMsg) {
				// We can skip the system message in the raw narrative for brevity.
			} else if (message instanceof UserMessage userMsg) {
				narrative.append("User question:\n").append(userMsg.getText()).append("\n\n");
			} else if (message instanceof AssistantMessage aiMsg) {
				narrative.append("Step ").append(stepCounter++).append(" - Assistant's Turn\n");

				if (StringUtils.isNotBlank(aiMsg.getText())) {
					narrative.append("Thought: ").append(aiMsg.getText()).append("\n");
				}

				if (aiMsg.getToolCalls() != null && !aiMsg.getToolCalls().isEmpty()) {
					narrative.append("Action: Calling tools...\n");
					for (ToolCall toolCall : aiMsg.getToolCalls()) {
						narrative.append("Tool: ").append(toolCall.name()).append("\nArguments: ")
								.append(toolCall.arguments()).append("\n");
					}
				}
				narrative.append("\n");
			} else if (message instanceof ToolResponseMessage toolMsg) {
				narrative.append("Step ").append(stepCounter++).append(" - Tool Execution Results\n");
				if (toolMsg.getResponses() != null && !toolMsg.getResponses().isEmpty()) {
					for (ToolResponseMessage.ToolResponse response : toolMsg.getResponses()) {
						String responseData = response.responseData();
						narrative.append("Observation from ").append(response.name()).append(":\n").append("Result: ")
								.append(responseData).append("\n");
					}
				}
				narrative.append("\n");
			}
		}
		return narrative.toString();
	}
}
