package es.alesqui.intelligence.event;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import es.alesqui.intelligence.service.access.ApiGroupLinkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Listener for API creation events.
 * Handles post-creation actions such as auto-linking APIs to TRIAL user workspaces.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApiCreatedEventListener {
    
    /**
     * Service for API-Group link operations.
     */
    private final ApiGroupLinkService apiGroupLinkService;
    
    /**
     * Handles API creation events by auto-linking to TRIAL user workspaces if applicable.
     * 
     * @param event the API creation event
     */
    @EventListener
    public void handleApiCreated(ApiCreatedEvent event) {
        log.debug("Handling API created event for API: {}", event.getApiId());
        
        apiGroupLinkService.autoLinkApiToTrialWorkspace(event.getApiId(), event.getUsername())
            .doOnSuccess(v -> log.debug("Auto-link check completed for API: {}", event.getApiId()))
            .doOnError(e -> log.error("Failed to auto-link API {} for user {}", 
                event.getApiId(), event.getUsername(), e))
            .subscribe(); // Fire and forget
    }
}
