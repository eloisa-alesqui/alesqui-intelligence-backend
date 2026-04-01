package es.alesqui.intelligence.service.security;

import es.alesqui.intelligence.config.SecurityConfigurationProperties;
import es.alesqui.intelligence.config.TrialConfigurationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for RateLimitingService.
 * Verifies IP-based rate limiting logic for trial registration and password reset protection.
 */
class RateLimitingServiceTest {

    private RateLimitingService rateLimitingService;

    @BeforeEach
    void setUp() {
        TrialConfigurationProperties trialConfig = new TrialConfigurationProperties();
        trialConfig.getRateLimit().setWindowHours(24);
        SecurityConfigurationProperties securityConfig = new SecurityConfigurationProperties();
        rateLimitingService = new RateLimitingService(trialConfig, securityConfig);
    }

    @Nested
    @DisplayName("isAllowed()")
    class IsAllowed {

        @Test
        @DisplayName("allows an IP that has never registered")
        void allowsUnknownIp() {
            StepVerifier.create(rateLimitingService.isAllowed("192.168.1.1"))
                    .expectNext(true)
                    .verifyComplete();
        }

        @Test
        @DisplayName("blocks the same IP after a recorded attempt within the window")
        void blocksIpWithinWindow() {
            StepVerifier.create(
                            rateLimitingService.recordAttempt("10.0.0.1")
                                    .then(rateLimitingService.isAllowed("10.0.0.1"))
                    )
                    .expectNext(false)
                    .verifyComplete();
        }

        @Test
        @DisplayName("allows a different IP independently")
        void allowsDifferentIp() {
            StepVerifier.create(
                            rateLimitingService.recordAttempt("10.0.0.2")
                                    .then(rateLimitingService.isAllowed("10.0.0.3"))
                    )
                    .expectNext(true)
                    .verifyComplete();
        }

        @Test
        @DisplayName("allows an IP again when the rate limit window has passed (windowHours=0)")
        void allowsIpAfterWindowExpires() {
            // windowHours=0 means every attempt is immediately outside the window
            TrialConfigurationProperties config = new TrialConfigurationProperties();
            config.getRateLimit().setWindowHours(0);
            RateLimitingService service = new RateLimitingService(config, new SecurityConfigurationProperties());

            StepVerifier.create(
                            service.recordAttempt("172.16.0.1")
                                    .then(service.isAllowed("172.16.0.1"))
                    )
                    .expectNext(true)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("recordAttempt()")
    class RecordAttempt {

        @Test
        @DisplayName("completes without error")
        void completesSuccessfully() {
            StepVerifier.create(rateLimitingService.recordAttempt("192.168.0.1"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("overwriting an existing entry completes without error")
        void overwriteCompletesSuccessfully() {
            StepVerifier.create(
                            rateLimitingService.recordAttempt("192.168.0.2")
                                    .then(rateLimitingService.recordAttempt("192.168.0.2"))
                    )
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("getTrackedIpCount()")
    class GetTrackedIpCount {

        @Test
        @DisplayName("returns 0 when no IPs have been recorded")
        void zeroWhenEmpty() {
            assertThat(rateLimitingService.getTrackedIpCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("increments as IPs are recorded")
        void incrementsOnRecord() {
            rateLimitingService.recordAttempt("1.1.1.1").block();
            rateLimitingService.recordAttempt("2.2.2.2").block();

            assertThat(rateLimitingService.getTrackedIpCount()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("isPasswordResetAllowed()")
    class IsPasswordResetAllowed {

        @Test
        @DisplayName("allows an IP with no prior attempts")
        void allowsUnknownIp() {
            StepVerifier.create(rateLimitingService.isPasswordResetAllowed("192.168.1.1"))
                    .expectNext(true)
                    .verifyComplete();
        }

        @Test
        @DisplayName("allows up to maxAttempts attempts within the window")
        void allowsUpToMaxAttempts() {
            SecurityConfigurationProperties config = new SecurityConfigurationProperties();
            config.getPasswordReset().setWindowMinutes(15);
            config.getPasswordReset().setMaxAttempts(3);
            RateLimitingService service = new RateLimitingService(new TrialConfigurationProperties(), config);

            StepVerifier.create(
                            service.recordPasswordResetAttempt("10.0.0.5")
                                    .then(service.recordPasswordResetAttempt("10.0.0.5"))
                                    .then(service.isPasswordResetAllowed("10.0.0.5"))
                    )
                    .expectNext(true)
                    .verifyComplete();
        }

        @Test
        @DisplayName("blocks an IP that has reached maxAttempts within the window")
        void blocksAfterMaxAttempts() {
            SecurityConfigurationProperties config = new SecurityConfigurationProperties();
            config.getPasswordReset().setWindowMinutes(15);
            config.getPasswordReset().setMaxAttempts(3);
            RateLimitingService service = new RateLimitingService(new TrialConfigurationProperties(), config);

            StepVerifier.create(
                            service.recordPasswordResetAttempt("10.0.0.6")
                                    .then(service.recordPasswordResetAttempt("10.0.0.6"))
                                    .then(service.recordPasswordResetAttempt("10.0.0.6"))
                                    .then(service.isPasswordResetAllowed("10.0.0.6"))
                    )
                    .expectNext(false)
                    .verifyComplete();
        }

        @Test
        @DisplayName("allows a different IP independently")
        void allowsDifferentIp() {
            StepVerifier.create(
                            rateLimitingService.recordPasswordResetAttempt("10.0.0.7")
                                    .then(rateLimitingService.isPasswordResetAllowed("10.0.0.8"))
                    )
                    .expectNext(true)
                    .verifyComplete();
        }
    }
}
