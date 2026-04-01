package es.alesqui.intelligence.model.api_spec;

import java.time.LocalDateTime;

public interface ApiSpecDocument {
    String getId();
    String getName();
    LocalDateTime getCreatedAt();
    void setCreatedAt(LocalDateTime createdAt);
    LocalDateTime getUpdatedAt();
    void setUpdatedAt(LocalDateTime updatedAt);
}
