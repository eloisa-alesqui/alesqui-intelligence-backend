package es.alesqui.intelligence.config;

import org.springframework.boot.context. properties.ConfigurationProperties;
import org.springframework.context.annotation. Configuration;

import lombok.Data;

/**
 * Configuration for deployment mode and company branding.
 * Used to customize user creation flows and email templates.
 */
@Configuration
@ConfigurationProperties(prefix = "app.deployment")
@Data
public class DeploymentConfig {
    
    /**
     * Deployment mode: TRIAL or CORPORATE
     */
    private DeploymentMode mode = DeploymentMode.TRIAL;
    
    /**
     * Company name for corporate deployments. 
     * Used in email templates and UI.
     */
    private String companyName = "Alesqui Intelligence";
    
    public enum DeploymentMode {
        TRIAL,      // Public trial version at www. alesqui.com
        CORPORATE   // Self-hosted corporate installation
    }
    
    public boolean isCorporate() {
        return mode == DeploymentMode.CORPORATE;
    }
    
    public boolean isTrial() {
        return mode == DeploymentMode.TRIAL;
    }
    
    /**
     * Self-registration is enabled only in TRIAL mode.
     * In CORPORATE mode, only admins can create users.
     */
    public boolean isSelfRegistrationEnabled() {
        return mode == DeploymentMode.TRIAL;
    }
}