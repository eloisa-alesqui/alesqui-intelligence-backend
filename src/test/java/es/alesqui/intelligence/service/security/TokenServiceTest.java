package es.alesqui.intelligence.service.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import java.time.temporal.ChronoUnit;

/**
 * Unit tests for TokenService.
 * Verifies cryptographic token generation and expiration calculation logic.
 */
class TokenServiceTest {

    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        tokenService = new TokenService();
    }

    @Nested
    @DisplayName("generateSecureToken()")
    class GenerateSecureToken {

        @Test
        @DisplayName("returns a 64-character string")
        void returnsCorrectLength() {
            String token = tokenService.generateSecureToken();
            assertThat(token).hasSize(64);
        }

        @Test
        @DisplayName("returns only lowercase hexadecimal characters")
        void returnsHexFormat() {
            String token = tokenService.generateSecureToken();
            assertThat(token).matches("[0-9a-f]{64}");
        }

        @Test
        @DisplayName("returns unique tokens on successive calls")
        void returnsUniqueTokens() {
            String token1 = tokenService.generateSecureToken();
            String token2 = tokenService.generateSecureToken();
            assertThat(token1).isNotEqualTo(token2);
        }
    }

    @Nested
    @DisplayName("calculateExpiration(hours)")
    class CalculateExpiration {

        @Test
        @DisplayName("returns Instant approximately 1 hour in the future")
        void oneHour() {
            Instant before = Instant.now().plusSeconds(3600 - 5);
            Instant expiration = tokenService.calculateExpiration(1);
            Instant after = Instant.now().plusSeconds(3600 + 5);

            assertThat(expiration).isBetween(before, after);
        }

        @Test
        @DisplayName("returns Instant approximately 48 hours in the future")
        void fortyEightHours() {
            Instant before = Instant.now().plusSeconds(48 * 3600 - 5);
            Instant expiration = tokenService.calculateExpiration(48);
            Instant after = Instant.now().plusSeconds(48 * 3600 + 5);

            assertThat(expiration).isBetween(before, after);
        }

        @Test
        @DisplayName("returns Instant at approximately now for 0 hours")
        void zeroHours() {
            Instant expiration = tokenService.calculateExpiration(0);
            assertThat(expiration).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS));
        }
    }

    @Nested
    @DisplayName("calculateActivationExpiration()")
    class CalculateActivationExpiration {

        @Test
        @DisplayName("returns Instant approximately 48 hours from now")
        void returns48HoursFromNow() {
            Instant before = Instant.now().plusSeconds(48 * 3600 - 5);
            Instant expiration = tokenService.calculateActivationExpiration();
            Instant after = Instant.now().plusSeconds(48 * 3600 + 5);

            assertThat(expiration).isBetween(before, after);
        }
    }

    @Nested
    @DisplayName("calculatePasswordResetExpiration()")
    class CalculatePasswordResetExpiration {

        @Test
        @DisplayName("returns Instant approximately 1 hour from now")
        void returns1HourFromNow() {
            Instant before = Instant.now().plusSeconds(3600 - 5);
            Instant expiration = tokenService.calculatePasswordResetExpiration();
            Instant after = Instant.now().plusSeconds(3600 + 5);

            assertThat(expiration).isBetween(before, after);
        }
    }
}
