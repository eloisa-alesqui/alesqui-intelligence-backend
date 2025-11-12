package es.alesqui.intelligence.model.access;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

/**
 * Associates an API with a Group for visibility control in MongoDB.
 * An API may be linked to zero, one or many groups.
 * If an API has zero links it is considered public (visible to all users).
 */
@Data
@Document("api_group_links")
@CompoundIndexes({
        @CompoundIndex(name = "uniq_api_group", def = "{ 'apiId': 1, 'groupId': 1 }", unique = true)
})
public class ApiGroupLink {

    /**
     * Unique identifier for the link document.
     */
    @Id
    private String id;

    /**
     * ID of the API document (referencing UnifiedApiDocument's id field).
     */
    private String apiId;

    /**
     * ID of the group receiving access to the API.
     */
    private String groupId;

    /**
     * Creation timestamp for auditing.
     */
    private Instant createdAt = Instant.now();
    
}
