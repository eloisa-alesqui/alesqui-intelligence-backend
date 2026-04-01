package es.alesqui.intelligence.controller;

import es.alesqui.intelligence.config.TestSecurityConfig;
import es.alesqui.intelligence.dto.audit.AuditStatsResponse;
import es.alesqui.intelligence.model.audit.AuditAction;
import es.alesqui.intelligence.model.audit.AuditLog;
import es.alesqui.intelligence.model.audit.AuditResult;
import es.alesqui.intelligence.model.audit.EntityType;
import es.alesqui.intelligence.security.JwtService;
import es.alesqui.intelligence.service.audit.AuditService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

/**
 * Controller-layer tests for AuditLogController.
 * Uses TestSecurityConfig to bypass the JWT filter.
 * AuditLogController does not use SecurityUtils — no mockUser() needed.
 */
@WebFluxTest(AuditLogController.class)
@Import(TestSecurityConfig.class)
class AuditLogControllerTest {

    @Autowired
    WebTestClient webClient;

    @MockitoBean AuditService auditService;

    // Required by JwtAuthenticationWebFilter (@Component WebFilter auto-scanned)
    @MockitoBean JwtService jwtService;
    @MockitoBean ReactiveUserDetailsService reactiveUserDetailsService;

    private AuditLog mockLog;
    private AuditStatsResponse statsResponse;

    @BeforeEach
    void setUp() {
        mockLog = AuditLog.builder()
                .id("log-1")
                .timestamp(Instant.parse("2026-03-29T10:00:00Z"))
                .actorUsername("admin")
                .actorUserId("user-1")
                .action(AuditAction.USER_CREATED)
                .entityType(EntityType.USER)
                .entityId("user-2")
                .entityName("john.doe")
                .details("Created user")
                .ipAddress("127.0.0.1")
                .userAgent("TestAgent")
                .result(AuditResult.SUCCESS)
                .build();

        statsResponse = AuditStatsResponse.builder()
                .totalLogs(10)
                .successCount(8)
                .failureCount(2)
                .actionCounts(Map.of(AuditAction.USER_CREATED, 5L))
                .entityTypeCounts(Map.of(EntityType.USER, 10L))
                .userActivityCounts(Map.of("admin", 10L))
                .lastActivityTime("2026-03-29T00:00:00Z")
                .build();
    }

    // ──────────────────────────── getRecentAuditLogs ────────────────────────────

    @Test
    void getRecentAuditLogs_returns200() {
        AuditLog log2 = AuditLog.builder()
                .id("log-2")
                .timestamp(Instant.parse("2026-03-28T10:00:00Z"))
                .actorUsername("admin")
                .action(AuditAction.USER_DELETED)
                .entityType(EntityType.USER)
                .entityName("jane.doe")
                .result(AuditResult.SUCCESS)
                .build();

        given(auditService.getRecentLogs(100)).willReturn(Flux.just(mockLog, log2));

        webClient.get()
                .uri("/api/admin/audit-logs/recent")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[0].id").isEqualTo("log-1")
                .jsonPath("$[0].action").isEqualTo("USER_CREATED")
                .jsonPath("$[1].id").isEqualTo("log-2");
    }

    // ──────────────────────────── getAuditLogsByUser ────────────────────────────

    @Test
    void getAuditLogsByUser_returns200WithPagination() {
        given(auditService.countLogsByUser("admin")).willReturn(Mono.just(1L));
        given(auditService.getLogsByUser("admin", 0, 50)).willReturn(Flux.just(mockLog));

        webClient.get()
                .uri("/api/admin/audit-logs/user/admin")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content[0].actorUsername").isEqualTo("admin")
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.page").isEqualTo(0)
                .jsonPath("$.size").isEqualTo(50)
                .jsonPath("$.first").isEqualTo(true);
    }

    // ──────────────────────────── getAuditLogsByEntity ────────────────────────────

    @Test
    void getAuditLogsByEntity_returns200() {
        given(auditService.countLogsByEntity(EntityType.USER, "user-2")).willReturn(Mono.just(1L));
        given(auditService.getLogsByEntity(EntityType.USER, "user-2", 0, 50)).willReturn(Flux.just(mockLog));

        webClient.get()
                .uri("/api/admin/audit-logs/entity/USER/user-2")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content[0].entityId").isEqualTo("user-2")
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.page").isEqualTo(0)
                .jsonPath("$.first").isEqualTo(true);
    }

    // ──────────────────────────── getAuditLogsByAction ────────────────────────────

    @Test
    void getAuditLogsByAction_returns200() {
        given(auditService.countLogsByAction(AuditAction.USER_CREATED)).willReturn(Mono.just(1L));
        given(auditService.getLogsByAction(AuditAction.USER_CREATED, 0, 50)).willReturn(Flux.just(mockLog));

        webClient.get()
                .uri("/api/admin/audit-logs/action/USER_CREATED")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content[0].action").isEqualTo("USER_CREATED")
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.first").isEqualTo(true);
    }

    // ──────────────────────────── getAuditLogsByDateRange ────────────────────────────

    @Test
    void getAuditLogsByDateRange_returns200() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = Instant.parse("2026-03-29T00:00:00Z");

        given(auditService.countLogsByDateRange(eq(start), eq(end))).willReturn(Mono.just(1L));
        given(auditService.getLogsByDateRange(eq(start), eq(end), eq(0), eq(50))).willReturn(Flux.just(mockLog));

        webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/admin/audit-logs/date-range")
                        .queryParam("startDate", "2026-01-01T00:00:00Z")
                        .queryParam("endDate", "2026-03-29T00:00:00Z")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content[0].id").isEqualTo("log-1")
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.first").isEqualTo(true);
    }

    // ──────────────────────────── getAuditLogById ────────────────────────────

    @Test
    void getAuditLogById_found_returns200() {
        given(auditService.getLogById("log-1")).willReturn(Mono.just(mockLog));

        webClient.get()
                .uri("/api/admin/audit-logs/log-1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("log-1")
                .jsonPath("$.actorUsername").isEqualTo("admin")
                .jsonPath("$.action").isEqualTo("USER_CREATED");
    }

    @Test
    void getAuditLogById_notFound_returns404() {
        given(auditService.getLogById("missing")).willReturn(Mono.empty());

        webClient.get()
                .uri("/api/admin/audit-logs/missing")
                .exchange()
                .expectStatus().isNotFound();
    }

    // ──────────────────────────── getAuditLogStats ────────────────────────────

    @Test
    void getAuditLogStats_returns200() {
        given(auditService.getAuditStats()).willReturn(Mono.just(statsResponse));

        webClient.get()
                .uri("/api/admin/audit-logs/stats")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalLogs").isEqualTo(10)
                .jsonPath("$.successCount").isEqualTo(8)
                .jsonPath("$.failureCount").isEqualTo(2);
    }
}
