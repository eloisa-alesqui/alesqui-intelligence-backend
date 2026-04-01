package es.alesqui.intelligence.service.audit;

import com.fasterxml.jackson.databind.ObjectMapper;

import es.alesqui.intelligence.dto.audit.AuditStatsResponse;
import es.alesqui.intelligence.model.audit.*;
import es.alesqui.intelligence.repository.AuditLogRepository;
import es.alesqui.intelligence.service.identity.UserService;
import es.alesqui.intelligence.util.IpAddressExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for creating and querying audit log entries.
 * 
 * This service provides a comprehensive audit trail for all administrative
 * operations in the system. It captures who performed an action, what was
 * affected, when it occurred, and from where (IP address, user agent).
 * 
 * The service integrates with the UserService to automatically capture
 * the current authenticated user's information and with the HTTP request
 * context to extract technical metadata.
 * 
 * All audit log creation methods are designed to be fire-and-forget,
 * meaning they should not block or fail the main business operation if
 * logging encounters an error.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserService userService;
    private final ObjectMapper objectMapper;
    private final ReactiveMongoTemplate mongoTemplate;

    /**
     * Logs an administrative action with the current user's context.
     * 
     * This is the most commonly used method for audit logging. It automatically
     * captures the current authenticated user from the security context and
     * extracts technical metadata from the HTTP request.
     * 
     * @param action the type of action performed
     * @param entityType the type of entity affected
     * @param entityId the unique identifier of the affected entity
     * @param entityName the human-readable name of the affected entity
     * @param details optional additional details about the action
     * @param request the HTTP request context for extracting IP and user agent
     * @return a Mono that completes when the log is saved (or fails silently)
     */
    public Mono<Void> logAction(
            AuditAction action,
            EntityType entityType,
            String entityId,
            String entityName,
            String details,
            ServerHttpRequest request
    ) {
        return userService.getCurrentUser()
                .flatMap(user -> logAction(
                        action, entityType, entityId, entityName,
                        user.getUsername(), user.getId(),
                        details, extractIp(request), extractUserAgent(request),
                        AuditResult.SUCCESS, null, null, null
                ))
                .then()
                .onErrorResume(error -> {
                    log.error("Failed to log audit action: {} - {}", action, entityType, error);
                    return Mono.empty(); // Don't fail the main operation
                });
    }

    /**
     * Logs an action with explicit user information (when user context is not available).
     * 
     * Use this method when you need to log an action but the user is not yet authenticated
     * or when you need to log on behalf of a specific user.
     * 
     * @param action the type of action performed
     * @param entityType the type of entity affected
     * @param entityId the unique identifier of the affected entity
     * @param entityName the human-readable name of the affected entity
     * @param actorUsername the username of the user performing the action
     * @param actorUserId the user ID of the user performing the action
     * @param details optional additional details about the action
     * @param request the HTTP request context for extracting IP and user agent
     * @return a Mono that completes when the log is saved (or fails silently)
     */
    public Mono<Void> logActionWithUser(
            AuditAction action,
            EntityType entityType,
            String entityId,
            String entityName,
            String actorUsername,
            String actorUserId,
            String details,
            ServerHttpRequest request
    ) {
        return logAction(
                action, entityType, entityId, entityName,
                actorUsername, actorUserId,
                details, extractIp(request), extractUserAgent(request),
                AuditResult.SUCCESS, null, null, null
        )
        .then()
        .onErrorResume(error -> {
            log.error("Failed to log audit action: {} - {}", action, entityType, error);
            return Mono.empty(); // Don't fail the main operation
        });
    }

    /**
     * Logs a failed operation with explicit user information.
     * 
     * @param action the type of action that was attempted
     * @param entityType the type of entity involved
     * @param entityId the unique identifier of the entity
     * @param entityName the human-readable name of the entity
     * @param actorUsername the username of the user performing the action
     * @param errorMessage description of the error that occurred
     * @param request the HTTP request context
     * @return a Mono that completes when the log is saved
     */
    public Mono<Void> logFailureWithUser(
            AuditAction action,
            EntityType entityType,
            String entityId,
            String entityName,
            String actorUsername,
            String errorMessage,
            ServerHttpRequest request
    ) {
        return logAction(
                action, entityType, entityId, entityName,
                actorUsername, null,
                null, extractIp(request), extractUserAgent(request),
                AuditResult.FAILURE, errorMessage, null, null
        )
        .then()
        .onErrorResume(error -> {
            log.error("Failed to log audit failure: {} - {}", action, entityType, error);
            return Mono.empty();
        });
    }

    /**
     * Logs an action tracking state changes is important for compliance or rollback purposes.
     * The previous and new data are automatically serialized to JSON.
     * 
     * @param action the type of action performed
     * @param entityType the type of entity affected
     * @param entityId the unique identifier of the affected entity
     * @param entityName the human-readable name of the affected entity
     * @param previousData the state of the entity before the operation
     * @param newData the state of the entity after the operation
     * @param request the HTTP request context
     * @return a Mono that completes when the log is saved
     */
    public Mono<Void> logActionWithData(
            AuditAction action,
            EntityType entityType,
            String entityId,
            String entityName,
            Object previousData,
            Object newData,
            ServerHttpRequest request
    ) {
        return userService.getCurrentUser()
                .flatMap(user -> {
                    try {
                        String prevJson = previousData != null ? objectMapper.writeValueAsString(previousData) : null;
                        String newJson = newData != null ? objectMapper.writeValueAsString(newData) : null;
                        
                        return logAction(
                                action, entityType, entityId, entityName,
                                user.getUsername(), user.getId(),
                                null, extractIp(request), extractUserAgent(request),
                                AuditResult.SUCCESS, null, prevJson, newJson
                        );
                    } catch (Exception e) {
                        log.error("Error serializing audit data for action: {}", action, e);
                        return Mono.empty();
                    }
                })
                .then()
                .onErrorResume(error -> {
                    log.error("Failed to log audit action with data: {} - {}", action, entityType, error);
                    return Mono.empty();
                });
    }

    /**
     * Logs a failed operation with error details.
     * 
     * This method should be called from error handlers to record operations
     * that failed due to validation errors, permission issues, or unexpected
     * exceptions. It helps identify security incidents and system issues.
     * 
     * @param action the type of action that was attempted
     * @param entityType the type of entity involved
     * @param entityId the unique identifier of the entity
     * @param entityName the human-readable name of the entity
     * @param errorMessage description of the error that occurred
     * @param request the HTTP request context
     * @return a Mono that completes when the log is saved
     */
    public Mono<Void> logFailure(
            AuditAction action,
            EntityType entityType,
            String entityId,
            String entityName,
            String errorMessage,
            ServerHttpRequest request
    ) {
        return userService.getCurrentUser()
                .flatMap(user -> logAction(
                        action, entityType, entityId, entityName,
                        user.getUsername(), user.getId(),
                        null, extractIp(request), extractUserAgent(request),
                        AuditResult.FAILURE, errorMessage, null, null
                ))
                .onErrorResume(e -> {
                    // If we can't get the current user, log as SYSTEM
                    return logAction(
                            action, entityType, entityId, entityName,
                            "SYSTEM", null,
                            null, extractIp(request), extractUserAgent(request),
                            AuditResult.FAILURE, errorMessage, null, null
                    );
                })
                .then()
                .onErrorResume(error -> {
                    log.error("Failed to log audit failure: {} - {}", action, entityType, error);
                    return Mono.empty();
                });
    }

    /**
     * Internal method to create and persist an audit log entry.
     * 
     * This is the core logging method that all public methods delegate to.
     * It constructs the AuditLog entity and saves it to MongoDB.
     * 
     * @param action the type of action performed
     * @param entityType the type of entity affected
     * @param entityId the unique identifier of the entity
     * @param entityName the human-readable name of the entity
     * @param actorUsername the username of the user who performed the action
     * @param actorUserId the user ID of the user who performed the action
     * @param details optional additional details
     * @param ipAddress the client IP address
     * @param userAgent the client user agent string
     * @param result the result of the operation (SUCCESS or FAILURE)
     * @param errorMessage error message if result is FAILURE
     * @param previousData JSON representation of previous state
     * @param newData JSON representation of new state
     * @return a Mono emitting the saved audit log entry
     */
    private Mono<AuditLog> logAction(
            AuditAction action,
            EntityType entityType,
            String entityId,
            String entityName,
            String actorUsername,
            String actorUserId,
            String details,
            String ipAddress,
            String userAgent,
            AuditResult result,
            String errorMessage,
            String previousData,
            String newData
    ) {
        AuditLog auditLog = AuditLog.builder()
                .timestamp(Instant.now())
                .actorUsername(actorUsername)
                .actorUserId(actorUserId)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .entityName(entityName)
                .details(details)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .result(result)
                .errorMessage(errorMessage)
                .previousData(previousData)
                .newData(newData)
                .build();

        if (auditLog == null) {
            log.error("Failed to build audit log for action: {} - {}", action, entityType);
            return Mono.empty();
        }

        return auditLogRepository.save(auditLog)
                .doOnSuccess(saved -> log.debug("✅ Audit log created: {} - {} - {}", action, entityType, entityName))
                .doOnError(e -> log.error("❌ Failed to create audit log: {} - {}", action, entityType, e.getMessage()));
    }

    /**
     * Logs a failed user operation for audit purposes.
     * Used in error handlers to track failed administrative actions on users.
     *
     * @param action       the action that was attempted
     * @param userId       the user ID (if known)
     * @param username     the username (if known)
     * @param errorMessage the error message
     * @param request      the HTTP request for audit logging
     * @return Mono signaling completion
     */
    public Mono<Void> logUserOperationFailure(AuditAction action, String userId, String username,
            String errorMessage, ServerHttpRequest request) {
        return logFailure(
                action,
                EntityType.USER,
                userId != null ? userId : "unknown",
                username != null ? username : "unknown",
                errorMessage,
                request);
    }

    // ==================== Query Methods ====================

    /**
     * Retrieves the most recent audit logs.
     * 
     * @param limit maximum number of logs to retrieve
     * @return a Flux emitting the most recent audit logs
     */
    public Flux<AuditLog> getRecentLogs(int limit) {
        return auditLogRepository.findTop100ByOrderByTimestampDesc()
                .take(limit);
    }

    /**
     * Retrieves all audit logs for a specific user.
     * 
     * @param username the username to filter by
     * @return a Flux emitting all logs for the specified user
     */
    public Flux<AuditLog> getLogsByUser(String username) {
        return auditLogRepository.findByActorUsernameOrderByTimestampDesc(username);
    }

    /**
     * Retrieves paginated audit logs for a specific user.
     * 
     * @param username the username to filter by
     * @param page the page number (zero-based)
     * @param size the number of items per page
     * @return a Flux emitting audit logs for the specified page
     */
    public Flux<AuditLog> getLogsByUser(String username, int page, int size) {
        return auditLogRepository.findByActorUsernameOrderByTimestampDesc(username)
                .skip((long) page * size)
                .take(size);
    }

    /**
     * Counts total audit logs for a specific user.
     * 
     * @param username the username to filter by
     * @return a Mono emitting the total count
     */
    public Mono<Long> countLogsByUser(String username) {
        return auditLogRepository.findByActorUsernameOrderByTimestampDesc(username)
                .count();
    }

    /**
     * Retrieves all audit logs for a specific entity.
     * 
     * @param entityType the type of entity
     * @param entityId the unique identifier of the entity
     * @return a Flux emitting all logs for the specified entity
     */
    public Flux<AuditLog> getLogsByEntity(EntityType entityType, String entityId) {
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByTimestampDesc(entityType, entityId);
    }

    /**
     * Retrieves paginated audit logs for a specific entity.
     * 
     * @param entityType the type of entity
     * @param entityId the unique identifier of the entity
     * @param page the page number (zero-based)
     * @param size the number of items per page
     * @return a Flux emitting audit logs for the specified page
     */
    public Flux<AuditLog> getLogsByEntity(EntityType entityType, String entityId, int page, int size) {
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByTimestampDesc(entityType, entityId)
                .skip((long) page * size)
                .take(size);
    }

    /**
     * Counts total audit logs for a specific entity.
     * 
     * @param entityType the type of entity
     * @param entityId the unique identifier of the entity
     * @return a Mono emitting the total count
     */
    public Mono<Long> countLogsByEntity(EntityType entityType, String entityId) {
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByTimestampDesc(entityType, entityId)
                .count();
    }

    /**
     * Retrieves all audit logs of a specific action type.
     * 
     * @param action the action type to filter by
     * @return a Flux emitting all logs of the specified action type
     */
    public Flux<AuditLog> getLogsByAction(AuditAction action) {
        return auditLogRepository.findByActionOrderByTimestampDesc(action);
    }

    /**
     * Retrieves paginated audit logs of a specific action type.
     * 
     * @param action the action type to filter by
     * @param page the page number (zero-based)
     * @param size the number of items per page
     * @return a Flux emitting audit logs for the specified page
     */
    public Flux<AuditLog> getLogsByAction(AuditAction action, int page, int size) {
        return auditLogRepository.findByActionOrderByTimestampDesc(action)
                .skip((long) page * size)
                .take(size);
    }

    /**
     * Counts total audit logs for a specific action type.
     * 
     * @param action the action type to filter by
     * @return a Mono emitting the total count
     */
    public Mono<Long> countLogsByAction(AuditAction action) {
        return auditLogRepository.findByActionOrderByTimestampDesc(action)
                .count();
    }

    /**
     * Retrieves audit logs within a specific date range.
     * 
     * @param start the start of the time range (inclusive)
     * @param end the end of the time range (inclusive)
     * @return a Flux emitting logs within the specified range
     */
    public Flux<AuditLog> getLogsByDateRange(Instant start, Instant end) {
        return auditLogRepository.findByTimestampBetweenOrderByTimestampDesc(start, end);
    }

    /**
     * Retrieves paginated audit logs within a specific date range.
     * 
     * @param start the start of the time range (inclusive)
     * @param end the end of the time range (inclusive)
     * @param page the page number (zero-based)
     * @param size the number of items per page
     * @return a Flux emitting audit logs for the specified page
     */
    public Flux<AuditLog> getLogsByDateRange(Instant start, Instant end, int page, int size) {
        return auditLogRepository.findByTimestampBetweenOrderByTimestampDesc(start, end)
                .skip((long) page * size)
                .take(size);
    }

    /**
     * Counts total audit logs within a specific date range.
     * 
     * @param start the start of the time range (inclusive)
     * @param end the end of the time range (inclusive)
     * @return a Mono emitting the total count
     */
    public Mono<Long> countLogsByDateRange(Instant start, Instant end) {
        return auditLogRepository.findByTimestampBetweenOrderByTimestampDesc(start, end)
                .count();
    }

    /**
     * Retrieves a specific audit log by its ID.
     * 
     * @param logId the unique identifier of the audit log
     * @return a Mono emitting the audit log if found
     */
    public Mono<AuditLog> getLogById(String logId) {
        if (logId == null) {
            return Mono.empty();
        }
        return auditLogRepository.findById(logId);
    }

    /**
     * Retrieves statistical summary of audit log data.
     * 
     * Aggregates metrics about:
     * - Total logs
     * - Success/failure rates
     * - Action type distribution
     * - Entity type distribution
     * - User activity levels
     * 
     * @return Mono containing audit statistics
     */
    public Mono<AuditStatsResponse> getAuditStats() {
        return mongoTemplate.aggregate(
            Aggregation.newAggregation(
                Aggregation.facet(
                    Aggregation.count().as("count")
                ).as("total")
                .and(
                    Aggregation.group("result").count().as("count")
                ).as("resultCounts")
                .and(
                    Aggregation.group("action").count().as("count")
                ).as("actionCounts")
                .and(
                    Aggregation.group("entityType").count().as("count")
                ).as("entityTypeCounts")
                .and(
                    Aggregation.group("actorUsername").count().as("count")
                ).as("userActivityCounts")
                .and(
                    Aggregation.sort(Sort.Direction.DESC, "timestamp"),
                    Aggregation.limit(1),
                    Aggregation.project("timestamp")
                ).as("lastActivity")
            ),
            "audit_logs",
            Document.class
        )
        .next()
        .map(this::mapAuditStatsFromAggregation)
        .defaultIfEmpty(buildEmptyStats());
    }

    private AuditStatsResponse mapAuditStatsFromAggregation(Document doc) {
        List<Document> totalList = doc.getList("total", Document.class);
        long totalLogs = totalList.isEmpty() ? 0L : ((Number) totalList.get(0).get("count")).longValue();

        List<Document> resultCountsList = doc.getList("resultCounts", Document.class);
        long successCount = 0L;
        long failureCount = 0L;
        for (Document rc : resultCountsList) {
            String id = rc.getString("_id");
            long count = ((Number) rc.get("count")).longValue();
            if ("SUCCESS".equals(id)) {
                successCount = count;
            } else if ("FAILURE".equals(id)) {
                failureCount = count;
            }
        }

        List<Document> actionCountsList = doc.getList("actionCounts", Document.class);
        Map<AuditAction, Long> actionCounts = new HashMap<>();
        for (Document ac : actionCountsList) {
            String id = ac.getString("_id");
            long count = ((Number) ac.get("count")).longValue();
            try {
                actionCounts.put(AuditAction.valueOf(id), count);
            } catch (IllegalArgumentException e) {
                log.warn("Unknown AuditAction value in aggregation: {}", id);
            }
        }

        List<Document> entityTypeCountsList = doc.getList("entityTypeCounts", Document.class);
        Map<EntityType, Long> entityTypeCounts = new HashMap<>();
        for (Document etc : entityTypeCountsList) {
            String id = etc.getString("_id");
            long count = ((Number) etc.get("count")).longValue();
            try {
                entityTypeCounts.put(EntityType.valueOf(id), count);
            } catch (IllegalArgumentException e) {
                log.warn("Unknown EntityType value in aggregation: {}", id);
            }
        }

        List<Document> userActivityCountsList = doc.getList("userActivityCounts", Document.class);
        Map<String, Long> userActivityCounts = new HashMap<>();
        for (Document uac : userActivityCountsList) {
            String id = uac.getString("_id");
            long count = ((Number) uac.get("count")).longValue();
            if (id != null) {
                userActivityCounts.put(id, count);
            }
        }

        List<Document> lastActivityList = doc.getList("lastActivity", Document.class);
        String lastActivityTime = "N/A";
        if (!lastActivityList.isEmpty()) {
            Object ts = lastActivityList.get(0).get("timestamp");
            if (ts instanceof java.util.Date date) {
                lastActivityTime = date.toInstant().toString();
            } else if (ts != null) {
                lastActivityTime = ts.toString();
            }
        }

        return AuditStatsResponse.builder()
                .totalLogs(totalLogs)
                .successCount(successCount)
                .failureCount(failureCount)
                .actionCounts(actionCounts)
                .entityTypeCounts(entityTypeCounts)
                .userActivityCounts(userActivityCounts)
                .lastActivityTime(lastActivityTime)
                .build();
    }

    private AuditStatsResponse buildEmptyStats() {
        return AuditStatsResponse.builder()
                .totalLogs(0)
                .successCount(0)
                .failureCount(0)
                .actionCounts(Map.of())
                .entityTypeCounts(Map.of())
                .userActivityCounts(Map.of())
                .lastActivityTime("N/A")
                .build();
    }

    

    // ==================== Utility Methods ====================

    /**
     * Extracts the client IP address from the HTTP request.
     *
     * Delegates to {@link IpAddressExtractor#extract} which only trusts proxy
     * headers (X-Forwarded-For, X-Real-IP) when the direct TCP peer is a
     * private/loopback address, preventing IP spoofing in audit logs.
     *
     * @param request the HTTP request
     * @return the client IP address, or "unknown" if not available
     */
    private String extractIp(ServerHttpRequest request) {
        return IpAddressExtractor.extract(request);
    }

    /**
     * Extracts the User-Agent header from the HTTP request.
     * 
     * @param request the HTTP request
     * @return the User-Agent string, or null if not present
     */
    private String extractUserAgent(ServerHttpRequest request) {
        return request.getHeaders().getFirst("User-Agent");
    }
}
