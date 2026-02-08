package es.alesqui.intelligence.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

/**
 * Configuration properties for initial admin user creation.
 * 
 * Purpose:
 * - Configure the initial admin user created on first startup
 * - Only used in CORPORATE mode when no users exist
 * - Supports auto-generated passwords for security
 */
@Configuration
@ConfigurationProperties(prefix = "app.initial-admin")
@Data
public class InitialAdminProperties {
    
    /**
     * Email address for the initial admin user.
     * Default: admin@company.com
     */
    private String email = "admin@company.com";
    
    /**
     * Password for the initial admin user.
     * If empty or null, a secure random password will be generated.
     */
    private String password;
    
    /**
     * Application URL to display in the initial admin setup logs.
     * Default: http://localhost
     */
    private String appUrl = "http://localhost";
}
