package es.alesqui.intelligence.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

/**
 * Configuration properties for audit log retention policy.
 * 
 * This configuration controls the Time To Live (TTL) for audit log entries
 * in MongoDB. Audit logs older than the configured retention period will be
 * automatically deleted by MongoDB's background cleanup process.
 * 
 * Default Retention: 730 days (2 years)
 * 
 * Configuration:
 * Set {@code audit.retention.days} in application.properties.
 * Example: {@code audit.retention.days=365} for 1 year retention.
 * 
 * Important Notes:
 * - Changes require application restart
 * - Existing logs are not immediately deleted; TTL only applies to new documents
 * - MongoDB TTL cleanup runs every 60 seconds
 * - For immediate cleanup after changing retention, drop and recreate the index
 * 
 * @see es.alesqui.intelligence.model.audit.AuditLog
 */
@Data
@Configuration("auditRetentionConfig")
@ConfigurationProperties(prefix = "audit.retention")
public class AuditRetentionConfig {
    
    /**
     * Number of days to retain audit logs before automatic deletion.
     * Default: 730 days (2 years).
     * Minimum recommended: 90 days for compliance purposes.
     */
    private int days = 730;
    
    /**
     * Calculates the retention period in seconds for MongoDB TTL index.
     * 
     * @return retention period in seconds (days * 24 * 60 * 60)
     */
    public long getRetentionSeconds() {
        return (long) days * 24 * 60 * 60;
    }
}
