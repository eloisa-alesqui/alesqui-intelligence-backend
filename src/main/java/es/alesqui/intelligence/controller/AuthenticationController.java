package es.alesqui.intelligence.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import es.alesqui.intelligence.dto.security.ActivateAccountRequest;
import es.alesqui.intelligence.dto.security.ActivateAccountResponse;
import es.alesqui.intelligence.dto.security.AuthRequest;
import es.alesqui.intelligence.dto.security.AuthResponse;
import es.alesqui.intelligence.dto.security.ForgotPasswordRequest;
import es.alesqui.intelligence.dto.security.ForgotPasswordResponse;
import es.alesqui.intelligence.dto.security.RefreshTokenRequest;
import es.alesqui.intelligence.dto.security.ResendActivationRequest;
import es.alesqui.intelligence.dto.security.ResetPasswordRequest;
import es.alesqui.intelligence.dto.security.ResetPasswordResponse;
import es.alesqui.intelligence.dto.security.ValidateTokenRequest;
import es.alesqui.intelligence.dto.security.ValidateTokenResponse;
import es.alesqui.intelligence.model.audit.AuditAction;
import es.alesqui.intelligence.model.audit.EntityType;
import es.alesqui.intelligence.model.core.User;
import es.alesqui.intelligence.model.core.enums.Role;
import es.alesqui.intelligence.security.JwtService;
import es.alesqui.intelligence.service.access.UserActivationService;
import es.alesqui.intelligence.service.access.UserPasswordService;
import es.alesqui.intelligence.service.audit.AuditService;
import es.alesqui.intelligence.service.identity.UserService;
import es.alesqui.intelligence.service.security.RateLimitingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * REST controller for handling authentication endpoints.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthenticationController {

    private final ReactiveAuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserActivationService userActivationService;
    private final UserPasswordService userPasswordService;
    private final AuditService auditService;
    private final UserService userService;
    private final RateLimitingService rateLimitingService;

    /**
     * Handles the user login request.
     *
     * This endpoint authenticates a user based on their username and password.
     * If the credentials are valid, it generates and returns a JWT access token
     * and a refresh token.
     *
     * @param authRequest The authentication request with user credentials.
     * @param httpRequest the HTTP request for audit logging
     * @return A Mono containing the authentication response with JWTs.
     * Returns a Mono.error with 401 Unauthorized if authentication fails.
     */
    @PostMapping("/login")
    public Mono<AuthResponse> login(@Valid @RequestBody AuthRequest authRequest,
                                     ServerHttpRequest httpRequest) {
        log.debug("[Auth] Attempting login for '{}'", authRequest.getUsername());
        Authentication authenticationToken = new UsernamePasswordAuthenticationToken(
                authRequest.getUsername(),
                authRequest.getPassword()
        );
        // The authenticationManager will use our ReactiveUserDetailsService and PasswordEncoder
        // to validate the credentials.
        return authenticationManager.authenticate(authenticationToken)
                .zipWith(Mono.just(authRequest.getUsername()))
                .doOnSuccess(tuple -> log.debug("[Auth] Authentication success for '{}')", tuple.getT1().getName()))
                .flatMap(tuple -> {
                    Authentication authentication = tuple.getT1();
                    // The principal is the UserDetails object we loaded.
                    User user = (User) authentication.getPrincipal();
                    String accessToken = jwtService.generateToken(user);
                    String refreshToken = jwtService.generateRefreshToken(user);

                    // Log successful login
                    return auditService.logActionWithUser(
                            AuditAction.AUTH_LOGIN,
                            EntityType.USER,
                            user.getId(),
                            user.getUsername(),
                            user.getUsername(),
                            user.getId(),
                            "User logged in successfully",
                            httpRequest
                    ).thenReturn(AuthResponse.builder()
                            .accessToken(accessToken)
                            .refreshToken(refreshToken)
                            .build());
                })
                .onErrorResume(e -> {
                    log.debug("[Auth] Authentication failed: {} - {}", e.getClass().getSimpleName(), e.getMessage());
                    // Extract username from the error context if possible
                    return auditService.logFailureWithUser(
                            AuditAction.AUTH_LOGIN,
                            EntityType.USER,
                            "unknown",
                            authRequest.getUsername(),
                            authRequest.getUsername(),
                            "Invalid credentials: " + e.getMessage(),
                            httpRequest
                    ).then(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials")));
                });
    }

    /**
     * Refreshes JWT tokens using a valid refresh token.
     * 
     * This endpoint allows clients to obtain new access and refresh tokens
     * without requiring the user to log in again.
     * 
     * @param request the request containing the refresh token
     * @param httpRequest the HTTP request for audit logging
     * @return a Mono containing new JWT tokens
     */
        @PostMapping("/refresh")
    public Mono<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request,
                                            ServerHttpRequest httpRequest) {
        String refreshToken = request.getRefreshToken();
        
        // Asumo que 'jwtService.extractUsername' devuelve String
        return Mono.fromCallable(() -> jwtService.extractUsername(refreshToken))
                .<User>flatMap(username -> {
                    log.debug("[Auth] Attempting to refresh token for user '{}'", username);
                    return userService.findByUsername(username);
                })
                .<AuthResponse>flatMap(user -> {
                    // Validate the refresh token
                    if (!jwtService.isTokenValid(refreshToken, user) || !jwtService.isRefreshToken(refreshToken)) {
                        log.warn("[Auth] Invalid refresh token for user '{}'", user.getUsername());
                        return auditService.logFailureWithUser(
                                AuditAction.AUTH_TOKEN_REFRESH,
                                EntityType.USER,
                                user.getId(),
                                user.getUsername(),
                                user.getUsername(),
                                "Invalid or expired refresh token",
                                httpRequest
                        )
                        .then(Mono.<AuthResponse>error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token")));
                    }
                    
                    // Generate new tokens
                    String newAccessToken = jwtService.generateToken(user);
                    String newRefreshToken = jwtService.generateRefreshToken(user);
                    
                    log.debug("[Auth] Successfully refreshed tokens for user '{}'", user.getUsername());
                    
                    // Log successful token refresh
                    return auditService.logActionWithUser(
                            AuditAction.AUTH_TOKEN_REFRESH,
                            EntityType.USER,
                            user.getId(),
                            user.getUsername(),
                            user.getUsername(),
                            user.getId(),
                            "JWT tokens refreshed successfully",
                            httpRequest
                    ).thenReturn(AuthResponse.builder()
                            .accessToken(newAccessToken)
                            .refreshToken(newRefreshToken)
                            .build());
                })
                .onErrorResume(e -> {
                    if (e instanceof ResponseStatusException) {
                        return Mono.<AuthResponse>error(e);
                    }
                    log.warn("[Auth] Token refresh failed: {}", e.getMessage());
                    return Mono.<AuthResponse>error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
                });
    }

    /**
     * Handles the user logout request.
     * 
     * This endpoint logs the logout action for audit purposes.
     * Since JWT tokens are stateless, the actual token invalidation must be handled client-side.
     * 
     * @param httpRequest the HTTP request for audit logging
     * @return a Mono indicating successful logout
     */
    @PostMapping("/logout")
    public Mono<Void> logout(ServerHttpRequest httpRequest) {
        return userService.getCurrentUser()
                .flatMap(user -> {
                    log.debug("[Auth] User '{}' logging out", user.getUsername());
                    return auditService.logActionWithUser(
                            AuditAction.AUTH_LOGOUT,
                            EntityType.USER,
                            user.getId(),
                            user.getUsername(),
                            user.getUsername(),
                            user.getId(),
                            "User logged out",
                            httpRequest
                    );
                })
                .doOnError(e -> log.warn("[Auth] Logout audit logging failed: {}", e.getMessage()))
                .onErrorResume(e -> {
                    log.warn("[Auth] Logout called without valid authentication: {}", e.getMessage());
                    return Mono.empty();
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("[Auth] Logout called without valid authentication");
                    return Mono.empty();
                }));
    }

    /**
     * Validates an activation token.
     *
     * @param request the request containing the token to validate
     * @return validation response with user details if valid
     */
    @PostMapping("/validate-token")
    public Mono<ValidateTokenResponse> validateToken(@Valid @RequestBody ValidateTokenRequest request) {
        log.debug("[Auth] Validating activation token");
        return doValidateToken(request.getToken());
    }

    /**
     * Validates an activation token via GET request (for direct URL access).
     *
     * @param token the activation token from query parameter
     * @return validation response with user details if valid
     */
    @GetMapping("/validate-token")
    public Mono<ValidateTokenResponse> validateTokenGet(@RequestParam String token) {
        log.debug("[Auth] Validating activation token via GET");
        return doValidateToken(token);
    }

    /**
     * Shared validation logic for both POST and GET validate-token endpoints.
     * Looks up the user by activation token and builds a {@link ValidateTokenResponse}
     * with the user's email and primary role if found, or an invalid response otherwise.
     *
     * @param token the raw activation token to validate
     * @return a {@link Mono} emitting a valid response with user details, or an invalid response
     *         if the token does not exist or has expired
     */
    private Mono<ValidateTokenResponse> doValidateToken(String token) {
        return userActivationService.validateActivationToken(token)
            .map(user -> {
                String role = user.getRoles().stream()
                    .map(Role::getLabel)
                    .findFirst()
                    .orElse("N/A");

                return ValidateTokenResponse.builder()
                    .valid(true)
                    .email(user.getUsername())
                    .role(role)
                    .build();
            })
            .switchIfEmpty(Mono.just(ValidateTokenResponse.builder()
                .valid(false)
                .message("Invalid or expired token")
                .build()));
    }

    /**
     * Activates a user account with a new password.
     * 
     * @param request the request containing token and new password
     * @param httpRequest the HTTP request for audit logging
     * @return activation response
     */
    @PostMapping("/activate-account")
    public Mono<ActivateAccountResponse> activateAccount(@Valid @RequestBody ActivateAccountRequest request,
                                                         ServerHttpRequest httpRequest) {
        log.debug("[Auth] Activating account");
        
        return userActivationService.activateAccount(request.getToken(), request.getPassword(), httpRequest)
            .map(user -> ActivateAccountResponse.builder()
                .success(true)
                .message("Account activated successfully. You can now log in.")
                .email(user.getUsername())
                .build())
            .onErrorResume(e -> {
                log.warn("[Auth] Account activation failed: {}", e.getMessage());
                // Log failure audit event
                return auditService.logUserOperationFailure(
                        AuditAction.USER_ACTIVATED,
                        "token",
                        "Account activation via token",
                        e.getMessage(),
                        httpRequest
                ).then(Mono.just(ActivateAccountResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .build()));
            });
    }

    /**
     * Resends an activation email to a user.
     * 
     * @param request the request containing the user's email
     * @return response indicating success or failure
     */
    @PostMapping("/resend-activation")
    public Mono<ActivateAccountResponse> resendActivation(@Valid @RequestBody ResendActivationRequest request) {
        log.debug("[Auth] Resending activation email to: {}", request.getEmail());
        
        return userActivationService.resendActivationEmail(request.getEmail())
            .thenReturn(ActivateAccountResponse.builder()
                .success(true)
                .message("Activation email sent successfully. Please check your inbox.")
                .email(request.getEmail())
                .build())
            .onErrorResume(e -> {
                log.warn("[Auth] Resend activation failed: {}", e.getMessage());
                return Mono.just(ActivateAccountResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .build());
            });
    }

    /**
     * Handles the "forgot password" request.
     * Sends a password reset email to the user if the email exists.
     * 
     * @param request the request containing the user's email
     * @param httpRequest the HTTP request for audit logging
     * @return response indicating that the email has been sent (for security, always returns success)
     */
    @PostMapping("/forgot-password")
    public Mono<ForgotPasswordResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
                                                        ServerHttpRequest httpRequest) {
        log.debug("[Auth] Password reset requested for: {}", request.getEmail());

        String ip = httpRequest.getRemoteAddress() != null
                ? httpRequest.getRemoteAddress().getAddress().getHostAddress()
                : "unknown";

        ForgotPasswordResponse silentResponse = ForgotPasswordResponse.builder()
                .success(true)
                .message("If an account exists with this email, a password reset link has been sent.")
                .email(request.getEmail())
                .build();

        return rateLimitingService.isPasswordResetAllowed(ip)
                .flatMap(allowed -> {
                    if (!allowed) {
                        // Return success silently to avoid leaking rate limit info
                        return Mono.just(silentResponse);
                    }
                    return rateLimitingService.recordPasswordResetAttempt(ip)
                            .then(userPasswordService.requestPasswordReset(request.getEmail(), httpRequest))
                            .thenReturn(silentResponse)
                            .onErrorResume(e -> {
                                log.warn("[Auth] Forgot password error: {}", e.getMessage());
                                return Mono.just(silentResponse);
                            });
                });
    }

    /**
     * Validates a password reset token.
     * 
     * @param token the password reset token from query parameter
     * @return validation response with user details if valid
     */
    @GetMapping("/validate-reset-token")
    public Mono<ValidateTokenResponse> validateResetToken(@RequestParam String token) {
        log.debug("[Auth] Validating password reset token");
        
        return userPasswordService.validatePasswordResetToken(token)
            .map(user -> ValidateTokenResponse.builder()
                .valid(true)
                .email(user.getUsername())
                .message("Token is valid")
                .build())
            .switchIfEmpty(Mono.just(ValidateTokenResponse.builder()
                .valid(false)
                .message("Invalid or expired token")
                .build()));
    }

    /**
     * Resets a user's password with a valid reset token.
     * 
     * @param request the request containing token and new password
     * @param httpRequest the HTTP request for audit logging
     * @return reset response
     */
    @PostMapping("/reset-password")
    public Mono<ResetPasswordResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request,
                                                     ServerHttpRequest httpRequest) {
        log.debug("[Auth] Resetting password");
        
        return userPasswordService.resetPassword(request.getToken(), request.getNewPassword(), httpRequest)
            .map(user -> ResetPasswordResponse.builder()
                .success(true)
                .message("Password successfully reset. You can now log in with your new password.")
                .email(user.getUsername())
                .build())
            .onErrorResume(e -> {
                log.warn("[Auth] Password reset failed: {}", e.getMessage());
                // Log failure audit event
                return auditService.logUserOperationFailure(
                        AuditAction.AUTH_PASSWORD_RESET,
                        "token",
                        "Password reset via token",
                        e.getMessage(),
                        httpRequest
                ).then(Mono.just(ResetPasswordResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .build()));
            });
    }
}
