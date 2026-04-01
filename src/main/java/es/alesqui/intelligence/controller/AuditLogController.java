package es.alesqui.intelligence.controller;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import es.alesqui.intelligence.dto.audit.AuditLogResponse;
import es.alesqui.intelligence.dto.audit.AuditLogSummaryResponse;
import es.alesqui.intelligence.dto.audit.AuditStatsResponse;
import es.alesqui.intelligence.dto.audit.PagedAuditLogResponse;
import es.alesqui.intelligence.model.audit.AuditAction;
import es.alesqui.intelligence.model.audit.AuditLog;
import es.alesqui.intelligence.model.audit.EntityType;
import es.alesqui.intelligence.service.audit.AuditService;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * REST controller for audit log querying and reporting.
 * Provides endpoints to retrieve, filter, and aggregate audit log entries.
 * All endpoints require ADMIN or SUPERADMIN role authorization.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Validated
@Slf4j
@PreAuthorize("hasRole('SUPERADMIN')")
public class AuditLogController {

    private final AuditService auditService;

    /**
     * Retrieves recent audit logs with optional pagination.
     *
     * @param limit maximum number of logs to retrieve (default: 100, max: 500)
     * @return a Flux of audit log summaries
     */
    @GetMapping("/audit-logs/recent")
    public Flux<AuditLogSummaryResponse> getRecentAuditLogs(
            @RequestParam(defaultValue = "100") @Min(1) int limit) {

        log.debug("Fetching {} recent audit logs", limit);

        int cappedLimit = Math.min(limit, 500);

        return auditService.getRecentLogs(cappedLimit)
                .map(this::mapToSummaryResponse);
    }

    /**
     * Retrieves paginated audit logs for a specific user.
     *
     * @param username the username to filter by
     * @param page the page number (zero-based, default: 0)
     * @param size the number of items per page (default: 50, max: 200)
     * @return a Mono containing paginated audit log details
     */
    @GetMapping("/audit-logs/user/{username}")
    public Mono<PagedAuditLogResponse<AuditLogResponse>> getAuditLogsByUser(
            @PathVariable String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        log.debug("Fetching audit logs for user: {} (page={}, size={})", username, page, size);

        int cappedSize = Math.min(size, 200);

        return auditService.countLogsByUser(username)
                .flatMap(totalElements ->
                    auditService.getLogsByUser(username, page, cappedSize)
                            .map(this::mapToFullResponse)
                            .collectList()
                            .map(content -> buildPagedResponse(content, page, cappedSize, totalElements))
                );
    }

    /**
     * Retrieves paginated audit logs for a specific entity.
     *
     * @param entityType the type of entity (USER, GROUP, API, etc.)
     * @param entityId the ID of the entity
     * @param page the page number (zero-based, default: 0)
     * @param size the number of items per page (default: 50, max: 200)
     * @return a Mono containing paginated audit log details
     */
    @GetMapping("/audit-logs/entity/{entityType}/{entityId}")
    public Mono<PagedAuditLogResponse<AuditLogResponse>> getAuditLogsByEntity(
            @PathVariable EntityType entityType,
            @PathVariable String entityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        log.debug("Fetching audit logs for entity: {} with ID: {} (page={}, size={})",
                  entityType, entityId, page, size);

        int cappedSize = Math.min(size, 200);

        return auditService.countLogsByEntity(entityType, entityId)
                .flatMap(totalElements ->
                    auditService.getLogsByEntity(entityType, entityId, page, cappedSize)
                            .map(this::mapToFullResponse)
                            .collectList()
                            .map(content -> buildPagedResponse(content, page, cappedSize, totalElements))
                );
    }

    /**
     * Retrieves paginated audit logs filtered by action type.
     *
     * @param action the audit action to filter by
     * @param page the page number (zero-based, default: 0)
     * @param size the number of items per page (default: 50, max: 200)
     * @return a Mono containing paginated audit log summaries
     */
    @GetMapping("/audit-logs/action/{action}")
    public Mono<PagedAuditLogResponse<AuditLogSummaryResponse>> getAuditLogsByAction(
            @PathVariable AuditAction action,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        log.debug("Fetching audit logs for action: {} (page={}, size={})", action, page, size);

        int cappedSize = Math.min(size, 200);

        return auditService.countLogsByAction(action)
                .flatMap(totalElements ->
                    auditService.getLogsByAction(action, page, cappedSize)
                            .map(this::mapToSummaryResponse)
                            .collectList()
                            .map(content -> buildPagedResponse(content, page, cappedSize, totalElements))
                );
    }

    /**
     * Retrieves paginated audit logs within a specific date range.
     *
     * @param startDate the start date (ISO 8601 format)
     * @param endDate the end date (ISO 8601 format)
     * @param page the page number (zero-based, default: 0)
     * @param size the number of items per page (default: 50, max: 200)
     * @return a Mono containing paginated audit log summaries
     */
    @GetMapping("/audit-logs/date-range")
    public Mono<PagedAuditLogResponse<AuditLogSummaryResponse>> getAuditLogsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) String startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) String endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        try {
            Instant start = Instant.parse(startDate);
            Instant end = Instant.parse(endDate);

            log.debug("Fetching audit logs from {} to {} (page={}, size={})", start, end, page, size);

            int cappedSize = Math.min(size, 200);

            return auditService.countLogsByDateRange(start, end)
                    .flatMap(totalElements ->
                        auditService.getLogsByDateRange(start, end, page, cappedSize)
                                .map(this::mapToSummaryResponse)
                                .collectList()
                                .map(content -> buildPagedResponse(content, page, cappedSize, totalElements))
                    );

        } catch (DateTimeParseException e) {
            log.error("Invalid date format provided: startDate={}, endDate={}", startDate, endDate);
            return Mono.error(new IllegalArgumentException("Invalid date format. Use ISO 8601 format: YYYY-MM-DDTHH:mm:ss.SSSZ"));
        }
    }

    /**
     * Retrieves detailed information about a specific audit log entry.
     *
     * @param logId the ID of the audit log entry
     * @return a ResponseEntity with full audit log details or 404 if not found
     */
    @GetMapping("/audit-logs/{logId}")
    public Mono<ResponseEntity<AuditLogResponse>> getAuditLogById(@PathVariable String logId) {
        log.debug("Fetching audit log by ID: {}", logId);

        return auditService.getLogById(logId)
                .map(this::mapToFullResponse)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Retrieves statistical summary of audit log data.
     *
     * @return a Mono containing audit statistics
     */
    @GetMapping("/audit-logs/stats")
    public Mono<AuditStatsResponse> getAuditLogStats() {
        log.debug("Fetching audit log statistics");

        return auditService.getAuditStats();
    }

    // ==================== HELPER METHODS ====================

    private <T> PagedAuditLogResponse<T> buildPagedResponse(List<T> content, int page, int size, long totalElements) {
        int totalPages = (int) Math.ceil((double) totalElements / size);

        return PagedAuditLogResponse.<T>builder()
                .content(content)
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .first(page == 0)
                .last(page >= totalPages - 1)
                .hasNext(page < totalPages - 1)
                .hasPrevious(page > 0)
                .build();
    }

    private AuditLogSummaryResponse mapToSummaryResponse(AuditLog log) {
        return AuditLogSummaryResponse.builder()
                .id(log.getId())
                .timestamp(log.getTimestamp())
                .actorUsername(log.getActorUsername())
                .action(log.getAction())
                .actionDescription(log.getAction().getDescription())
                .entityType(log.getEntityType())
                .entityName(log.getEntityName())
                .result(log.getResult())
                .build();
    }

    private AuditLogResponse mapToFullResponse(AuditLog log) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .timestamp(log.getTimestamp())
                .actorUsername(log.getActorUsername())
                .actorUserId(log.getActorUserId())
                .action(log.getAction())
                .actionDescription(log.getAction().getDescription())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .entityName(log.getEntityName())
                .details(log.getDetails())
                .ipAddress(log.getIpAddress())
                .userAgent(log.getUserAgent())
                .result(log.getResult())
                .errorMessage(log.getErrorMessage())
                .previousData(log.getPreviousData())
                .newData(log.getNewData())
                .build();
    }

}
