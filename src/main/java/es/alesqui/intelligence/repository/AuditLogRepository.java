package es.alesqui.intelligence.repository;

import es.alesqui.intelligence.model.audit.AuditAction;
import es.alesqui.intelligence.model.audit.AuditLog;
import es.alesqui.intelligence.model.audit.EntityType;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import java.time.Instant;

/**
 * Reactive repository for accessing audit log data in MongoDB.
 * 
 * This repository provides specialized query methods for retrieving
 * audit logs based on various criteria such as user, entity, action type,
 * and time range. All methods return reactive types (Flux/Mono) to support
 * non-blocking, asynchronous data access patterns.
 */
@Repository
public interface AuditLogRepository extends ReactiveMongoRepository<AuditLog, String> {
    
    /**
     * Finds all audit logs for a specific user, ordered by most recent first.
     * 
     * This method is useful for investigating all actions performed by a particular
     * user, for example during a security audit or troubleshooting user issues.
     * 
     * @param actorUsername the username of the user who performed the actions
     * @return a Flux emitting audit logs in descending timestamp order
     */
    Flux<AuditLog> findByActorUsernameOrderByTimestampDesc(String actorUsername);
    
    /**
     * Finds all audit logs for a specific entity (resource), ordered by most recent first.
     * 
     * This method retrieves the complete history of actions performed on a particular
     * entity, such as all modifications to a specific user account or group.
     * 
     * @param entityType the type of entity (USER, GROUP, API, etc.)
     * @param entityId the unique identifier of the entity
     * @return a Flux emitting audit logs in descending timestamp order
     */
    Flux<AuditLog> findByEntityTypeAndEntityIdOrderByTimestampDesc(EntityType entityType, String entityId);
    
    /**
     * Finds all audit logs of a specific action type, ordered by most recent first.
     * 
     * This method is useful for analyzing patterns of specific operations,
     * such as all user deletions or all failed login attempts.
     * 
     * @param action the type of action to filter by
     * @return a Flux emitting audit logs in descending timestamp order
     */
    Flux<AuditLog> findByActionOrderByTimestampDesc(AuditAction action);
    
    /**
     * Finds audit logs within a specific time range, ordered by most recent first.
     * 
     * This method enables time-based queries for compliance reporting,
     * performance analysis, or investigating incidents during a specific period.
     * 
     * @param start the start of the time range (inclusive)
     * @param end the end of the time range (inclusive)
     * @return a Flux emitting audit logs in descending timestamp order
     */
    Flux<AuditLog> findByTimestampBetweenOrderByTimestampDesc(Instant start, Instant end);
    
    /**
     * Finds the most recent audit logs, limited to a maximum number of results.
     * 
     * This method is optimized for dashboard displays and quick recent activity views.
     * The limit prevents memory issues when dealing with large audit log collections.
     * 
     * @return a Flux emitting up to 100 most recent audit logs
     */
    Flux<AuditLog> findTop100ByOrderByTimestampDesc();
}
