package es.alesqui.intelligence.service.security;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import es.alesqui.intelligence.config.TrialConfigurationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Service for IP-based rate limiting to prevent abuse of public endpoints.
 * 
 * Purpose:
 * - Prevent multiple trial registrations from the same IP address
 * - In-memory implementation suitable for single-instance deployments
 * - For multi-instance deployments, consider Redis-based implementation
 * 
 * Business Rules:
 * - Configurable attempts per IP per time window (default: 1 per 24 hours)
 * - Automatic cleanup of expired entries
 * - Thread-safe using ConcurrentHashMap
 * 
 * Configuration:
 * - trial.rate-limit.window-hours: Time window in hours (default: 24)
 * - trial.rate-limit.max-attempts: Max attempts per window (default: 1)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitingService {
    
    private final TrialConfigurationProperties trialConfig;
    private final ConcurrentHashMap<String, Instant> ipRegistrationAttempts = new ConcurrentHashMap<>();
    private static final Duration CLEANUP_INTERVAL = Duration.ofHours(1);
    
    private Instant lastCleanup = Instant.now();
    
    /**
     * Checks if an IP address is allowed to register for trial.
     * 
     * @param ipAddress the client IP address
     * @return Mono<Boolean> true if allowed, false if rate limited
     */
    public Mono<Boolean> isAllowed(String ipAddress) {
        return Mono.fromCallable(() -> {
            // Periodic cleanup of expired entries
            performCleanupIfNeeded();
            
            Instant lastAttempt = ipRegistrationAttempts.get(ipAddress);
            
            if (lastAttempt == null) {
                // First attempt from this IP
                return true;
            }
            
            // Check if enough time has passed since last attempt
            Instant now = Instant.now();
            Duration timeSinceLastAttempt = Duration.between(lastAttempt, now);
            Duration rateLimitWindow = Duration.ofHours(trialConfig.getRateLimit().getWindowHours());
            
            boolean allowed = timeSinceLastAttempt.compareTo(rateLimitWindow) >= 0;
            
            if (!allowed) {
                log.warn("[RateLimit] IP {} blocked - last attempt was {} ago (limit: {}h)", 
                    ipAddress, timeSinceLastAttempt, trialConfig.getRateLimit().getWindowHours());
            }
            
            return allowed;
        });
    }
    
    /**
     * Records a successful trial registration for the IP address.
     * 
     * @param ipAddress the client IP address
     * @return Mono<Void> completion signal
     */
    public Mono<Void> recordAttempt(String ipAddress) {
        return Mono.fromRunnable(() -> {
            ipRegistrationAttempts.put(ipAddress, Instant.now());
            log.debug("[RateLimit] Recorded trial registration for IP {}", ipAddress);
        });
    }
    
    /**
     * Performs cleanup of expired rate limit entries.
     * Called periodically to prevent memory leaks.
     */
    private void performCleanupIfNeeded() {
        Instant now = Instant.now();
        Duration timeSinceLastCleanup = Duration.between(lastCleanup, now);
        
        if (timeSinceLastCleanup.compareTo(CLEANUP_INTERVAL) >= 0) {
            Duration rateLimitWindow = Duration.ofHours(trialConfig.getRateLimit().getWindowHours());
            int sizeBefore = ipRegistrationAttempts.size();
            
            ipRegistrationAttempts.entrySet().removeIf(entry -> {
                Duration age = Duration.between(entry.getValue(), now);
                return age.compareTo(rateLimitWindow) >= 0;
            });
            
            int sizeAfter = ipRegistrationAttempts.size();
            lastCleanup = now;
            
            if (sizeBefore != sizeAfter) {
                log.debug("[RateLimit] Cleaned up {} expired entries ({} -> {})", 
                    sizeBefore - sizeAfter, sizeBefore, sizeAfter);
            }
        }
    }
    
    /**
     * Gets current statistics for monitoring.
     * 
     * @return number of IPs currently being tracked
     */
    public int getTrackedIpCount() {
        performCleanupIfNeeded();
        return ipRegistrationAttempts.size();
    }
}
