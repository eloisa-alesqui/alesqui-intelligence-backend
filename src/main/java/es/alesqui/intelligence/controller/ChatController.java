package es.alesqui.intelligence.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.alesqui.intelligence.dto.chat.request.ChatRequest;
import es.alesqui.intelligence.dto.chat.response.ChatResponse;
import es.alesqui.intelligence.dto.chat.response.SseEvent;
import es.alesqui.intelligence.service.chat.ChatOrchestrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("isAuthenticated()")
public class ChatController {

    private final ChatOrchestrationService chatOrchestrationService;
    private final ObjectMapper objectMapper; // Injected to serialize events to JSON

    /**
     * Processes a chat request and streams responses using Server-Sent Events (SSE).
     * This endpoint is ideal for clients that want to receive real-time status updates.
     *
     * @param request The chat request containing the user query.
     * @return A Flux of ServerSentEvent, where each event's data is a JSON string
     * representing an SseEvent object.
     */
    @PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamMessage(@RequestBody @Valid ChatRequest request) {
        ensureConversationId(request);
        log.info("📨 Received chat stream request for conversation: {}", request.getConversationId());

        return chatOrchestrationService.processQuery(request)
                .map(this::toSse); // Convert our SseEvent DTOs to SSE format
    }

    /**
     * Processes a chat request and returns a single, complete response.
     * This endpoint is for clients that do not support streaming and wait for the full answer.
     *
     * @param request The chat request containing the user query.
     * @return A Mono containing the final ChatResponse.
     */
    @PostMapping("/message")
    public Mono<ChatResponse> sendMessage(@RequestBody @Valid ChatRequest request) {
        ensureConversationId(request);
        log.info("📨 Received chat request for conversation: {}", request.getConversationId());

        // Adapt the streaming service to a single response Mono
        return chatOrchestrationService.processQuery(request)
                // We only care about the FINAL_RESPONSE event
                .filter(event -> event.getType() == SseEvent.EventType.FINAL_RESPONSE)
                // Extract the ChatResponse payload from the event
                .map(event -> (ChatResponse) event.getPayload())
                // Take the first (and only) final response event
                .next();
    }

    /**
     * Converts an SseEvent DTO into a ServerSentEvent wrapper with a JSON string payload.
     *
     * @param event The SseEvent to convert.
     * @return A ServerSentEvent ready to be sent to the client.
     */
    @SneakyThrows // Automatically handles Jackson's checked exceptions
    private ServerSentEvent<String> toSse(SseEvent event) {
        String jsonPayload = objectMapper.writeValueAsString(event);
        return ServerSentEvent.builder(jsonPayload).build();
    }
    
    /**
     * Ensures that the ChatRequest has a valid conversation ID, generating one if not present.
     *
     * @param request The incoming chat request.
     */
    private void ensureConversationId(ChatRequest request) {
        if (request.getConversationId() == null || request.getConversationId().isBlank()) {
            request.setConversationId(generateConversationId());
        }
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