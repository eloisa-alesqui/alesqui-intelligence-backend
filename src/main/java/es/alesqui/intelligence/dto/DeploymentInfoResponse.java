package es.alesqui.intelligence.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response containing deployment information for frontend customization.
 * Used by public endpoints to inform the UI about deployment mode and features.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentInfoResponse {
    
    /**
     * Deployment mode: TRIAL or CORPORATE
     */
    private String mode;
    
    /**
     * Company name for branding
     */
    private String companyName;
    
    /**
     * Whether self-registration is enabled (true for TRIAL, false for CORPORATE)
     */
    private boolean selfRegistrationEnabled;
}
