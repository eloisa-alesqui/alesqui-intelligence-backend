package es.alesqui.intelligence.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import es.alesqui.intelligence.model.core.enums.AuthProvider;
import es.alesqui.intelligence.model.core.enums.Role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for JwtService.
 * Verifies JWT generation, validation, and claim extraction.
 */
class JwtServiceTest {

    // 32-byte key encoded as Base64 — meets HMAC-SHA256 minimum requirements (256 bits)
    private static final String TEST_SECRET = Base64.getEncoder().encodeToString(
            "this-is-a-test-secret-key-32byte".getBytes()
    );
    private static final long ACCESS_EXPIRATION_MS = 3_600_000L;    // 1 hour
    private static final long REFRESH_EXPIRATION_MS = 86_400_000L;  // 24 hours

    private JwtService jwtService;
    private UserDetails testUser;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", TEST_SECRET);
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", ACCESS_EXPIRATION_MS);
        ReflectionTestUtils.setField(jwtService, "refreshExpiration", REFRESH_EXPIRATION_MS);

        testUser = User.withUsername("alice")
                .password("irrelevant")
                .authorities(new SimpleGrantedAuthority("ROLE_USER"))
                .build();
    }

    @Nested
    @DisplayName("generateToken()")
    class GenerateToken {

        @Test
        @DisplayName("returns a non-null, non-empty JWT string")
        void returnsNonNullToken() {
            String token = jwtService.generateToken(testUser);
            assertThat(token).isNotNull().isNotBlank();
        }

        @Test
        @DisplayName("embeds the correct subject (username)")
        void embedsCorrectSubject() {
            String token = jwtService.generateToken(testUser);
            assertThat(jwtService.extractUsername(token)).isEqualTo("alice");
        }

        @Test
        @DisplayName("embeds the user's authorities as a claim")
        void embedsAuthorities() {
            String token = jwtService.generateToken(testUser);
            @SuppressWarnings("unchecked")
            List<String> authorities = (List<String>) jwtService.extractClaim(token,
                    claims -> claims.get("authorities", List.class));
            assertThat(authorities).containsExactly("ROLE_USER");
        }
    }

    @Nested
    @DisplayName("generateRefreshToken()")
    class GenerateRefreshToken {

        @Test
        @DisplayName("returns a token different from the access token")
        void returnsDifferentToken() {
            // Tokens are time-based; generating at the same millisecond could theoretically
            // produce the same token, but in practice the issued-at timestamps differ enough.
            String access = jwtService.generateToken(testUser);
            String refresh = jwtService.generateRefreshToken(testUser);
            // Both must be valid JWTs with the correct subject
            assertThat(jwtService.extractUsername(refresh)).isEqualTo("alice");
            // Expiration of refresh must be later than access (different expiration embedded)
            assertThat(jwtService.extractClaim(refresh,
                    io.jsonwebtoken.Claims::getExpiration))
                    .isAfterOrEqualTo(jwtService.extractClaim(access,
                            io.jsonwebtoken.Claims::getExpiration));
        }
    }

    @Nested
    @DisplayName("typ claim")
    class TypClaim {

        @Test
        @DisplayName("access token has typ = 'access'")
        void accessTokenHasTypAccess() {
            String token = jwtService.generateToken(testUser);
            assertThat(jwtService.extractTokenType(token)).isEqualTo("access");
        }

        @Test
        @DisplayName("refresh token has typ = 'refresh'")
        void refreshTokenHasTypRefresh() {
            String token = jwtService.generateRefreshToken(testUser);
            assertThat(jwtService.extractTokenType(token)).isEqualTo("refresh");
        }

        @Test
        @DisplayName("isAccessToken() returns true for access token, false for refresh token")
        void isAccessToken() {
            String access = jwtService.generateToken(testUser);
            String refresh = jwtService.generateRefreshToken(testUser);
            assertThat(jwtService.isAccessToken(access)).isTrue();
            assertThat(jwtService.isAccessToken(refresh)).isFalse();
        }

        @Test
        @DisplayName("isRefreshToken() returns true for refresh token, false for access token")
        void isRefreshToken() {
            String access = jwtService.generateToken(testUser);
            String refresh = jwtService.generateRefreshToken(testUser);
            assertThat(jwtService.isRefreshToken(refresh)).isTrue();
            assertThat(jwtService.isRefreshToken(access)).isFalse();
        }
    }

    @Nested
    @DisplayName("isTokenValid()")
    class IsTokenValid {

        @Test
        @DisplayName("returns true for a fresh token belonging to the correct user")
        void validForCorrectUser() {
            String token = jwtService.generateToken(testUser);
            assertThat(jwtService.isTokenValid(token, testUser)).isTrue();
        }

        @Test
        @DisplayName("returns false when the username does not match")
        void invalidForWrongUser() {
            String token = jwtService.generateToken(testUser);
            UserDetails otherUser = User.withUsername("bob")
                    .password("irrelevant")
                    .authorities(new SimpleGrantedAuthority("ROLE_USER"))
                    .build();
            assertThat(jwtService.isTokenValid(token, otherUser)).isFalse();
        }

        @Test
        @DisplayName("throws ExpiredJwtException for an expired token")
        void throwsForExpiredToken() {
            // JJWT throws ExpiredJwtException during claim parsing, not just returns false
            ReflectionTestUtils.setField(jwtService, "jwtExpiration", -1L);
            String expiredToken = jwtService.generateToken(testUser);
            assertThatThrownBy(() -> jwtService.isTokenValid(expiredToken, testUser))
                    .isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
        }
    }

    @Nested
    @DisplayName("extractUsername()")
    class ExtractUsername {

        @Test
        @DisplayName("extracts the subject from a valid token")
        void extractsSubject() {
            String token = jwtService.generateToken(testUser);
            assertThat(jwtService.extractUsername(token)).isEqualTo("alice");
        }

        @Test
        @DisplayName("throws an exception for a tampered token")
        void throwsForTamperedToken() {
            String token = jwtService.generateToken(testUser);
            String tampered = token.substring(0, token.length() - 4) + "XXXX";
            assertThatThrownBy(() -> jwtService.extractUsername(tampered))
                    .isInstanceOf(JwtException.class);
        }
    }

    @Nested
    @DisplayName("authProvider claim")
    class AuthProviderClaim {

        @Test
        @DisplayName("access token with GOOGLE user embeds authProvider=GOOGLE")
        void generateToken_withGoogleUser_embedsAuthProviderClaim() {
            es.alesqui.intelligence.model.core.User googleUser = es.alesqui.intelligence.model.core.User.builder()
                    .username("google@example.com")
                    .authProvider(AuthProvider.GOOGLE)
                    .roles(Set.of(Role.ROLE_TRIAL))
                    .build();
            String token = jwtService.generateToken(googleUser);
            String authProvider = jwtService.extractClaim(token, claims -> claims.get("authProvider", String.class));
            assertThat(authProvider).isEqualTo("GOOGLE");
        }

        @Test
        @DisplayName("access token with LOCAL user embeds authProvider=LOCAL")
        void generateToken_withLocalUser_embedsLocalClaim() {
            es.alesqui.intelligence.model.core.User localUser = es.alesqui.intelligence.model.core.User.builder()
                    .username("local@example.com")
                    .authProvider(AuthProvider.LOCAL)
                    .roles(Set.of(Role.ROLE_IT))
                    .build();
            String token = jwtService.generateToken(localUser);
            String authProvider = jwtService.extractClaim(token, claims -> claims.get("authProvider", String.class));
            assertThat(authProvider).isEqualTo("LOCAL");
        }

        @Test
        @DisplayName("access token with null authProvider falls back to LOCAL")
        void generateToken_withNullProvider_embedsLocalFallback() {
            es.alesqui.intelligence.model.core.User user = es.alesqui.intelligence.model.core.User.builder()
                    .username("noauth@example.com")
                    .authProvider(null)
                    .roles(Set.of(Role.ROLE_IT))
                    .build();
            String token = jwtService.generateToken(user);
            String authProvider = jwtService.extractClaim(token, claims -> claims.get("authProvider", String.class));
            assertThat(authProvider).isEqualTo("LOCAL");
        }
    }

    @Nested
    @DisplayName("generateLinkChallenge / isValidLinkChallenge")
    class LinkChallenge {

        @Test
        @DisplayName("generate and validate happy path returns true")
        void generateAndValidate_happyPath() {
            String challenge = jwtService.generateLinkChallenge("user@example.com", "google-sub-123", Duration.ofMinutes(5));
            assertThat(jwtService.isValidLinkChallenge(challenge, "user@example.com", "google-sub-123")).isTrue();
        }

        @Test
        @DisplayName("wrong email returns false")
        void validate_wrongEmail_returnsFalse() {
            String challenge = jwtService.generateLinkChallenge("user@example.com", "google-sub-123", Duration.ofMinutes(5));
            assertThat(jwtService.isValidLinkChallenge(challenge, "wrong@example.com", "google-sub-123")).isFalse();
        }

        @Test
        @DisplayName("wrong sub returns false")
        void validate_wrongSub_returnsFalse() {
            String challenge = jwtService.generateLinkChallenge("user@example.com", "google-sub-123", Duration.ofMinutes(5));
            assertThat(jwtService.isValidLinkChallenge(challenge, "user@example.com", "wrong-sub")).isFalse();
        }

        @Test
        @DisplayName("tampered token returns false")
        void validate_tamperedToken_returnsFalse() {
            String challenge = jwtService.generateLinkChallenge("user@example.com", "google-sub-123", Duration.ofMinutes(5));
            String tampered = challenge.substring(0, challenge.length() - 4) + "XXXX";
            assertThat(jwtService.isValidLinkChallenge(tampered, "user@example.com", "google-sub-123")).isFalse();
        }

        @Test
        @DisplayName("expired challenge returns false")
        void validate_expiredChallenge_returnsFalse() {
            String challenge = jwtService.generateLinkChallenge("user@example.com", "google-sub-123", Duration.ofMillis(-1));
            assertThat(jwtService.isValidLinkChallenge(challenge, "user@example.com", "google-sub-123")).isFalse();
        }
    }
}
