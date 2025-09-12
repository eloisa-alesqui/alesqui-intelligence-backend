package es.alesqui.intelligence.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import es.alesqui.intelligence.config.ChatConfig;
import es.alesqui.intelligence.dto.chat.request.ChatRequest;
import es.alesqui.intelligence.dto.chat.response.ChatResponse;
import es.alesqui.intelligence.service.chat.ChatOrchestrationService;
import reactor.core.publisher.Mono;
import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatOrchestrationService chatOrchestrationService;
    private final ChatConfig chatConfig;

    /**
     * Processes incoming chat messages and returns AI-generated responses asynchronously.
     * This endpoint is fully non-blocking, leveraging the reactive service layer.
     *
     * @param request The chat request containing the user query and conversation context.
     * @return A reactive Mono containing the ResponseEntity with the AI-generated chat response or error details.
     */
    @PostMapping("/message")
    @PreAuthorize("hasAnyRole('ROLE_IT', 'ROLE_BUSINESS')")
    public Mono<ResponseEntity<ChatResponse>> sendMessage(@RequestBody ChatRequest request) {
        
        // Ensure a conversation ID exists for the request.
        if (request.getConversationId() == null || request.getConversationId().isBlank()) {
            request.setConversationId(generateConversationId());
        }
        final String conversationId = request.getConversationId();

        log.info("📨 Received chat request for conversation: {}", conversationId);
        
        // Determine the timeout to use: either from the request or the central configuration.
        Duration requestTimeout = request.getTimeoutSeconds() > 0 
            ? Duration.ofSeconds(request.getTimeoutSeconds()) 
            : chatConfig.getProcessingTimeout(); // Use the Duration object directly

        return chatOrchestrationService.processQuery(request)
        	// Apply the determined timeout
            .timeout(requestTimeout)
            // Map the successful ChatResponse to a 200 OK ResponseEntity.
            .map(ResponseEntity::ok)
            // Handle any errors that occur during the reactive stream processing.
            .onErrorResume(throwable -> handleError(throwable, conversationId));
    }

    /**
     * Handles errors from the reactive pipeline and converts them into a standard error response.
     *
     * @param throwable      The error that occurred.
     * @param conversationId The ID of the conversation that failed.
     * @return A Mono containing a 500 INTERNAL_SERVER_ERROR ResponseEntity.
     */
    private Mono<ResponseEntity<ChatResponse>> handleError(Throwable throwable, String conversationId) {
        log.error("❌ Error processing message for conversation {}: {}", conversationId, throwable.getMessage(), throwable);
        
        ChatResponse errorResponse = ChatResponse.error(
            "An error occurred while processing your request: " + throwable.getMessage(), 
            conversationId
        );
        
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse));
    }

    /**
     * Generates a new, unique conversation ID.
     *
     * @return A formatted conversation ID string.
     */
    private String generateConversationId() {
        return "conv_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }
}