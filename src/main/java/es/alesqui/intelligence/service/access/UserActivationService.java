package es.alesqui.intelligence.service.access;

import java.time.Instant;
import java.util.function.Function;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import es.alesqui.intelligence.model.access.Group;
import es.alesqui.intelligence.model.audit.AuditAction;
import es.alesqui.intelligence.model.audit.EntityType;
import es.alesqui.intelligence.model.core.User;
import es.alesqui.intelligence.model.core.enums.Role;
import es.alesqui.intelligence.repository.UserRepository;
import es.alesqui.intelligence.service.audit.AuditService;
import es.alesqui.intelligence.service.notification.EmailService;
import es.alesqui.intelligence.service.notification.EmailTemplateService;
import es.alesqui.intelligence.service.security.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Service responsible for user account activation operations.
 * Handles activation token validation, account activation, and resending activation emails.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserActivationService {

    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final EmailService emailService;
    private final EmailTemplateService emailTemplateService;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;
    private final TrialWorkspaceService trialWorkspaceService;

    /**
     * Validates an activation token.
     *
     * @param token the activation token to validate
     * @return Mono containing the user if token is valid, empty if invalid/expired
     */
    public Mono<User> validateActivationToken(String token) {
        return userRepository.findByActivationToken(token)
                .flatMap(user -> {
                    if (user.isActive()) {
                        log.warn("Token used for already active user: {}", user.getUsername());
                        return Mono.empty();
                    }

                    if (user.getActivationTokenExpiresAt() == null ||
                            user.getActivationTokenExpiresAt().isBefore(Instant.now())) {
                        log.warn("Expired activation token for user: {}", user.getUsername());
                        return Mono.empty();
                    }

                    log.debug("Valid activation token for user: {}", user.getUsername());
                    return Mono.just(user);
                });
    }

    /**
     * Activates a user account with the provided token and password.
     * For TRIAL users, creates their workspace after activation.
     *
     * @param token    the activation token
     * @param password the new password to set
     * @param request  the HTTP request for audit logging
     * @return Mono containing the activated user
     */
    public Mono<User> activateAccount(String token, String password, ServerHttpRequest request) {
        return this.activateAccount(token, password,
                user -> trialWorkspaceService.createTrialWorkspace(user, request))
                .flatMap(user -> {
                    // Log account activation audit event
                    // Use logActionWithUser since the user is not authenticated during activation
                    return auditService.logActionWithUser(
                            AuditAction.USER_ACTIVATED,
                            EntityType.USER,
                            user.getId(),
                            user.getUsername(),
                            user.getUsername(),
                            user.getId(),
                            "User account activated",
                            request).thenReturn(user);
                });
    }

    /**
     * Activates a user account with the provided token and password.
     * If the user has ROLE_TRIAL, callback to create workspace group.
     *
     * @param token                        the activation token
     * @param password                     the new password to set
     * @param createTrialWorkspaceCallback callback to create trial workspace if
     *                                     needed
     * @return Mono containing the activated user
     */
    public Mono<User> activateAccount(String token, String password,
            Function<User, Mono<Group>> createTrialWorkspaceCallback) {
        return validateActivationToken(token)
                .switchIfEmpty(Mono.error(
                        new IllegalArgumentException("Invalid or expired activation token")))
                .flatMap(user -> {
                    user.setPassword(passwordEncoder.encode(password));
                    user.setActive(true);
                    user.setActivationToken(null);
                    user.setActivationTokenExpiresAt(null);

                    log.info("Activating user account: {}", user.getUsername());
                    return userRepository.save(user)
                            .flatMap(savedUser -> {
                                // Check if user has ROLE_TRIAL
                                boolean hasTrialRole = savedUser.getRoles() != null &&
                                        savedUser.getRoles().contains(
                                                Role.ROLE_TRIAL);

                                if (hasTrialRole && createTrialWorkspaceCallback != null) {
                                    log.info("User {} has ROLE_TRIAL, creating automatic workspace",
                                            savedUser.getUsername());
                                    return createTrialWorkspaceCallback
                                            .apply(savedUser)
                                            .thenReturn(savedUser);
                                }

                                return Mono.just(savedUser);
                            });
                });
    }

    /**
     * Resends an activation email to a user.
     *
     * @param email the user's email address
     * @return Mono signaling completion
     */
    public Mono<Void> resendActivationEmail(String email) {
        return userRepository.findByUsername(email)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("User not found: " + email)))
                .flatMap(user -> {
                    if (user.isActive()) {
                        return Mono.error(
                                new IllegalArgumentException("User is already active"));
                    }

                    // Generate new token
                    String newToken = tokenService.generateSecureToken();
                    Instant newExpiration = tokenService.calculateActivationExpiration();

                    user.setActivationToken(newToken);
                    user.setActivationTokenExpiresAt(newExpiration);

                    String rolesStr = user.getRoles().stream()
                            .map(Role::getLabel)
                            .reduce((a, b) -> a + ", " + b)
                            .orElse("N/A");

                    log.info("Resending activation email to: {}", email);

                    return userRepository.save(user)
                            .flatMap(savedUser -> {
                                String htmlContent = emailTemplateService
                                        .buildActivationEmail(email, newToken,
                                                rolesStr);
                                return emailService.sendHtmlEmail(email,
                                        "Activate Your Account - Alesqui Intelligence",
                                        htmlContent);
                            });
                });
    }
}
