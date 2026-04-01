package es.alesqui.intelligence.controller;

import es.alesqui.intelligence.config.TestSecurityConfig;
import es.alesqui.intelligence.dto.security.*;
import es.alesqui.intelligence.model.core.User;
import es.alesqui.intelligence.model.core.enums.Role;
import java.util.Map;
import es.alesqui.intelligence.security.JwtService;
import es.alesqui.intelligence.service.access.UserActivationService;
import es.alesqui.intelligence.service.access.UserPasswordService;
import es.alesqui.intelligence.service.audit.AuditService;
import es.alesqui.intelligence.service.identity.UserService;
import es.alesqui.intelligence.service.security.RateLimitingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

/**
 * Controller-layer tests for AuthenticationController.
 * Uses TestSecurityConfig to bypass the JWT filter so tests focus on
 * request validation, business logic, and HTTP response codes.
 * Security enforcement (e.g. /api/auth/logout requires auth) is covered by
 * JwtAuthenticationWebFilterTest and SecurityConfig integration tests.
 */
@WebFluxTest(AuthenticationController.class)
@Import(TestSecurityConfig.class)
class AuthenticationControllerTest {

    @Autowired
    WebTestClient webClient;

    @MockitoBean
    ReactiveAuthenticationManager authenticationManager;

    @MockitoBean
    JwtService jwtService;

    @MockitoBean
    UserActivationService userActivationService;

    @MockitoBean
    UserPasswordService userPasswordService;

    @MockitoBean
    AuditService auditService;

    @MockitoBean
    UserService userService;

    @MockitoBean
    RateLimitingService rateLimitingService;

    private User mockUser;

    @BeforeEach
    void setUp() {
        mockUser = User.builder()
                .id("user-1")
                .username("test@example.com")
                .password("$2a$10$hashedpassword")
                .roles(Set.of(Role.ROLE_IT))
                .isActive(true)
                .build();

        // Default stub for auditService — most controller paths call it fire-and-forget
        given(auditService.logActionWithUser(any(), any(), any(), any(), any(), any(), any(), any()))
                .willReturn(Mono.empty());
        given(auditService.logFailureWithUser(any(), any(), any(), any(), any(), any(), any()))
                .willReturn(Mono.empty());
        given(auditService.logUserOperationFailure(any(), any(), any(), any(), any()))
                .willReturn(Mono.empty());

        // Default stub for rateLimitingService — allow all requests
        given(rateLimitingService.isPasswordResetAllowed(anyString())).willReturn(Mono.just(true));
        given(rateLimitingService.recordPasswordResetAttempt(anyString())).willReturn(Mono.empty());
    }

    // ──────────────────────────── LOGIN ────────────────────────────

    @Test
    void login_success_returns200WithTokens() {
        Authentication auth = new UsernamePasswordAuthenticationToken(mockUser, null, mockUser.getAuthorities());
        given(authenticationManager.authenticate(any())).willReturn(Mono.just(auth));
        given(jwtService.generateToken(any(User.class))).willReturn("access-token");
        given(jwtService.generateRefreshToken(any(User.class))).willReturn("refresh-token");

        webClient.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(AuthRequest.builder().username("test@example.com").password("password").build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(AuthResponse.class)
                .value(response -> {
                    assertThat(response.getAccessToken()).isEqualTo("access-token");
                    assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
                });
    }

    @Test
    void login_invalidCredentials_returns401() {
        given(authenticationManager.authenticate(any()))
                .willReturn(Mono.error(new BadCredentialsException("Bad credentials")));

        webClient.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(AuthRequest.builder().username("test@example.com").password("wrong").build())
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void login_blankUsername_returns400() {
        webClient.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(AuthRequest.builder().username("").password("password").build())
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void login_blankPassword_returns400() {
        webClient.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(AuthRequest.builder().username("test@example.com").password("").build())
                .exchange()
                .expectStatus().isBadRequest();
    }

    // ──────────────────────────── REFRESH TOKEN ────────────────────────────

    @Test
    void refreshToken_success_returnsNewTokens() {
        given(jwtService.extractUsername(anyString())).willReturn("test@example.com");
        given(userService.findByUsername("test@example.com")).willReturn(Mono.just(mockUser));
        given(jwtService.isTokenValid(anyString(), any(User.class))).willReturn(true);
        given(jwtService.isRefreshToken(anyString())).willReturn(true);
        given(jwtService.generateToken(any(User.class))).willReturn("new-access-token");
        given(jwtService.generateRefreshToken(any(User.class))).willReturn("new-refresh-token");

        webClient.post().uri("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(RefreshTokenRequest.builder().refreshToken("valid-refresh-token").build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(AuthResponse.class)
                .value(response -> {
                    assertThat(response.getAccessToken()).isEqualTo("new-access-token");
                    assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");
                });
    }

    @Test
    void refreshToken_invalidToken_returns401() {
        given(jwtService.extractUsername(anyString())).willReturn("test@example.com");
        given(userService.findByUsername("test@example.com")).willReturn(Mono.just(mockUser));
        given(jwtService.isTokenValid(anyString(), any(User.class))).willReturn(false);
        given(jwtService.isRefreshToken(anyString())).willReturn(false);

        webClient.post().uri("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(RefreshTokenRequest.builder().refreshToken("invalid-token").build())
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void refreshToken_blankToken_returns400() {
        webClient.post().uri("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(RefreshTokenRequest.builder().refreshToken("").build())
                .exchange()
                .expectStatus().isBadRequest();
    }

    // ──────────────────────────── LOGOUT ────────────────────────────

    @Test
    void logout_whenAuthenticated_returns200() {
        given(userService.getCurrentUser()).willReturn(Mono.just(mockUser));

        webClient.post().uri("/api/auth/logout")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void logout_whenUnauthenticated_stillReturns200() {
        // The controller handles the unauthenticated case gracefully with switchIfEmpty.
        // The 401 for unauthenticated logout is enforced by the security filter chain,
        // tested in JwtAuthenticationWebFilterTest.
        given(userService.getCurrentUser()).willReturn(Mono.empty());

        webClient.post().uri("/api/auth/logout")
                .exchange()
                .expectStatus().isOk();
    }

    // ──────────────────────────── VALIDATE TOKEN ────────────────────────────

    @Test
    void validateToken_validToken_returnsValidResponse() {
        given(userActivationService.validateActivationToken("valid-token")).willReturn(Mono.just(mockUser));

        webClient.post().uri("/api/auth/validate-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("token", "valid-token"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(ValidateTokenResponse.class)
                .value(response -> {
                    assertThat(response.isValid()).isTrue();
                    assertThat(response.getEmail()).isEqualTo("test@example.com");
                });
    }

    @Test
    void validateToken_invalidToken_returnsInvalidResponse() {
        given(userActivationService.validateActivationToken("bad-token")).willReturn(Mono.empty());

        webClient.post().uri("/api/auth/validate-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("token", "bad-token"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(ValidateTokenResponse.class)
                .value(response -> assertThat(response.isValid()).isFalse());
    }

    @Test
    void validateTokenGet_validToken_returnsValidResponse() {
        given(userActivationService.validateActivationToken("valid-token")).willReturn(Mono.just(mockUser));

        webClient.get().uri("/api/auth/validate-token?token=valid-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody(ValidateTokenResponse.class)
                .value(response -> assertThat(response.isValid()).isTrue());
    }

    // ──────────────────────────── ACTIVATE ACCOUNT ────────────────────────────

    @Test
    void activateAccount_success_returns200() {
        given(userActivationService.activateAccount(anyString(), anyString(), any(ServerHttpRequest.class)))
                .willReturn(Mono.just(mockUser));

        webClient.post().uri("/api/auth/activate-account")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"token\":\"valid-token\",\"password\":\"Test1234!\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody(ActivateAccountResponse.class)
                .value(response -> {
                    assertThat(response.isSuccess()).isTrue();
                    assertThat(response.getEmail()).isEqualTo("test@example.com");
                });
    }

    @Test
    void activateAccount_invalidToken_returnsSuccessFalse() {
        given(userActivationService.activateAccount(anyString(), anyString(), any(ServerHttpRequest.class)))
                .willReturn(Mono.error(new IllegalArgumentException("Invalid or expired activation token")));

        webClient.post().uri("/api/auth/activate-account")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"token\":\"bad-token\",\"password\":\"Test1234!\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody(ActivateAccountResponse.class)
                .value(response -> assertThat(response.isSuccess()).isFalse());
    }

    // ──────────────────────────── RESEND ACTIVATION ────────────────────────────

    @Test
    void resendActivation_success_returns200() {
        given(userActivationService.resendActivationEmail(anyString())).willReturn(Mono.empty());

        webClient.post().uri("/api/auth/resend-activation")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"test@example.com\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody(ActivateAccountResponse.class)
                .value(response -> {
                    assertThat(response.isSuccess()).isTrue();
                    assertThat(response.getEmail()).isEqualTo("test@example.com");
                });
    }

    // ──────────────────────────── FORGOT PASSWORD ────────────────────────────

    @Test
    void forgotPassword_success_returns200WithGenericMessage() {
        given(userPasswordService.requestPasswordReset(anyString(), any())).willReturn(Mono.empty());

        webClient.post().uri("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ForgotPasswordRequest.builder().email("test@example.com").build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(ForgotPasswordResponse.class)
                .value(response -> {
                    assertThat(response.isSuccess()).isTrue();
                    assertThat(response.getEmail()).isEqualTo("test@example.com");
                    // Must not reveal whether the account exists
                    assertThat(response.getMessage()).contains("If an account exists");
                });
    }

    @Test
    void forgotPassword_unknownEmail_returnsGenericMessage() {
        // Even on service error, the response must not reveal if email exists (security)
        given(userPasswordService.requestPasswordReset(anyString(), any()))
                .willReturn(Mono.error(new RuntimeException("User not found")));

        webClient.post().uri("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ForgotPasswordRequest.builder().email("unknown@example.com").build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(ForgotPasswordResponse.class)
                .value(response -> {
                    assertThat(response.isSuccess()).isTrue();
                    assertThat(response.getMessage()).contains("If an account exists");
                });
    }

    @Test
    void forgotPassword_invalidEmail_returns400() {
        webClient.post().uri("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ForgotPasswordRequest.builder().email("not-an-email").build())
                .exchange()
                .expectStatus().isBadRequest();
    }

    // ──────────────────────────── VALIDATE RESET TOKEN ────────────────────────────

    @Test
    void validateResetToken_validToken_returnsValidResponse() {
        given(userPasswordService.validatePasswordResetToken("valid-reset-token"))
                .willReturn(Mono.just(mockUser));

        webClient.get().uri("/api/auth/validate-reset-token?token=valid-reset-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody(ValidateTokenResponse.class)
                .value(response -> {
                    assertThat(response.isValid()).isTrue();
                    assertThat(response.getEmail()).isEqualTo("test@example.com");
                });
    }

    @Test
    void validateResetToken_invalidToken_returnsInvalidResponse() {
        given(userPasswordService.validatePasswordResetToken("bad-token")).willReturn(Mono.empty());

        webClient.get().uri("/api/auth/validate-reset-token?token=bad-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody(ValidateTokenResponse.class)
                .value(response -> assertThat(response.isValid()).isFalse());
    }

    // ──────────────────────────── RESET PASSWORD ────────────────────────────

    @Test
    void resetPassword_success_returns200() {
        given(userPasswordService.resetPassword(anyString(), anyString(), any()))
                .willReturn(Mono.just(mockUser));

        webClient.post().uri("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ResetPasswordRequest.builder().token("valid-token").newPassword("NewPass1!").build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(ResetPasswordResponse.class)
                .value(response -> {
                    assertThat(response.isSuccess()).isTrue();
                    assertThat(response.getEmail()).isEqualTo("test@example.com");
                });
    }

    @Test
    void resetPassword_invalidToken_returnsSuccessFalse() {
        given(userPasswordService.resetPassword(anyString(), anyString(), any()))
                .willReturn(Mono.error(new IllegalArgumentException("Invalid or expired password reset token")));

        webClient.post().uri("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ResetPasswordRequest.builder().token("bad-token").newPassword("NewPass1!").build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(ResetPasswordResponse.class)
                .value(response -> assertThat(response.isSuccess()).isFalse());
    }
}
