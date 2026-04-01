package es.alesqui.intelligence.controller;

import es.alesqui.intelligence.config.TestSecurityConfig;
import es.alesqui.intelligence.dto.conversation.ConversationDetailDTO;
import es.alesqui.intelligence.dto.conversation.ConversationExportDTO;
import es.alesqui.intelligence.dto.conversation.ConversationSummaryDTO;
import es.alesqui.intelligence.model.conversation.ConversationRecord;
import es.alesqui.intelligence.security.JwtService;
import es.alesqui.intelligence.service.conversation.ConversationService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

/**
 * Controller-layer tests for ConversationController.
 * Uses TestSecurityConfig to bypass the JWT filter.
 * SecurityUtils.getCurrentUsername() is exercised via SecurityMockServerConfigurers.mockUser().
 */
@WebFluxTest(ConversationController.class)
@Import(TestSecurityConfig.class)
class ConversationControllerTest {

    @Autowired
    WebTestClient webClient;

    @MockitoBean ConversationService conversationService;

    // Required by JwtAuthenticationWebFilter (@Component WebFilter auto-scanned)
    @MockitoBean JwtService jwtService;
    @MockitoBean ReactiveUserDetailsService reactiveUserDetailsService;

    private ConversationSummaryDTO summary1;
    private ConversationSummaryDTO summary2;
    private ConversationDetailDTO detail;
    private ConversationExportDTO exportDTO;
    private ConversationRecord record;

    @BeforeEach
    void setUp() {
        summary1 = new ConversationSummaryDTO("conv-1", "Chat about APIs", Instant.now());
        summary2 = new ConversationSummaryDTO("conv-2", "Another chat", Instant.now());

        detail = ConversationDetailDTO.builder()
                .id("rec-1")
                .userPrompt("Hello")
                .responseText("Hi there!")
                .timestamp(Instant.now())
                .build();

        exportDTO = ConversationExportDTO.builder()
                .username("testuser")
                .exportedAt(Instant.now())
                .totalConversations(0)
                .conversations(List.of())
                .build();

        record = ConversationRecord.builder()
                .id("rec-1")
                .conversationId("conv-1")
                .username("testuser")
                .build();
    }

    // ──────────────────────────── getUserConversations ────────────────────────────

    @Test
    void getUserConversations_returns200() {
        given(conversationService.getHistoryForUser("testuser")).willReturn(Flux.just(summary1, summary2));

        webClient.mutateWith(SecurityMockServerConfigurers.mockUser("testuser"))
                .get()
                .uri("/api/conversations")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ConversationSummaryDTO.class)
                .hasSize(2);
    }

    // ──────────────────────────── getConversationById ────────────────────────────

    @Test
    void getConversationById_returns200() {
        given(conversationService.getConversationDetailsForUser("conv-1", "testuser"))
                .willReturn(Flux.just(detail));

        webClient.mutateWith(SecurityMockServerConfigurers.mockUser("testuser"))
                .get()
                .uri("/api/conversations/conv-1")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ConversationDetailDTO.class)
                .hasSize(1);
    }

    // ──────────────────────────── deleteConversation ────────────────────────────

    @Test
    void deleteConversation_returns204() {
        given(conversationService.deleteConversationForUser("conv-1", "testuser"))
                .willReturn(Mono.empty());

        webClient.mutateWith(SecurityMockServerConfigurers.mockUser("testuser"))
                .delete()
                .uri("/api/conversations/conv-1")
                .exchange()
                .expectStatus().isNoContent();
    }

    // ──────────────────────────── export ────────────────────────────

    @Test
    void exportConversations_returns200WithContentDisposition() {
        given(conversationService.exportConversationsForUser("testuser"))
                .willReturn(Mono.just(exportDTO));

        webClient.mutateWith(SecurityMockServerConfigurers.mockUser("testuser"))
                .get()
                .uri("/api/conversations/export")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().value(HttpHeaders.CONTENT_DISPOSITION,
                        value -> assertThat(value).contains("attachment"))
                .expectHeader().contentType(MediaType.APPLICATION_JSON);
    }

    // ──────────────────────────── reportIssue ────────────────────────────

    @Test
    void reportIssue_returns200() {
        given(conversationService.reportRecord(eq("rec-1"), eq("Something went wrong"), eq("testuser")))
                .willReturn(Mono.just(record));

        webClient.mutateWith(SecurityMockServerConfigurers.mockUser("testuser"))
                .post()
                .uri("/api/conversations/records/rec-1/report")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"comment\": \"Something went wrong\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("Thank you"));
    }

    @Test
    void reportIssue_forbidden_returns403() {
        given(conversationService.reportRecord(eq("rec-1"), eq("comment"), eq("testuser")))
                .willReturn(Mono.error(new SecurityException("Access denied")));

        webClient.mutateWith(SecurityMockServerConfigurers.mockUser("testuser"))
                .post()
                .uri("/api/conversations/records/rec-1/report")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"comment\": \"comment\"}")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void reportIssue_notFound_returns404() {
        given(conversationService.reportRecord(eq("missing"), eq("comment"), eq("testuser")))
                .willReturn(Mono.error(new RuntimeException("Record not found")));

        webClient.mutateWith(SecurityMockServerConfigurers.mockUser("testuser"))
                .post()
                .uri("/api/conversations/records/missing/report")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"comment\": \"comment\"}")
                .exchange()
                .expectStatus().isNotFound();
    }

    // ──────────────────────────── no-auth fallback ────────────────────────────

    @Test
    void getUserConversations_noAuth_fallsBackToAnonymous() {
        given(conversationService.getHistoryForUser("anonymous_fallback")).willReturn(Flux.empty());

        webClient.get()
                .uri("/api/conversations")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ConversationSummaryDTO.class)
                .hasSize(0);
    }
}
