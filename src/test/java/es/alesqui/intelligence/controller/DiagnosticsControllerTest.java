package es.alesqui.intelligence.controller;

import es.alesqui.intelligence.config.TestSecurityConfig;
import es.alesqui.intelligence.dto.conversation.ConversationDetailDTO;
import es.alesqui.intelligence.dto.conversation.DiagnosticTicketDTO;
import es.alesqui.intelligence.model.conversation.ConversationStatus;
import es.alesqui.intelligence.security.JwtService;
import es.alesqui.intelligence.service.conversation.ConversationService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

/**
 * Controller-layer tests for DiagnosticsController.
 * Uses TestSecurityConfig to bypass the JWT filter.
 */
@WebFluxTest(DiagnosticsController.class)
@Import(TestSecurityConfig.class)
class DiagnosticsControllerTest {

    @Autowired
    WebTestClient webClient;

    @MockitoBean ConversationService conversationService;

    // Required by JwtAuthenticationWebFilter (@Component WebFilter auto-scanned)
    @MockitoBean JwtService jwtService;
    @MockitoBean ReactiveUserDetailsService reactiveUserDetailsService;

    private DiagnosticTicketDTO ticket;
    private ConversationDetailDTO detail;

    @BeforeEach
    void setUp() {
        ticket = DiagnosticTicketDTO.builder()
                .recordId("rec-1")
                .conversationId("conv-1")
                .username("testuser")
                .timestamp(Instant.parse("2026-03-29T10:00:00Z"))
                .status(ConversationStatus.REPORTED_BY_USER)
                .userPrompt("Why does this fail?")
                .userFeedbackComment("It returned wrong data")
                .build();

        detail = ConversationDetailDTO.builder()
                .id("rec-1")
                .userPrompt("Why does this fail?")
                .responseText("Let me check...")
                .timestamp(Instant.parse("2026-03-29T10:00:00Z"))
                .isError(false)
                .build();
    }

    // ──────────────────────────── getTickets ────────────────────────────

    @Test
    void getTickets_returns200WithPagination() {
        var pageable = PageRequest.of(0, 10);
        var page = new PageImpl<>(List.of(ticket), pageable, 1);

        given(conversationService.getTickets(any(), any(), any())).willReturn(Mono.just(page));

        webClient.get()
                .uri("/api/diagnostics/tickets")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content[0].recordId").isEqualTo("rec-1")
                .jsonPath("$.totalElements").isEqualTo(1);
    }

    // ──────────────────────────── getConversationDetails ────────────────────────────

    @Test
    void getConversationDetails_returns200() {
        given(conversationService.getConversationDetailsForIT("conv-1")).willReturn(Flux.just(detail));

        webClient.get()
                .uri("/api/diagnostics/conversations/conv-1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].id").isEqualTo("rec-1");
    }

    // ──────────────────────────── updateTicketStatus ────────────────────────────

    @Test
    void updateTicketStatus_returns200() {
        given(conversationService.updateRecordStatus(eq("rec-1"), eq(ConversationStatus.UNDER_REVIEW)))
                .willReturn(Mono.just(detail));

        webClient.put()
                .uri("/api/diagnostics/tickets/rec-1/status")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"status\":\"UNDER_REVIEW\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("rec-1");
    }

    // ──────────────────────────── addInternalNote ────────────────────────────

    @Test
    void addInternalNote_returns200() {
        given(conversationService.addInternalNote(eq("rec-1"), eq("Investigating")))
                .willReturn(Mono.just(detail));

        webClient.post()
                .uri("/api/diagnostics/tickets/rec-1/notes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"note\":\"Investigating\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("rec-1");
    }
}
