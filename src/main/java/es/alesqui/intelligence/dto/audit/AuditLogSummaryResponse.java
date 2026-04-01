package es.alesqui.intelligence.dto.audit;

import java.time.Instant;

import es.alesqui.intelligence.model.audit.AuditAction;
import es.alesqui.intelligence.model.audit.AuditResult;
import es.alesqui.intelligence.model.audit.EntityType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight summary of an audit log entry for list views.
 * 
 * This DTO contains only essential information for displaying audit logs
 * in table views, omitting detailed data like previousData and newData.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogSummaryResponse {
    
    /** Unique identifier of the audit log entry */
    private String id;
    
    /** Timestamp when the action was performed */
    private Instant timestamp;
    
    /** Username of the user who performed the action */
    private String actorUsername;
    
    /** Type of action performed */
    private AuditAction action;

    /** Human-readable description of the action */
    private String actionDescription;
    
    /** Type of entity affected */
    private EntityType entityType;
    
    /** Name or identifier of the entity */
    private String entityName;
    
    /** Result of the operation */
    private AuditResult result;
}
