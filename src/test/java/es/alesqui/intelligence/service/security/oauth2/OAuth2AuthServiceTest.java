package es.alesqui.intelligence.service.security.oauth2;

import es.alesqui.intelligence.config.DeploymentConfig;
import es.alesqui.intelligence.config.GoogleOAuth2Properties;
import es.alesqui.intelligence.config.TrialConfigurationProperties;
import es.alesqui.intelligence.dto.security.oauth2.GoogleTokenInfo;
import es.alesqui.intelligence.dto.security.oauth2.OAuth2AuthResponse;
import es.alesqui.intelligence.model.access.Group;
import es.alesqui.intelligence.model.core.User;
import es.alesqui.intelligence.model.core.enums.AuthProvider;
import es.alesqui.intelligence.model.core.enums.Role;
import es.alesqui.intelligence.repository.UserRepository;
import es.alesqui.intelligence.security.JwtService;
import es.alesqui.intelligence.service.access.TrialWorkspaceService;
import es.alesqui.intelligence.service.audit.AuditService;
import es.alesqui.intelligence.service.security.RateLimitingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OAuth2AuthServiceTest {

    @Mock
    GoogleTokenVerificationService tokenVerificationService;

    @Mock
    UserRepository userRepository;

    @Mock
    AuditService auditService;

    @Mock
    RateLimitingService rateLimitingService;

    @Mock
    TrialWorkspaceService trialWorkspaceService;

    @Mock
    DeploymentConfig deploymentConfig;

    @Mock
    TrialConfigurationProperties trialConfig;

    @Mock
    GoogleOAuth2Properties googleProps;

    JwtService jwtService;

    OAuth2AuthService service;

    ServerHttpRequest mockRequest;

    GoogleTokenInfo validTokenInfo;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        String testSecret = Base64.getEncoder().encodeToString("this-is-a-test-secret-key-32byte".getBytes());
        ReflectionTestUtils.setField(jwtService, "secretKey", testSecret);
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 3_600_000L);
        ReflectionTestUtils.setField(jwtService, "refreshExpiration", 86_400_000L);

        service = new OAuth2AuthService(
                tokenVerificationService, userRepository, jwtService,
                auditService, rateLimitingService, trialWorkspaceService,
                deploymentConfig, trialConfig, googleProps);

        mockRequest = mock(ServerHttpRequest.class);

        given(rateLimitingService.isOAuth2Allowed(any())).willReturn(Mono.just(true));
        given(rateLimitingService.recordOAuth2Attempt(any())).willReturn(Mono.empty());
        given(auditService.logActionWithUser(any(), any(), any(), any(), any(), any(), any(), any()))
                .willReturn(Mono.empty());
        given(auditService.logFailureWithUser(any(), any(), any(), any(), any(), any(), any()))
                .willReturn(Mono.empty());
        given(googleProps.isEnabled()).willReturn(true);
        given(googleProps.getLinkChallengeTtl()).willReturn(Duration.ofMinutes(5));
        given(trialConfig.getDurationDays()).willReturn(14);

        validTokenInfo = new GoogleTokenInfo(
                "accounts.google.com",
                "test-client-id",
                "google-sub-123",
                "user@example.com",
                "true",
                "Test User",
                null,
                String.valueOf(Instant.now().plusSeconds(3600).getEpochSecond()),
                null);
    }

    @Nested
    @DisplayName("loginWithGoogle() — existing Google user")
    class ExistingGoogleUser {

        @Test
        @DisplayName("user found by (GOOGLE, sub) → returns tokens immediately, newUser=false")
        void existingGoogleUser_returnsTokens() {
            User googleUser = User.builder()
                    .id("user-1")
                    .username("user@example.com")
                    .authProvider(AuthProvider.GOOGLE)
                    .providerId("google-sub-123")
                    .roles(Set.of(Role.ROLE_TRIAL))
                    .isActive(true)
                    .build();

            given(tokenVerificationService.verify(any())).willReturn(Mono.just(validTokenInfo));
            given(userRepository.findByAuthProviderAndProviderId(AuthProvider.GOOGLE, "google-sub-123"))
                    .willReturn(Mono.just(googleUser));

            var request = es.alesqui.intelligence.dto.security.oauth2.GoogleLoginRequest.builder()
                    .idToken("valid-google-token")
                    .build();

            StepVerifier.create(service.loginWithGoogle(request, mockRequest))
                    .assertNext(response -> {
                        assertThat(response.getAccessToken()).isNotNull();
                        assertThat(response.getRefreshToken()).isNotNull();
                        assertThat(response.isNewUser()).isFalse();
                        assertThat(response.isLinkRequired()).isFalse();
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("loginWithGoogle() — new user in TRIAL mode")
    class NewUserTrialMode {

        @Test
        @DisplayName("no existing user + TRIAL mode → creates TRIAL user and returns tokens with newUser=true")
        void newUser_trialMode_createsUserAndReturnsTokens() {
            User savedUser = User.builder()
                    .id("new-user-1")
                    .username("user@example.com")
                    .authProvider(AuthProvider.GOOGLE)
                    .providerId("google-sub-123")
                    .roles(Set.of(Role.ROLE_TRIAL))
                    .isActive(true)
                    .trialStartDate(Instant.now())
                    .trialEndDate(Instant.now().plus(Duration.ofDays(14)))
                    .build();

            given(tokenVerificationService.verify(any())).willReturn(Mono.just(validTokenInfo));
            given(userRepository.findByAuthProviderAndProviderId(any(), any())).willReturn(Mono.empty());
            given(userRepository.findByUsername("user@example.com")).willReturn(Mono.empty());
            given(userRepository.save(any(User.class))).willReturn(Mono.just(savedUser));
            given(deploymentConfig.isCorporate()).willReturn(false);
            given(trialWorkspaceService.createTrialWorkspace(any(User.class), any()))
                    .willReturn(Mono.just(mock(Group.class)));

            var request = es.alesqui.intelligence.dto.security.oauth2.GoogleLoginRequest.builder()
                    .idToken("valid-google-token")
                    .build();

            StepVerifier.create(service.loginWithGoogle(request, mockRequest))
                    .assertNext(response -> {
                        assertThat(response.getAccessToken()).isNotNull();
                        assertThat(response.getRefreshToken()).isNotNull();
                        assertThat(response.isNewUser()).isTrue();
                        assertThat(response.isLinkRequired()).isFalse();
                    })
                    .verifyComplete();

            verify(userRepository).save(any(User.class));
            verify(trialWorkspaceService).createTrialWorkspace(any(User.class), any());
        }
    }

    @Nested
    @DisplayName("loginWithGoogle() — new user in CORPORATE mode")
    class NewUserCorporateMode {

        @Test
        @DisplayName("no existing user + CORPORATE mode → returns 403")
        void newUser_corporateMode_returns403() {
            given(tokenVerificationService.verify(any())).willReturn(Mono.just(validTokenInfo));
            given(userRepository.findByAuthProviderAndProviderId(any(), any())).willReturn(Mono.empty());
            given(userRepository.findByUsername("user@example.com")).willReturn(Mono.empty());
            given(deploymentConfig.isCorporate()).willReturn(true);

            var request = es.alesqui.intelligence.dto.security.oauth2.GoogleLoginRequest.builder()
                    .idToken("valid-google-token")
                    .build();

            StepVerifier.create(service.loginWithGoogle(request, mockRequest))
                    .expectErrorMatches(e -> e instanceof ResponseStatusException rse
                            && rse.getStatusCode().value() == 403)
                    .verify();
        }
    }

    @Nested
    @DisplayName("loginWithGoogle() — LOCAL account conflict")
    class LocalAccountConflict {

        @Test
        @DisplayName("existing LOCAL account → returns linkRequired=true with linkChallenge, no accessToken")
        void localAccountExists_returnsLinkRequired() {
            User localUser = User.builder()
                    .id("local-user-1")
                    .username("user@example.com")
                    .authProvider(AuthProvider.LOCAL)
                    .roles(Set.of(Role.ROLE_IT))
                    .isActive(true)
                    .build();

            given(tokenVerificationService.verify(any())).willReturn(Mono.just(validTokenInfo));
            given(userRepository.findByAuthProviderAndProviderId(any(), any())).willReturn(Mono.empty());
            given(userRepository.findByUsername("user@example.com")).willReturn(Mono.just(localUser));

            var request = es.alesqui.intelligence.dto.security.oauth2.GoogleLoginRequest.builder()
                    .idToken("valid-google-token")
                    .build();

            StepVerifier.create(service.loginWithGoogle(request, mockRequest))
                    .assertNext(response -> {
                        assertThat(response.isLinkRequired()).isTrue();
                        assertThat(response.getLinkChallenge()).isNotNull();
                        assertThat(response.getAccessToken()).isNull();
                        assertThat(response.getRefreshToken()).isNull();
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("confirmGoogleLink()")
    class LinkConfirmation {

        @Test
        @DisplayName("valid challenge → updates authProvider to GOOGLE and returns tokens")
        void validChallenge_linksAccountAndReturnsTokens() {
            // Generate a real link challenge using the real JwtService
            String challenge = jwtService.generateLinkChallenge(
                    "user@example.com", "google-sub-123", Duration.ofMinutes(5));

            User localUser = User.builder()
                    .id("local-user-1")
                    .username("user@example.com")
                    .authProvider(AuthProvider.LOCAL)
                    .password("$2a$10$hashedpassword")
                    .roles(Set.of(Role.ROLE_IT))
                    .isActive(true)
                    .build();

            User linkedUser = User.builder()
                    .id("local-user-1")
                    .username("user@example.com")
                    .authProvider(AuthProvider.GOOGLE)
                    .providerId("google-sub-123")
                    .password("$2a$10$hashedpassword")
                    .roles(Set.of(Role.ROLE_IT))
                    .isActive(true)
                    .build();

            given(tokenVerificationService.verify(any())).willReturn(Mono.just(validTokenInfo));
            given(userRepository.findByUsername("user@example.com")).willReturn(Mono.just(localUser));
            given(userRepository.save(any(User.class))).willReturn(Mono.just(linkedUser));

            var request = es.alesqui.intelligence.dto.security.oauth2.GoogleLinkConfirmRequest.builder()
                    .idToken("valid-google-token")
                    .linkChallenge(challenge)
                    .build();

            StepVerifier.create(service.confirmGoogleLink(request, mockRequest))
                    .assertNext(response -> {
                        assertThat(response.getAccessToken()).isNotNull();
                        assertThat(response.getRefreshToken()).isNotNull();
                        assertThat(response.isLinkRequired()).isFalse();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("invalid challenge (tampered) → returns 401")
        void invalidChallenge_returns401() {
            given(tokenVerificationService.verify(any())).willReturn(Mono.just(validTokenInfo));

            String validChallenge = jwtService.generateLinkChallenge(
                    "user@example.com", "google-sub-123", Duration.ofMinutes(5));
            String tamperedChallenge = validChallenge.substring(0, validChallenge.length() - 4) + "XXXX";

            var request = es.alesqui.intelligence.dto.security.oauth2.GoogleLinkConfirmRequest.builder()
                    .idToken("valid-google-token")
                    .linkChallenge(tamperedChallenge)
                    .build();

            StepVerifier.create(service.confirmGoogleLink(request, mockRequest))
                    .expectErrorMatches(e -> e instanceof ResponseStatusException rse
                            && rse.getStatusCode().value() == 401)
                    .verify();
        }

        @Test
        @DisplayName("expired challenge → returns 401")
        void expiredChallenge_returns401() {
            given(tokenVerificationService.verify(any())).willReturn(Mono.just(validTokenInfo));

            String expiredChallenge = jwtService.generateLinkChallenge(
                    "user@example.com", "google-sub-123", Duration.ofMillis(-1));

            var request = es.alesqui.intelligence.dto.security.oauth2.GoogleLinkConfirmRequest.builder()
                    .idToken("valid-google-token")
                    .linkChallenge(expiredChallenge)
                    .build();

            StepVerifier.create(service.confirmGoogleLink(request, mockRequest))
                    .expectErrorMatches(e -> e instanceof ResponseStatusException rse
                            && rse.getStatusCode().value() == 401)
                    .verify();
        }
    }

    @Nested
    @DisplayName("Feature disabled (enabled=false)")
    class FeatureDisabled {

        @Test
        @DisplayName("loginWithGoogle returns 404 when Google OAuth2 is disabled")
        void loginWithGoogle_disabled_returns404() {
            given(googleProps.isEnabled()).willReturn(false);

            var request = es.alesqui.intelligence.dto.security.oauth2.GoogleLoginRequest.builder()
                    .idToken("valid-google-token")
                    .build();

            StepVerifier.create(service.loginWithGoogle(request, mockRequest))
                    .expectErrorMatches(e -> e instanceof ResponseStatusException rse
                            && rse.getStatusCode().value() == 404)
                    .verify();
        }

        @Test
        @DisplayName("confirmGoogleLink returns 404 when Google OAuth2 is disabled")
        void confirmGoogleLink_disabled_returns404() {
            given(googleProps.isEnabled()).willReturn(false);

            var request = es.alesqui.intelligence.dto.security.oauth2.GoogleLinkConfirmRequest.builder()
                    .idToken("valid-google-token")
                    .linkChallenge("any-challenge")
                    .build();

            StepVerifier.create(service.confirmGoogleLink(request, mockRequest))
                    .expectErrorMatches(e -> e instanceof ResponseStatusException rse
                            && rse.getStatusCode().value() == 404)
                    .verify();
        }
    }
}
