package es.alesqui.postmangpt.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import es.alesqui.postmangpt.dto.chat.request.ChatRequest;
import es.alesqui.postmangpt.dto.chat.response.ChatResponse;
import es.alesqui.postmangpt.service.chat.ChatOrchestrationService;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

	private final ChatOrchestrationService chatOrchestrationService;

	/**
	 * Processes incoming chat messages and returns AI-generated responses.
	 * Automatically generates conversation IDs for new conversations and handles
	 * query classification, routing, and error recovery.
	 *
	 * @param request the chat request containing user query and conversation
	 *                context
	 * @return reactive response containing the AI-generated chat response or error
	 *         details
	 */
	@PostMapping("/message")
	public Mono<ResponseEntity<ChatResponse>> sendMessage(@RequestBody ChatRequest request) {
		
		// Generate conversation ID if not provided
	    String conversationId = request.getConversationId() != null ? request.getConversationId()
	            : generateConversationId();

	    // Ensure request has conversation ID set
	    if (request.getConversationId() == null) {
	        request.setConversationId(conversationId);
	    }

	    log.info("📨 Received chat request for conversation: {}", conversationId);

	    return Mono.fromCallable(() -> chatOrchestrationService.processQuery(request))
	            .subscribeOn(Schedulers.boundedElastic())
	            .timeout(Duration.ofSeconds(request.getTimeoutSeconds() != 0 ? request.getTimeoutSeconds() : 60))
	            .retryWhen(Retry.backoff(2, Duration.ofMillis(500))
	                    .filter(ex -> !(ex instanceof TimeoutException)))
	            .map(ResponseEntity::ok)
	            .onErrorResume(throwable -> handleError(throwable, conversationId)); 
	}
	

	private Mono<ResponseEntity<ChatResponse>> handleError(Throwable throwable, String conversationId) {
		log.error("❌ Error processing message for conversation {}: {}", conversationId, throwable.getMessage(),
				throwable);
		// Create error response using the static factory method
		ChatResponse errorResponse = ChatResponse.error(throwable.getMessage(), conversationId);
		return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse));
	}

	private String generateConversationId() {
		return "conv_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
	}
}