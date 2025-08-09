package es.alesqui.postmangpt.service.chat;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Service for managing interactions with an AI chat model.
 * Provides functionalities for maintaining conversation history, extracting structured data,
 * and interacting with the model with or without memory.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SpringAIService {

    private final ChatClient chatClient;
    private final Scheduler boundedElasticScheduler;
    private final MeterRegistry meterRegistry;
    private final Map<String, List<Message>> conversationHistory = new ConcurrentHashMap<>();

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
     * Limits the size of a conversation history list to a maximum number of messages.
     *
     * @param history The conversation history to limit.
     * @param maxSize The maximum size allowed for the history.
     */
    private void limitConversationHistory(List<Message> history, int maxSize) {
    	synchronized (history) {
            if (history.size() > maxSize) {
                int removedCount = history.size() - maxSize;
                for (int i = 0; i < removedCount; i++) {
                    history.remove(0); // Eliminar desde el inicio
                }
                log.info("History trimmed: {} messages removed", removedCount);
            }
        }
    }

    /**
     * Retrieves or creates a synchronized list for the conversation history of a given conversation ID.
     *
     * @param conversationId The unique identifier for the conversation.
     * @return A synchronized list representing the conversation history.
     */
    private List<Message> getOrCreateConversationHistory(String conversationId) {
        return conversationHistory.computeIfAbsent(conversationId, k -> Collections.synchronizedList(new ArrayList<>()));
    }

    /**
     * Initiates a chat session with memory, allowing the AI model to consider previous messages in the conversation.
     * Updates the conversation history and limits it to a maximum of 20 messages.
     *
     * @param systemPrompt Instructions for the AI model to define its behavior.
     * @param userPrompt The user's message to be processed by the AI model.
     * @param conversationId Unique identifier for the conversation to maintain its history.
     * @return A reactive Mono containing the AI model's response as a string.
     */
	public Mono<String> chat(String systemPrompt, String userPrompt, String conversationId) {
		validateInputs(userPrompt);

		return Mono.defer(() -> Mono.fromCallable(() -> {
			log.debug("Processing chat request for conversation: {}", conversationId);
			log.debug("systemPrompt: " + systemPrompt);
			log.debug("userPrompt: " + userPrompt);
			long startTime = System.currentTimeMillis();
			try {
				List<Message> history = getOrCreateConversationHistory(conversationId);
				String response = chatClient.prompt().system(systemPrompt).user(userPrompt).call().content();

				log.debug("Chat response generated for conversation: {}", conversationId); 
				log.debug("response: " + response);

				history.add(new UserMessage(userPrompt));
				history.add(new AssistantMessage(response));
				limitConversationHistory(history, 20);

				return response;

			} catch (Exception e) {
				log.error("Error processing chat request for conversation {}: {}", conversationId, e.getMessage(), e);
				throw new RuntimeException("Failed to process chat request", e);
			} finally {
				long endTime = System.currentTimeMillis();
				meterRegistry.timer("chat.execution.time").record(endTime - startTime, TimeUnit.MILLISECONDS);
			}
		}).subscribeOn(boundedElasticScheduler))
				.timeout(Duration.ofSeconds(60))
				.doOnSuccess(response -> log.info("Successfully processed chat for conversation: {}", conversationId))
				.doOnError(error -> log.error("Failed to process chat for conversation {}: {}", conversationId,
						error.getMessage()))
				.onErrorReturn(
						"I apologize, but I'm experiencing technical difficulties. Please try again in a moment.");
	}
    
    /**
     * Extracts structured data from the AI model based on a given prompt.
     * The result is mapped to the specified target class using the entity() method.
     *
     * @param systemPrompt The system-level instructions or context for the AI model.
     * @param userPrompt The user's query or instructions for structured data extraction.
     * @param conversationId Identifier for the conversation context.
     * @param targetClass The class type to which the extracted data should be mapped.
     * @param <T> The type of the structured data to be extracted.
     * @return A reactive Mono containing the extracted structured data as an instance of the target class.
     */
    public <T> Mono<T> extractStructuredData(String systemPrompt, String userPrompt, String conversationId, Class<T> targetClass) {
        validateInputs(systemPrompt, userPrompt, conversationId);

        long startTime = System.currentTimeMillis();
        log.info("Extracting structured data for class: {}, systemPrompt: {}, userPrompt: {}, conversationId: {}", 
                 targetClass.getSimpleName(), systemPrompt, userPrompt, conversationId);
        
        List<Message> history = getOrCreateConversationHistory(conversationId);

        return Mono.fromCallable(() -> {
            // Llamada al cliente de chat para obtener la respuesta como String
            String rawResponse = chatClient.prompt()
                .system(systemPrompt)
                .messages(history.toArray(new Message[0]))
                .user(userPrompt)
                .call()
                .content();

            // Mapear la respuesta al tipo de datos estructurados
            T responseEntity = chatClient.prompt()
                .system(systemPrompt)
                .messages(history.toArray(new Message[0]))
                .user(userPrompt)
                .call()
                .entity(targetClass);

            // Actualizar el historial de la conversación
            history.add(new UserMessage(userPrompt));
            history.add(new AssistantMessage(rawResponse));
            limitConversationHistory(history, 20);

            long duration = System.currentTimeMillis() - startTime;
            meterRegistry.timer("ai.extraction.duration", 
                                "class", targetClass.getSimpleName(), 
                                "conversationId", conversationId)
                .record(duration, TimeUnit.MILLISECONDS);

            log.info("Successfully extracted data for class: {}, conversationId: {}", targetClass.getSimpleName(), conversationId);
            return responseEntity;
        })
        .doOnSuccess(result -> {
            meterRegistry.counter("ai.extraction.success", 
                                  "class", targetClass.getSimpleName(), 
                                  "conversationId", conversationId).increment();
            log.debug("Extracted structured data: {}, conversationId: {}", result, conversationId);
        })
        .doOnError(error -> {
            meterRegistry.counter("ai.extraction.error", 
                                  "class", targetClass.getSimpleName(), 
                                  "conversationId", conversationId).increment();
            log.error("Error extracting structured data for class: {}, systemPrompt: {}, userPrompt: {}, conversationId: {}", 
                      targetClass.getSimpleName(), systemPrompt, userPrompt, conversationId, error);
        });
    }

    /**
     * Initiates a chat session without memory, meaning the AI model processes the user's message
     * without considering any previous conversation history.
     *
     * @param systemPrompt Instructions for the AI model to define its behavior.
     * @param userPrompt The user's message to be processed by the AI model.
     * @return A reactive Mono containing the AI model's response as a string.
     */
    public Mono<String> chatWithoutMemory(String systemPrompt, String userPrompt) {
        validateInputs(systemPrompt, userPrompt);

        return Mono.fromSupplier(() -> {
            long startTime = System.currentTimeMillis();
            try {
                String response = chatClient.prompt()
                        .system(systemPrompt)
                        .user(userPrompt)
                        .call()
                        .content();

                return response;
            } finally {
                long endTime = System.currentTimeMillis();
                meterRegistry.timer("chatWithoutMemory.execution.time").record(endTime - startTime, TimeUnit.MILLISECONDS);
            }
        });
    }

    /**
     * Clears the conversation history for a given conversation ID.
     *
     * @param conversationId The unique identifier for the conversation to be cleared.
     */
    public void clearConversation(String conversationId) {
        validateInputs(conversationId);

        List<Message> removed = conversationHistory.remove(conversationId);
        if (removed != null) {
            log.info("Conversation removed. It contained {} messages", removed.size());
        } else {
            log.warn("No conversation found with ID: {}", conversationId);
        }
    }

    /**
     * Retrieves the size of the conversation history for a given conversation ID.
     *
     * @param conversationId The unique identifier for the conversation.
     * @return The number of messages in the conversation history.
     */
    public int getConversationSize(String conversationId) {
        validateInputs(conversationId);

        int size = conversationHistory.getOrDefault(conversationId, Collections.emptyList()).size();
        log.info("Conversation {} size: {} messages", conversationId, size);
        return size;
    }

    /**
     * Retrieves the conversation history for a given conversation ID.
     * Returns a view of the history to prevent external modifications.
     *
     * @param conversationId The unique identifier for the conversation.
     * @return A list of messages representing the conversation history.
     */
    public List<Message> getConversationHistory(String conversationId) {
        validateInputs(conversationId);

        List<Message> history = conversationHistory.get(conversationId);
        if (history == null) {
            log.info("No history found for conversation: {}", conversationId);
            return Collections.emptyList();
        }
        log.info("Retrieved history for {}: {} messages", conversationId, history.size());
        return Collections.unmodifiableList(history);
    }

    /**
     * Retrieves statistics about all active conversations.
     * Provides the number of messages in each conversation.
     *
     * @return A map where the keys are conversation IDs and the values are the number of messages.
     */
    public Map<String, Integer> getConversationStats() {
        log.info("=== Conversation Statistics ===");
        return conversationHistory.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().size()));
    }

    /**
     * Tests the connection to the AI model by sending a predefined prompt.
     *
     * @return A reactive Mono containing the AI model's response as a string.
     */
    public Mono<String> testConnection() {
        return Mono.fromSupplier(() -> {
            long startTime = System.currentTimeMillis();
            try {
                String response = chatClient.prompt()
                        .user("Say 'Connection test successful'")
                        .call()
                        .content();

                return response;
            } finally {
                long endTime = System.currentTimeMillis();
                meterRegistry.timer("testConnection.execution.time").record(endTime - startTime, TimeUnit.MILLISECONDS);
            }
        });
    }
}
