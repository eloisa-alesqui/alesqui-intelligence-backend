package es.alesqui.intelligence.service.security;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Service;

import es.alesqui.intelligence.config.SecurityConfigurationProperties;
import es.alesqui.intelligence.config.TrialConfigurationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Service for IP-based rate limiting to prevent abuse of public endpoints.
 *
 * Purpose:
 * - Prevent multiple trial registrations from the same IP address
 * - Prevent password reset email flooding from the same IP address
 * - In-memory implementation suitable for single-instance deployments
 * - For multi-instance deployments, consider Redis-based implementation
 *
 * Business Rules:
 * - Configurable attempts per IP per time window
 * - Automatic cleanup of expired entries
 * - Thread-safe using ConcurrentHashMap and CopyOnWriteArrayList
 *
 * Configuration:
 * - trial.rate-limit.window-hours: Time window in hours for trial registration (default: 24)
 * - trial.rate-limit.max-attempts: Max trial registration attempts per window (default: 1)
 * - app.security.password-reset.window-minutes: Time window in minutes for password reset (default: 15)
 * - app.security.password-reset.max-attempts: Max password reset attempts per window (default: 3)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitingService {

    private final TrialConfigurationProperties trialConfig;
    private final SecurityConfigurationProperties securityConfig;


    private final ConcurrentHashMap<String, Instant> ipRegistrationAttempts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Instant>> ipPasswordResetAttempts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Instant>> ipOAuth2Attempts = new ConcurrentHashMap<>();

    private static final Duration CLEANUP_INTERVAL = Duration.ofHours(1);
    private volatile Instant lastCleanup = Instant.now();

    /**
     * Checks if an IP address is allowed to register for trial.
     *
     * @param ipAddress the client IP address
     * @return Mono<Boolean> true if allowed, false if rate limited
     */
    public Mono<Boolean> isAllowed(String ipAddress) {
        return Mono.fromCallable(() -> {
            performCleanupIfNeeded();

            Instant lastAttempt = ipRegistrationAttempts.get(ipAddress);

            if (lastAttempt == null) {
                return true;
            }

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
     * Records a trial registration attempt for the IP address.
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
     * Checks if an IP address is allowed to request a password reset.
     * Allows up to app.security.password-reset.max-attempts per window.
     *
     * @param ipAddress the client IP address
     * @return Mono<Boolean> true if allowed, false if rate limited
     */
    public Mono<Boolean> isPasswordResetAllowed(String ipAddress) {
        return Mono.fromCallable(() -> {
            performCleanupIfNeeded();

            CopyOnWriteArrayList<Instant> attempts = ipPasswordResetAttempts.get(ipAddress);
            if (attempts == null || attempts.isEmpty()) {
                return true;
            }

            Instant now = Instant.now();
            Duration window = Duration.ofMinutes(securityConfig.getPasswordReset().getWindowMinutes());

            long recentAttempts = attempts.stream()
                    .filter(attempt -> Duration.between(attempt, now).compareTo(window) < 0)
                    .count();

            boolean allowed = recentAttempts < securityConfig.getPasswordReset().getMaxAttempts();

            if (!allowed) {
                log.warn("[RateLimit] IP {} blocked for password reset - {} attempts in last {}min",
                        ipAddress, recentAttempts, securityConfig.getPasswordReset().getWindowMinutes());
            }

            return allowed;
        });
    }

    /**
     * Records a password reset attempt for the IP address.
     *
     * @param ipAddress the client IP address
     * @return Mono<Void> completion signal
     */
    public Mono<Void> recordPasswordResetAttempt(String ipAddress) {
        return Mono.fromRunnable(() -> {
            ipPasswordResetAttempts
                    .computeIfAbsent(ipAddress, k -> new CopyOnWriteArrayList<>())
                    .add(Instant.now());
            log.debug("[RateLimit] Recorded password reset attempt for IP {}", ipAddress);
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
            // Clean trial registration map
            Duration trialWindow = Duration.ofHours(trialConfig.getRateLimit().getWindowHours());
            int trialSizeBefore = ipRegistrationAttempts.size();
            ipRegistrationAttempts.entrySet().removeIf(entry -> {
                Duration age = Duration.between(entry.getValue(), now);
                return age.compareTo(trialWindow) >= 0;
            });
            int trialSizeAfter = ipRegistrationAttempts.size();
            if (trialSizeBefore != trialSizeAfter) {
                log.debug("[RateLimit] Cleaned up {} expired trial registration entries ({} -> {})",
                        trialSizeBefore - trialSizeAfter, trialSizeBefore, trialSizeAfter);
            }

            // Clean password reset map: remove expired instants from each list, then remove empty lists
            Duration resetWindow = Duration.ofMinutes(securityConfig.getPasswordReset().getWindowMinutes());
            ipPasswordResetAttempts.forEach((ip, attempts) ->
                    attempts.removeIf(attempt -> Duration.between(attempt, now).compareTo(resetWindow) >= 0));
            ipPasswordResetAttempts.entrySet().removeIf(entry -> entry.getValue().isEmpty());

            // Clean OAuth2 map
            Duration oauth2Window = Duration.ofMinutes(securityConfig.getOauth2().getWindowMinutes());
            ipOAuth2Attempts.forEach((ip, attempts) ->
                    attempts.removeIf(attempt -> Duration.between(attempt, now).compareTo(oauth2Window) >= 0));
            ipOAuth2Attempts.entrySet().removeIf(entry -> entry.getValue().isEmpty());

            lastCleanup = now;
        }
    }

    /**
     * Checks if an IP address is allowed to call an OAuth2 endpoint.
     *
     * @param ipAddress the client IP address
     * @return Mono<Boolean> true if allowed, false if rate limited
     */
    public Mono<Boolean> isOAuth2Allowed(String ipAddress) {
        return Mono.fromCallable(() -> {
            performCleanupIfNeeded();

            CopyOnWriteArrayList<Instant> attempts = ipOAuth2Attempts.get(ipAddress);
            if (attempts == null || attempts.isEmpty()) {
                return true;
            }

            Duration window = Duration.ofMinutes(securityConfig.getOauth2().getWindowMinutes());
            int maxAttempts = securityConfig.getOauth2().getMaxAttempts();
            Instant now = Instant.now();
            long recentAttempts = attempts.stream()
                    .filter(attempt -> Duration.between(attempt, now).compareTo(window) < 0)
                    .count();

            boolean allowed = recentAttempts < maxAttempts;

            if (!allowed) {
                log.warn("[RateLimit] IP {} blocked for OAuth2 - {} attempts in last {}min",
                        ipAddress, recentAttempts, securityConfig.getOauth2().getWindowMinutes());
            }

            return allowed;
        });
    }

    /**
     * Records an OAuth2 endpoint attempt for the IP address.
     *
     * @param ipAddress the client IP address
     * @return Mono<Void> completion signal
     */
    public Mono<Void> recordOAuth2Attempt(String ipAddress) {
        return Mono.fromRunnable(() -> {
            ipOAuth2Attempts
                    .computeIfAbsent(ipAddress, k -> new CopyOnWriteArrayList<>())
                    .add(Instant.now());
            log.debug("[RateLimit] Recorded OAuth2 attempt for IP {}", ipAddress);
        });
    }

    /**
     * Gets current statistics for monitoring.
     *
     * @return number of IPs currently being tracked for trial registration
     */
    public int getTrackedIpCount() {
        performCleanupIfNeeded();
        return ipRegistrationAttempts.size();
    }
}
