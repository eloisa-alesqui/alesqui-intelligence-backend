package es.alesqui.intelligence.service.access;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import es.alesqui.intelligence.model.audit.AuditAction;
import es.alesqui.intelligence.model.audit.EntityType;
import es.alesqui.intelligence.model.core.User;
import es.alesqui.intelligence.repository.UserRepository;
import es.alesqui.intelligence.service.audit.AuditService;
import es.alesqui.intelligence.service.notification.EmailService;
import es.alesqui.intelligence.service.notification.EmailTemplateService;
import es.alesqui.intelligence.service.security.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Service responsible for password management operations.
 * Handles password reset requests, token validation, and password updates.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserPasswordService {

    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final EmailService emailService;
    private final EmailTemplateService emailTemplateService;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;

    /**
     * Initiates the password reset process for a user.
     * Generates a password reset token, saves it to the user, and sends a reset email.
     *
     * @param email   the user's email address
     * @param request the HTTP request for audit logging
     * @return Mono signaling completion
     */
    public Mono<Void> requestPasswordReset(String email, ServerHttpRequest request) {
        return userRepository.findByUsername(email)
                .switchIfEmpty(Mono.defer(() -> {
                    // For security, don't reveal if the email exists or not
                    log.warn("[PasswordReset] Password reset requested for non-existent email: {}",
                            email);
                    return Mono.empty(); // Return empty but don't error
                }))
                .flatMap(user -> {
                    if (!user.isActive()) {
                        // User must be activated first
                        log.warn("[PasswordReset] Password reset requested for inactive user: {}",
                                email);
                        return Mono.empty(); // Don't send email to inactive users
                    }

                    // Generate reset token
                    String resetToken = tokenService.generateSecureToken();
                    Instant tokenExpiration = tokenService.calculatePasswordResetExpiration();

                    user.setPasswordResetToken(resetToken);
                    user.setPasswordResetTokenExpiresAt(tokenExpiration);

                    log.info("[PasswordReset] Generating password reset token for: {}", email);

                    return userRepository.save(user)
                            .flatMap(savedUser -> {
                                log.info("[PasswordReset] Sending password reset email to: {}",
                                        email);
                                String htmlContent = emailTemplateService
                                        .buildPasswordResetEmail(email,
                                                resetToken);
                                return emailService.sendHtmlEmail(email,
                                        "Password Reset Request - Alesqui Intelligence",
                                        htmlContent)
                                        .then(auditService.logAction(
                                                AuditAction.AUTH_PASSWORD_RESET,
                                                EntityType.USER,
                                                savedUser.getId(),
                                                savedUser.getUsername(),
                                                "Password reset requested - email sent",
                                                request));
                            });
                })
                .then();
    }

    /**
     * Validates a password reset token.
     *
     * @param token the password reset token
     * @return Mono containing the user if the token is valid, empty otherwise
     */
    public Mono<User> validatePasswordResetToken(String token) {
        return userRepository.findByPasswordResetToken(token)
                .filter(user -> {
                    // Check if token is expired
                    if (user.getPasswordResetTokenExpiresAt() == null) {
                        log.warn("[PasswordReset] Token has no expiration date");
                        return false;
                    }

                    boolean isValid = user.getPasswordResetTokenExpiresAt().isAfter(Instant.now());
                    if (!isValid) {
                        log.warn("[PasswordReset] Token expired for user: {}",
                                user.getUsername());
                    }
                    return isValid;
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("[PasswordReset] Invalid or expired password reset token");
                    return Mono.empty();
                }));
    }

    /**
     * Resets a user's password using a valid reset token.
     *
     * @param token       the password reset token
     * @param newPassword the new password to set
     * @param request     the HTTP request for audit logging
     * @return Mono containing the updated user
     */
    public Mono<User> resetPassword(String token, String newPassword, ServerHttpRequest request) {
        return validatePasswordResetToken(token)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "Invalid or expired password reset token")))
                .flatMap(user -> {
                    // Hash the new password
                    String hashedPassword = passwordEncoder.encode(newPassword);
                    user.setPassword(hashedPassword);

                    // Clear the reset token
                    user.setPasswordResetToken(null);
                    user.setPasswordResetTokenExpiresAt(null);

                    // Ensure user is active (in case they were pending)
                    user.setActive(true);

                    log.info("[PasswordReset] Password successfully reset for user: {}",
                            user.getUsername());

                    return userRepository.save(user)
                            .flatMap(savedUser -> {
                                // Log password reset completion audit event
                                return auditService.logAction(
                                        AuditAction.AUTH_PASSWORD_RESET,
                                        EntityType.USER,
                                        savedUser.getId(),
                                        savedUser.getUsername(),
                                        "Password reset completed successfully",
                                        request).thenReturn(savedUser);
                            });
                });
    }
}
