package es.alesqui.intelligence.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Event published when a new API is created.
 * Listeners can react to this event to perform post-creation actions.
 */
@Getter
public class ApiCreatedEvent extends ApplicationEvent {
    
    private final String apiId;
    private final String username;
    
    public ApiCreatedEvent(Object source, String apiId, String username) {
        super(source);
        this.apiId = apiId;
        this.username = username;
    }
}
