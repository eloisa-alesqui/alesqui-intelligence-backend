package es.alesqui.intelligence.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

import es.alesqui.intelligence.model.audit.AuditLog;
import reactor.core.publisher.Mono;

/**
 * Configuration component that creates MongoDB indexes for audit logs at application startup.
 * 
 * This component programmatically creates a TTL (Time To Live) index on the timestamp field
 * of audit log documents. The TTL value is read from {@link AuditRetentionConfig} which allows
 * configurable retention periods via application.properties.
 * 
 * TTL Index Behavior:
 * - MongoDB automatically deletes documents when timestamp + TTL expires
 * - Background cleanup task runs every 60 seconds
 * - Only applies to documents created after index creation
 * - Changing retention requires index recreation (automatic on restart)
 * 
 * Index Recreation:
 * On startup, this component drops the existing TTL index (if any) and recreates it
 * with the current configuration. This ensures the retention period is always up-to-date.
 * 
 * @see AuditRetentionConfig
 * @see AuditLog
 */
@Component
public class AuditLogIndexConfiguration {
    
    private static final Logger log = LoggerFactory.getLogger(AuditLogIndexConfiguration.class);
    private static final String TTL_INDEX_NAME = "timestamp_ttl_idx";
    private static final String COLLECTION_NAME = "audit_logs";
    
    private final ReactiveMongoTemplate mongoTemplate;
    private final AuditRetentionConfig retentionConfig;
    
    public AuditLogIndexConfiguration(ReactiveMongoTemplate mongoTemplate, AuditRetentionConfig retentionConfig) {
        this.mongoTemplate = mongoTemplate;
        this.retentionConfig = retentionConfig;
    }
    
    /**
     * Creates the TTL index on application startup.
     * Drops any existing TTL index first to ensure configuration changes take effect.
     */
    @PostConstruct
    public void createTtlIndex() {
        long retentionSeconds = retentionConfig.getRetentionSeconds();
        log.info("Initializing audit log TTL index with retention: {} days ({} seconds)", 
                 retentionConfig.getDays(), retentionSeconds);
        
        // Drop existing TTL index if it exists (to apply new retention settings)
        mongoTemplate.indexOps(COLLECTION_NAME)
            .dropIndex(TTL_INDEX_NAME)
            .onErrorResume(e -> {
                // Index might not exist, which is fine
                log.debug("No existing TTL index to drop: {}", e.getMessage());
                return Mono.empty();
            })
            .then(
                // Create new TTL index with current retention settings
                mongoTemplate.indexOps(COLLECTION_NAME)
                    .createIndex(new Index()
                        .named(TTL_INDEX_NAME)
                        .on("timestamp", Sort.Direction.ASC)
                        .expire(retentionSeconds))
            )
            .doOnSuccess(indexName -> 
                log.info("Successfully created audit log TTL index '{}' with {} days retention", 
                         indexName, retentionConfig.getDays()))
            .doOnError(e -> 
                log.error("Failed to create audit log TTL index", e))
            .subscribe();
    }
}
