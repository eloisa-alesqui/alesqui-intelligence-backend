package es.alesqui.intelligence.dto.audit;

import java.time.Instant;
import java.util.List;

import es.alesqui.intelligence.model.audit.AuditAction;
import es.alesqui.intelligence.model.audit.AuditResult;
import es.alesqui.intelligence.model.audit.EntityType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for audit log entries.
 * 
 * This DTO provides a clean representation of audit log data for API responses,
 * including all relevant information about administrative actions performed in the system.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogResponse {
    
    /** Unique identifier of the audit log entry */
    private String id;
    
    /** Timestamp when the action was performed */
    private Instant timestamp;
    
    /** Username of the user who performed the action */
    private String actorUsername;
    
    /** User ID of the user who performed the action */
    private String actorUserId;
    
    /** Type of action performed */
    private AuditAction action;
    
    /** Human-readable description of the action */
    private String actionDescription;
    
    /** Type of entity affected by the action */
    private EntityType entityType;
    
    /** ID of the entity affected */
    private String entityId;
    
    /** Name or identifier of the entity */
    private String entityName;
    
    /** Additional details about the action (JSON format) */
    private String details;
    
    /** IP address of the client */
    private String ipAddress;
    
    /** User-Agent of the browser/client */
    private String userAgent;
    
    /** Result of the operation (SUCCESS or FAILURE) */
    private AuditResult result;
    
    /** Error message if result is FAILURE */
    private String errorMessage;
    
    /** Previous state of the entity (for UPDATE/DELETE operations) */
    private String previousData;
    
    /** New state of the entity (for CREATE/UPDATE operations) */
    private String newData;
}
