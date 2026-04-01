package es.alesqui.intelligence.controller;

import es.alesqui.intelligence.config.TestSecurityConfig;
import es.alesqui.intelligence.dto.chat.request.ChatRequest;
import es.alesqui.intelligence.dto.chat.response.ChatResponse;
import es.alesqui.intelligence.dto.chat.response.SseEvent;
import es.alesqui.intelligence.security.JwtService;
import es.alesqui.intelligence.service.chat.ChatOrchestrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.FluxExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * Controller-layer tests for ChatController.
 * Verifies SSE streaming, single-message endpoint, request validation, and
 * conversation ID auto-generation. Security enforcement (401 for unauthenticated
 * chat requests) is verified in SecurityConfig integration tests.
 */
@WebFluxTest(ChatController.class)
@Import(TestSecurityConfig.class)
class ChatControllerTest {

    @Autowired
    WebTestClient webClient;

    // JwtAuthenticationWebFilter is a @Component WebFilter loaded by @WebFluxTest;
    // its dependencies must be mocked so it can be instantiated.
    @MockitoBean
    JwtService jwtService;

    @MockitoBean
    ReactiveUserDetailsService userDetailsService;

    @MockitoBean
    ChatOrchestrationService chatOrchestrationService;

    private ChatResponse mockChatResponse;

    @BeforeEach
    void setUp() {
        mockChatResponse = ChatResponse.builder()
                .content("Here is the answer")
                .conversationId("conv-123")
                .success(true)
                .build();
    }

    // ──────────────────────────── POST /api/chat/stream ────────────────────────────

    @Test
    void streamMessage_success_returns200WithEventStream() {
        Flux<SseEvent> events = Flux.just(
                SseEvent.status("Analyzing request..."),
                SseEvent.finalResponse(mockChatResponse)
        );
        given(chatOrchestrationService.processQuery(any())).willReturn(events);

        ChatRequest request = buildChatRequest("What APIs are available?", "conv-123");

        webClient.post().uri("/api/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM);
    }

    @Test
    void streamMessage_success_emitsAllEvents() {
        Flux<SseEvent> events = Flux.just(
                SseEvent.status("Step 1"),
                SseEvent.status("Step 2"),
                SseEvent.finalResponse(mockChatResponse)
        );
        given(chatOrchestrationService.processQuery(any())).willReturn(events);

        ChatRequest request = buildChatRequest("List all endpoints", "conv-abc");

        FluxExchangeResult<String> result = webClient.post().uri("/api/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .returnResult(String.class);

        StepVerifier.create(result.getResponseBody())
                .expectNextCount(3)
                .verifyComplete();
    }

    @Test
    void streamMessage_generatesConversationId_whenNotProvided() {
        // The controller populates conversationId before calling the service
        Flux<SseEvent> events = Flux.just(SseEvent.finalResponse(mockChatResponse));
        given(chatOrchestrationService.processQuery(any(ChatRequest.class))).willAnswer(invocation -> {
            ChatRequest req = invocation.getArgument(0);
            // The service receives a request with a generated conversationId
            assertThat(req.getConversationId()).isNotBlank().startsWith("conv_");
            return events;
        });

        ChatRequest request = buildChatRequest("Hello", null);

        webClient.post().uri("/api/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void streamMessage_blankQuery_returns400() {
        ChatRequest request = buildChatRequest("", "conv-123");

        webClient.post().uri("/api/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void streamMessage_queryTooLong_returns400() {
        String longQuery = "a".repeat(2001); // exceeds @Size(max = 2000)
        ChatRequest request = buildChatRequest(longQuery, "conv-123");

        webClient.post().uri("/api/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void streamMessage_serviceError_emitsErrorEvent() {
        Flux<SseEvent> errorFlux = Flux.just(SseEvent.error("Processing failed"));
        given(chatOrchestrationService.processQuery(any())).willReturn(errorFlux);

        ChatRequest request = buildChatRequest("trigger error", "conv-123");

        FluxExchangeResult<String> result = webClient.post().uri("/api/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .returnResult(String.class);

        StepVerifier.create(result.getResponseBody())
                .expectNextMatches(data -> data.contains("ERROR"))
                .verifyComplete();
    }

    // ──────────────────────────── POST /api/chat/message ────────────────────────────

    @Test
    void sendMessage_success_returns200WithChatResponse() {
        Flux<SseEvent> events = Flux.just(
                SseEvent.status("Processing..."),
                SseEvent.finalResponse(mockChatResponse)
        );
        given(chatOrchestrationService.processQuery(any())).willReturn(events);

        ChatRequest request = buildChatRequest("What is the user count?", "conv-123");

        webClient.post().uri("/api/chat/message")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ChatResponse.class)
                .value(response -> {
                    assertThat(response.getContent()).isEqualTo("Here is the answer");
                    assertThat(response.isSuccess()).isTrue();
                    assertThat(response.getConversationId()).isEqualTo("conv-123");
                });
    }

    @Test
    void sendMessage_blankQuery_returns400() {
        ChatRequest request = buildChatRequest("", "conv-123");

        webClient.post().uri("/api/chat/message")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void sendMessage_generatesConversationId_whenNotProvided() {
        Flux<SseEvent> events = Flux.just(SseEvent.finalResponse(mockChatResponse));
        given(chatOrchestrationService.processQuery(any(ChatRequest.class))).willAnswer(invocation -> {
            ChatRequest req = invocation.getArgument(0);
            assertThat(req.getConversationId()).isNotBlank();
            return events;
        });

        ChatRequest request = buildChatRequest("Hello", null);

        webClient.post().uri("/api/chat/message")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk();
    }

    // ──────────────────────────── helpers ────────────────────────────

    private ChatRequest buildChatRequest(String query, String conversationId) {
        ChatRequest req = new ChatRequest();
        req.setQuery(query);
        req.setConversationId(conversationId);
        return req;
    }
}
