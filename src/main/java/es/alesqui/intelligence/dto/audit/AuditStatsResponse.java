package es.alesqui.intelligence.dto.audit;

import java.util.Map;

import es.alesqui.intelligence.model.audit.AuditAction;
import es.alesqui.intelligence.model.audit.EntityType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Statistical summary of audit log data.
 * 
 * Provides aggregated metrics about audit log activities,
 * useful for dashboard displays and reporting.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditStatsResponse {
    
    /** Total number of audit log entries */
    private long totalLogs;
    
    /** Number of successful operations */
    private long successCount;
    
    /** Number of failed operations */
    private long failureCount;
    
    /** Count of logs by action type */
    private Map<AuditAction, Long> actionCounts;
    
    /** Count of logs by entity type */
    private Map<EntityType, Long> entityTypeCounts;
    
    /** Count of logs by user */
    private Map<String, Long> userActivityCounts;
    
    /** Most recent activity timestamp */
    private String lastActivityTime;
}
