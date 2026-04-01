package es.alesqui.intelligence.service.conversation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatMemoryService {

	private final ConversationService conversationService;
	private final ChatMemory chatMemory;

	/**
	 * Loads the entire history of a given conversation from the database into the
	 * ChatMemory. It first clears any existing entries for that conversationId to
	 * avoid duplication.
	 *
	 * @param conversationId The ID of the conversation to load.
	 * @return A Mono<Void> that completes when the operation is finished.
	 */
	public Mono<Void> loadHistoryIntoMemory(String conversationId) {
	    log.info("🚀 Loading history for conversation '{}' into memory.", conversationId);
	    
	    return conversationService.findAllByConversationIdForMemory(conversationId)
	        .collectList()
	        .doOnNext(records -> {
	            if (records.isEmpty()) {
	                log.info("No history found for conversation '{}'. Starting fresh.", conversationId);
	                return;
	            }

	            chatMemory.clear(conversationId);

	            var messages = records.stream().flatMap(record -> {
	                var userMessage = new UserMessage(record.getUserPrompt());
	                var assistantMessage = new AssistantMessage(record.getResponseText());
	                return java.util.stream.Stream.of(userMessage, assistantMessage);
	            }).toList();

	            messages.forEach(message -> chatMemory.add(conversationId, message));
	            
	            log.info("✅ Successfully loaded {} messages for conversation '{}' into memory.", messages.size(),
	                    conversationId);
	        })
	        .then(); 
	}
}
