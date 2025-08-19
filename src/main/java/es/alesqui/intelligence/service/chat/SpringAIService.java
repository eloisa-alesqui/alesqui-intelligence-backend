package es.alesqui.intelligence.service.chat;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

import es.alesqui.intelligence.dto.chat.response.ChatWithReasoningResponse;
import es.alesqui.intelligence.service.chat.tools.ApiActionTools;
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
    private final ApiActionTools apiActionTools;
    private final ReasoningFormatterService reasoningFormatterService;
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
     * Chat with tools support
     * 
     * @param systemPrompt Instructions for the AI model to define its behavior.
     * @param userPrompt The user's message to be processed by the AI model.
     * @param conversationId Unique identifier for the conversation to maintain its history.
     * @param includeReasoning Whether to include reasoning in the response.
     * @return A reactive Mono containing the AI model's ChatResponse and optional reasoning.
     */
    public Mono<ChatWithReasoningResponse> chatWithTools(String systemPrompt, String userPrompt, 
                                                       String conversationId, boolean includeReasoning) {
        
        validateInputs(userPrompt);

        return Mono.defer(() -> Mono.fromCallable(() -> {
            log.debug("Processing chat with tools (enabled: {}) for conversation: {}", conversationId);
            long startTime = System.currentTimeMillis();
            
            try {
                ChatResponse chatResponse = null;
                String reasoning = null;
                List<Message> history = getOrCreateConversationHistory(conversationId);
                Map<String, Object> toolContext = Map.of("conversationId", conversationId);
                
                if (includeReasoning) {
                    ToolCallback[] toolCallbacks = ToolCallbacks.from(apiActionTools);
                    ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder().build();
                    ChatMemory chatMemory = MessageWindowChatMemory.builder().build();
                    
                    ChatOptions chatOptions = ToolCallingChatOptions.builder()
                            .toolCallbacks(toolCallbacks)
                            .internalToolExecutionEnabled(false)
                            .build();
                            
                    Prompt prompt = new Prompt(
                            List.of(new SystemMessage(systemPrompt), new UserMessage(userPrompt)),
                            chatOptions);
                    chatMemory.add(conversationId, prompt.getInstructions());
                    
                    Prompt promptWithMemory = new Prompt(chatMemory.get(conversationId), chatOptions);
                    chatResponse = chatClient.prompt(promptWithMemory).toolContext(toolContext).call().chatResponse();
                    chatMemory.add(conversationId, chatResponse.getResult().getOutput());

                    while (chatResponse.hasToolCalls()) {
                        ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(promptWithMemory,
                                chatResponse);
                        chatMemory.add(conversationId, toolExecutionResult.conversationHistory()
                            .get(toolExecutionResult.conversationHistory().size() - 1));
                        promptWithMemory = new Prompt(chatMemory.get(conversationId), chatOptions);
                        chatResponse = chatClient.prompt(promptWithMemory).toolContext(toolContext).call().chatResponse();
                        chatMemory.add(conversationId, chatResponse.getResult().getOutput());
                    }

                    history.add(new UserMessage(userPrompt));
                    history.add(new AssistantMessage(chatResponse.getResult().getOutput().getText()));
                    limitConversationHistory(history, 20);
                    
                    reasoning = generateReasoningNarrative(chatMemory.get(conversationId));
                    
                } else {
                    chatResponse = chatClient
                            .prompt()
                            .system(systemPrompt)
                            .user(userPrompt)
                            .toolContext(toolContext)
                            .tools(apiActionTools)
                            .call()
                            .chatResponse();
                            
                    history.add(new UserMessage(userPrompt));
                    history.add(new AssistantMessage(chatResponse.getResult().getOutput().getText()));
                    limitConversationHistory(history, 20);
                }
                
                return new ChatWithReasoningResponse(chatResponse, reasoning);
                
            } catch (Exception e) {
                log.error("Error processing chat with tools for conversation {}: {}", 
                         conversationId, e.getMessage(), e);
                throw new RuntimeException("Failed to process chat request with tools", e);
            } finally {
                long endTime = System.currentTimeMillis();
                meterRegistry.timer("chat.with.tools.execution.time",
                    "conversation_id", conversationId)
                    .record(endTime - startTime, TimeUnit.MILLISECONDS);
            }
        }).subscribeOn(boundedElasticScheduler))
        .flatMap(response -> {
            if (response.getFormattedReasoning() != null) {
                return reasoningFormatterService.formatReasoning(response.getFormattedReasoning())
                        .map(formattedReasoning -> {
                            response.setFormattedReasoning(formattedReasoning);
                            return response;
                        });
            } else {
                return Mono.just(response);
            }
        })
        .timeout(Duration.ofSeconds(120))
        .doOnSuccess(response -> {
            log.info("Successfully processed chat with tools for conversation: {}", conversationId);
            meterRegistry.counter("chat.with.tools.success").increment();
        })
        .doOnError(error -> {
            log.error("Failed to process chat with tools for conversation {}: {}", 
                     conversationId, error.getMessage());
            meterRegistry.counter("chat.with.tools.error").increment();
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
    
    /**
     * Genera una narrativa de razonamiento a partir de una lista de mensajes
     * 
     * @param messages Lista de mensajes de la conversación
     * @return String con la narrativa formateada
     */
    private String generateReasoningNarrative(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return "No hay mensajes disponibles para generar razonamiento.";
        }
        
        StringBuilder narrative = new StringBuilder();
        int stepCounter = 1;
        
        // Encabezado
        narrative.append("🧠 **RAZONAMIENTO PASO A PASO**\n");
        narrative.append("=".repeat(50)).append("\n\n");
        
        // Procesar cada mensaje
        for (int i = 0; i < messages.size(); i++) {
        	Message message = messages.get(i);
            
            switch (message.getMessageType()) {
                case SYSTEM:
                    SystemMessage sysMsg = (SystemMessage) message;
                    narrative.append("📋 **Configuración inicial:**\n");
                    narrative.append("   • Sistema: ").append(sysMsg.getText()).append("\n\n");
                    break;
                    
                case USER:
                    UserMessage userMsg = (UserMessage) message;
                    narrative.append("❓ **Pregunta del usuario:**\n");
                    narrative.append("   • \"").append(userMsg.getText()).append("\"\n\n");
                    break;
                    
                case ASSISTANT:
                    AssistantMessage aiMsg = (AssistantMessage) message;
                    narrative.append("🤖 **Paso ").append(stepCounter).append("\n\n");

                    if (aiMsg.getToolCalls() != null && !aiMsg.getToolCalls().isEmpty()) {
                        narrative.append("🔧 **Herramientas solicitadas:**\n");
                        for (ToolCall request : aiMsg.getToolCalls()) {
                            narrative.append("   • ").append(request.name())
                                   .append("(").append(request.arguments()).append(")\n");
                        }
                        narrative.append("\n");
                    }
                    stepCounter++;
                    break;
                    
                case TOOL:
                    ToolResponseMessage toolMsg = (ToolResponseMessage) message;
                    
                    if (toolMsg.getResponses() != null && !toolMsg.getResponses().isEmpty()) {
                        narrative.append("🔧 **Resultado de herramientas:**\n");
                        for (ToolResponse response : toolMsg.getResponses()) {
                            narrative.append("   • ").append(response.name()).append("\n\n")
                                   .append("Resultado: ").append(response.responseData()).append(")\n");
                        }
                        narrative.append("\n");
                    }
                    break;
            }
        }

        return narrative.toString();
    }
}
