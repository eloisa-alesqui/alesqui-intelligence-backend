package es.alesqui.intelligence.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * Configuration properties for trial user registration system.
 * 
 * Purpose:
 * - Centralizes all trial-related configuration
 * - Makes trial settings easily adjustable via application.properties
 * - Provides type-safe access to configuration values
 * 
 * Configuration properties (in application.properties):
 * - trial.duration.days: Trial period length in days (default: 14)
 * - trial.rate-limit.window-hours: Rate limit time window in hours (default: 24)
 * - trial.rate-limit.max-attempts: Max registration attempts per IP in window (default: 1)
 */
@Configuration
@ConfigurationProperties(prefix = "trial")
@Data
public class TrialConfigurationProperties {
    
    /**
     * Trial duration in days.
     * Default: 14 days
     */
    private int durationDays = 14;
    
    /**
     * Rate limiting configuration.
     */
    private RateLimit rateLimit = new RateLimit();
    
    /**
     * Rate limit settings for trial registration.
     */
    @Data
    public static class RateLimit {
        /**
         * Rate limit time window in hours.
         * Default: 24 hours
         */
        private int windowHours = 24;
        
        /**
         * Maximum registration attempts allowed per IP within the time window.
         * Default: 1 attempt
         */
        private int maxAttempts = 1;
    }
}
