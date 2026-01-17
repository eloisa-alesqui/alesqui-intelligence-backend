package es.alesqui.intelligence.service.access;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import es.alesqui.intelligence.dto.admin.CreateUserRequest;
import es.alesqui.intelligence.dto.admin.GroupWithCountsResponse;
import es.alesqui.intelligence.dto.admin.UpdateUserRequest;
import es.alesqui.intelligence.dto.admin.UpdateUserResponse;
import es.alesqui.intelligence.dto.admin.UpdateUserRolesRequest;
import es.alesqui.intelligence.dto.admin.UserDetailResponse;
import es.alesqui.intelligence.dto.admin.UserSummaryResponse;
import es.alesqui.intelligence.model.access.Group;
import es.alesqui.intelligence.model.access.GroupMembership;
import es.alesqui.intelligence.model.audit.AuditAction;
import es.alesqui.intelligence.model.audit.EntityType;
import es.alesqui.intelligence.model.core.User;
import es.alesqui.intelligence.model.core.enums.Role;
import es.alesqui.intelligence.repository.ApiGroupLinkRepository;
import es.alesqui.intelligence.repository.GroupMembershipRepository;
import es.alesqui.intelligence.repository.GroupRepository;
import es.alesqui.intelligence.repository.UserRepository;
import es.alesqui.intelligence.service.audit.AuditService;
import es.alesqui.intelligence.service.notification.EmailService;
import es.alesqui.intelligence.service.notification.EmailTemplateService;
import es.alesqui.intelligence.service.security.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service responsible for administrative operations on users.
 * Handles user creation, updates, deletion, activation, and queries.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserManagementService {

    private final UserRepository userRepository;
    private final GroupMembershipRepository membershipRepository;
    private final GroupRepository groupRepository;
    private final ApiGroupLinkRepository apiGroupLinkRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final EmailTemplateService emailTemplateService;
    private final EmailService emailService;
    private final TrialWorkspaceService trialWorkspaceService;
    private final AuditService auditService;

    /**
     * Creates a new user with the specified username, password, and roles.
     * For TRIAL users, automatically creates a workspace group.
     * 
     * @param req the request containing username, password, and roles
     * @param request the HTTP request for audit logging
     * @return Mono containing the newly created user
     */
    public Mono<User> createUser(CreateUserRequest req, ServerHttpRequest request) {
        return this.createUser(req, trialWorkspaceService::createTrialWorkspace)
                .flatMap(user -> {
                    // Log user creation audit event
                    String rolesStr = user.getRoles().stream()
                            .map(Role::getLabel)
                            .collect(Collectors.joining(", "));
                    return auditService.logAction(
                            AuditAction.USER_CREATED,
                            EntityType.USER,
                            user.getId(),
                            user.getUsername(),
                            "User created with roles: " + rolesStr + ", Active: " + user.isActive(),
                            request
                    ).thenReturn(user);
                });
    }

    /**
     * Deletes a user from the system.
     * Prevents deleting own account and the last SUPERADMIN.
     * For TRIAL users, also deletes their auto-created workspace.
     * 
     * @param userId the ID of the user to delete
     * @param currentUsername the username of the current authenticated user (to prevent self-deletion)
     * @param request the HTTP request for audit logging
     * @return Mono signaling completion
     */
    public Mono<Void> deleteUser(String userId, String currentUsername, ServerHttpRequest request) {
        // Load user first for audit logging before deletion
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("User not found: " + userId)))
                .flatMap(user -> {
                    // Perform deletion
                    return this.deleteUser(userId, currentUsername, trialWorkspaceService::deleteTrialUserWorkspace)
                            .then(
                                // Log user deletion audit event
                                auditService.logAction(
                                        AuditAction.USER_DELETED,
                                        EntityType.USER,
                                        user.getId(),
                                        user.getUsername(),
                                        "User deleted",
                                        request
                                )
                            );
                });
    }

    /**
     * Activates a user account with the provided token and password.
     * For TRIAL users, creates their workspace after activation.
     * 
     * @param token    the activation token
     * @param password the new password to set
     * @param request the HTTP request for audit logging
     * @return Mono containing the activated user
     */
    public Mono<User> activateAccount(String token, String password, ServerHttpRequest request) {
        return this.activateAccount(token, password, trialWorkspaceService::createTrialWorkspace)
                .flatMap(user -> {
                    // Log account activation audit event
                    return auditService.logAction(
                            AuditAction.USER_ACTIVATED,
                            EntityType.USER,
                            user.getId(),
                            user.getUsername(),
                            "User account activated",
                            request
                    ).thenReturn(user);
                });
    }

    /**
     * Creates a new user with the specified username, password (optional), and roles.
     * If the user has ROLE_TRIAL and is created with a password (immediately active),
     * automatically creates a workspace group.
     * 
     * Two creation modes:
     * 1. With password: User is immediately active and can log in
     * 2. Without password: User receives activation email with token to set password
     * 
     * @param req the request containing user creation details
     * @param createTrialWorkspaceCallback callback to create trial workspace if needed
     * @return the created user
     */
    public Mono<User> createUser(CreateUserRequest req, java.util.function.Function<User, Mono<Group>> createTrialWorkspaceCallback) {
        String username = req.getUsername().trim();
        boolean hasPassword = req.getPassword() != null && !req.getPassword().isBlank();
        boolean hasTrialRole = req.getRoles() != null && req.getRoles().contains(Role.ROLE_TRIAL);

        // Check if user already exists
        return userRepository.findByUsername(username)
                .flatMap(existingUser -> Mono.<User>error(
                        new IllegalArgumentException("User already exists with username: " + username)))
                .switchIfEmpty(Mono.defer(() -> {
                    User.UserBuilder userBuilder = User.builder()
                            .username(username)
                            .roles(req.getRoles())
                            .createdAt(Instant.now());

                    if (hasPassword) {
                        // Mode 1: Immediate activation with password
                        userBuilder
                                .password(passwordEncoder.encode(req.getPassword()))
                                .isActive(true);

                        log.info("Creating active user with password: {}", username);
                        return userRepository.save(userBuilder.build())
                                .flatMap(savedUser -> {
                                    // If ROLE_TRIAL user created with password, create workspace immediately
                                    if (hasTrialRole && createTrialWorkspaceCallback != null) {
                                        log.info(
                                                "User {} has ROLE_TRIAL and was created with password, creating automatic workspace",
                                                savedUser.getUsername());
                                        return createTrialWorkspaceCallback.apply(savedUser)
                                                .thenReturn(savedUser);
                                    }
                                    return Mono.just(savedUser);
                                });

                    } else {
                        // Mode 2: Pending activation, send email
                        String token = tokenService.generateSecureToken();
                        Instant expiration = tokenService.calculateActivationExpiration();

                        userBuilder
                                .password(null)
                                .isActive(false)
                                .activationToken(token)
                                .activationTokenExpiresAt(expiration);

                        User newUser = userBuilder.build();
                        String rolesStr = req.getRoles().stream()
                                .map(Role::getLabel)
                                .reduce((a, b) -> a + ", " + b)
                                .orElse("N/A");

                        log.info("Creating inactive user (pending activation): {}", username);

                        // Send email first, only save user if email succeeds
                        String htmlContent = emailTemplateService.buildActivationEmail(username, token, rolesStr);
                        return emailService.sendHtmlEmail(username, "Activate Your Account - Alesqui Intelligence", htmlContent)
                                .then(userRepository.save(newUser))
                                .doOnError(e -> log.error(
                                        "Failed to send activation email for user {}, not saving user", username, e));
                    }
                }));
    }

    /**
     * Updates the roles of a user by username.
     * 
     * @param username the username of the user
     * @param req      the request containing new roles
     * @param request the HTTP request for audit logging
     * @return the updated user
     */
    public Mono<User> updateUserRoles(String username, UpdateUserRolesRequest req, ServerHttpRequest request) {
        Set<Role> newRoles = req.getRoles();
        return userRepository.findByUsername(username)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("User not found: " + username)))
                .flatMap(user -> {
                    Set<Role> oldRoles = user.getRoles();
                    user.setRoles(newRoles.stream().collect(Collectors.toSet()));
                    return userRepository.save(user)
                            .flatMap(updatedUser -> {
                                // Log role modification audit event
                                String oldRolesStr = oldRoles.stream().map(Role::getLabel).collect(Collectors.joining(", "));
                                String newRolesStr = newRoles.stream().map(Role::getLabel).collect(Collectors.joining(", "));
                                return auditService.logAction(
                                        AuditAction.USER_ROLES_CHANGED,
                                        EntityType.USER,
                                        updatedUser.getId(),
                                        updatedUser.getUsername(),
                                        "Roles changed from [" + oldRolesStr + "] to [" + newRolesStr + "]",
                                        request
                                ).thenReturn(updatedUser);
                            });
                });
    }

    /**
     * Updates the roles of a user by user ID.
     * 
     * @param userId the ID of the user
     * @param req    the request containing new roles
     * @param request the HTTP request for audit logging
     * @return the updated user
     */
    public Mono<User> updateUserRolesById(String userId, UpdateUserRolesRequest req, ServerHttpRequest request) {
        Set<Role> newRoles = req.getRoles();
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("User not found: " + userId)))
                .flatMap(user -> {
                    Set<Role> oldRoles = user.getRoles();
                    user.setRoles(newRoles.stream().collect(Collectors.toSet()));
                    return userRepository.save(user)
                            .flatMap(updatedUser -> {
                                // Log role modification audit event
                                String oldRolesStr = oldRoles.stream().map(Role::getLabel).collect(Collectors.joining(", "));
                                String newRolesStr = newRoles.stream().map(Role::getLabel).collect(Collectors.joining(", "));
                                return auditService.logAction(
                                        AuditAction.USER_ROLES_CHANGED,
                                        EntityType.USER,
                                        updatedUser.getId(),
                                        updatedUser.getUsername(),
                                        "Roles changed from [" + oldRolesStr + "] to [" + newRolesStr + "]",
                                        request
                                ).thenReturn(updatedUser);
                            });
                });
    }

    /**
     * Partially updates a user's information (username, password, and/or roles).
     * 
     * @param userId the ID of the user to update
     * @param req    the request containing fields to update (all optional)
     * @param request the HTTP request for audit logging
     * @return the updated user response with group count
     */
    public Mono<UpdateUserResponse> updateUser(String userId, UpdateUserRequest req, ServerHttpRequest request) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("User not found: " + userId)))
                .flatMap(originalUser -> {
                    // Keep a copy of original for audit logging
                    User userBeforeUpdate = User.builder()
                            .id(originalUser.getId())
                            .username(originalUser.getUsername())
                            .roles(originalUser.getRoles())
                            .build();

                    // Update username if provided
                    if (req.getUsername() != null && !req.getUsername().trim().isEmpty()) {
                        String newUsername = req.getUsername().trim();
                        if (!newUsername.equals(originalUser.getUsername())) {
                            // Check if username is already taken
                            return userRepository.findByUsername(newUsername)
                                    .flatMap(existingUser -> Mono.<User>error(
                                            new IllegalStateException("Username already exists: " + newUsername)))
                                    .switchIfEmpty(Mono.defer(() -> {
                                        originalUser.setUsername(newUsername);
                                        return Mono.just(originalUser);
                                    }))
                                    .flatMap(u -> continueUpdate(u, req, userBeforeUpdate, request));
                        }
                    }

                    return continueUpdate(originalUser, req, userBeforeUpdate, request);
                })
                .flatMap(user -> {
                    // Get group count
                    return membershipRepository.findByUserId(userId)
                            .count()
                            .map(groupCount -> UpdateUserResponse.builder()
                                    .id(user.getId())
                                    .username(user.getUsername())
                                    .roles(user.getRoles() == null ? List.of()
                                            : user.getRoles().stream().map(Role::name).toList())
                                    .createdAt(user.getCreatedAt())
                                    .groupCount(groupCount)
                                    .build());
                });
    }

    /**
     * Helper method to continue updating user fields after username validation.
     */
    private Mono<User> continueUpdate(User user, UpdateUserRequest req, User userBeforeUpdate, ServerHttpRequest request) {
        boolean updated = false;
        boolean passwordChanged = false;
        boolean rolesChanged = false;
        Set<Role> oldRoles = user.getRoles();

        // Update password if provided
        if (req.getPassword() != null && !req.getPassword().trim().isEmpty()) {
            String encodedPassword = passwordEncoder.encode(req.getPassword());
            user.setPassword(encodedPassword);
            updated = true;
            passwordChanged = true;
        }

        // Update roles if provided
        if (req.getRoles() != null && !req.getRoles().isEmpty()) {
            // Check if trying to remove last SUPERADMIN
            if (user.getRoles().contains(Role.ROLE_SUPERADMIN) && !req.getRoles().contains(Role.ROLE_SUPERADMIN)) {
                // Count total SUPERADMIN users
                boolean finalUpdated = updated;
                boolean finalPasswordChanged = passwordChanged;
                return userRepository.findAll()
                        .filter(u -> u.getRoles().contains(Role.ROLE_SUPERADMIN))
                        .count()
                        .flatMap(count -> {
                            if (count <= 1) {
                                return Mono.error(new IllegalStateException(
                                        "Cannot remove SUPERADMIN role from the last SUPERADMIN user"));
                            }
                            user.setRoles(req.getRoles().stream().collect(Collectors.toSet()));
                            return userRepository.save(user)
                                    .flatMap(savedUser -> logUserUpdateAudit(savedUser, userBeforeUpdate, finalPasswordChanged, true, oldRoles, request));
                        });
            }
            user.setRoles(req.getRoles().stream().collect(Collectors.toSet()));
            updated = true;
            rolesChanged = true;
        }

        if (updated) {
            boolean finalPasswordChanged = passwordChanged;
            boolean finalRolesChanged = rolesChanged;
            return userRepository.save(user)
                    .flatMap(savedUser -> logUserUpdateAudit(savedUser, userBeforeUpdate, finalPasswordChanged, finalRolesChanged, oldRoles, request));
        }
        return Mono.just(user);
    }

    /**
     * Helper method to log audit events for user updates.
     */
    private Mono<User> logUserUpdateAudit(User updatedUser, User userBeforeUpdate, boolean passwordChanged, 
                                          boolean rolesChanged, Set<Role> oldRoles, ServerHttpRequest request) {
        Mono<Void> auditMono = Mono.empty();
        
        // Log password change
        if (passwordChanged) {
            auditMono = auditMono.then(
                    auditService.logAction(
                            AuditAction.USER_PASSWORD_CHANGED,
                            EntityType.USER,
                            updatedUser.getId(),
                            updatedUser.getUsername(),
                            "User password changed",
                            request
                    )
            );
        }
        
        // Log roles change
        if (rolesChanged) {
            String oldRolesStr = oldRoles.stream().map(Role::getLabel).collect(Collectors.joining(", "));
            String newRolesStr = updatedUser.getRoles().stream().map(Role::getLabel).collect(Collectors.joining(", "));
            auditMono = auditMono.then(
                    auditService.logAction(
                            AuditAction.USER_ROLES_CHANGED,
                            EntityType.USER,
                            updatedUser.getId(),
                            updatedUser.getUsername(),
                            "Roles changed from [" + oldRolesStr + "] to [" + newRolesStr + "]",
                            request
                    )
            );
        }
        
        return auditMono.thenReturn(updatedUser);
    }

    /**
     * Deletes a user from the system with comprehensive cleanup.
     * 
     * Validations:
     * - Prevents self-deletion
     * - Prevents deletion of the last SUPERADMIN
     * - Validates user exists
     * 
     * Cleanup operations:
     * - Removes all group memberships
     * - For TRIAL users: callback to delete their auto-created workspace
     * 
     * @param userId          the ID of the user to delete
     * @param currentUsername the username of the authenticated user (to prevent self-deletion)
     * @param deleteTrialWorkspaceCallback callback to delete trial workspace if needed
     * @return Mono signaling completion
     */
    public Mono<Void> deleteUser(String userId, String currentUsername, 
            java.util.function.Function<String, Mono<Void>> deleteTrialWorkspaceCallback) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("User not found: " + userId)))
                .flatMap(user -> {
                    // Validation 1: Prevent self-deletion
                    if (user.getUsername().equals(currentUsername)) {
                        return Mono.error(new IllegalStateException("Cannot delete your own account"));
                    }

                    // Validation 2: Check if user is the last SUPERADMIN
                    if (user.getRoles().contains(Role.ROLE_SUPERADMIN)) {
                        return userRepository.findAll()
                                .filter(u -> u.getRoles().contains(Role.ROLE_SUPERADMIN))
                                .count()
                                .flatMap(count -> {
                                    if (count <= 1) {
                                        return Mono.error(new IllegalStateException(
                                                "Cannot delete the last SUPERADMIN user"));
                                    }
                                    return proceedWithUserDeletion(user, deleteTrialWorkspaceCallback);
                                });
                    }

                    return proceedWithUserDeletion(user, deleteTrialWorkspaceCallback);
                });
    }

    /**
     * Performs the actual user deletion with cleanup.
     * 
     * @param user the user to delete
     * @param deleteTrialWorkspaceCallback callback to delete trial workspace
     * @return Mono signaling completion
     */
    private Mono<Void> proceedWithUserDeletion(User user, 
            java.util.function.Function<String, Mono<Void>> deleteTrialWorkspaceCallback) {
        String userId = user.getId();
        boolean isTrialUser = user.getRoles().contains(Role.ROLE_TRIAL);

        log.info("Deleting user: {} (ID: {}, TRIAL: {})", user.getUsername(), userId, isTrialUser);

        // Step 1: Delete all group memberships
        Mono<Void> deleteMemberships = membershipRepository.findByUserId(userId)
                .flatMap(membership -> membershipRepository.deleteById(membership.getId()))
                .then()
                .doOnSuccess(v -> log.debug("Deleted all memberships for user {}", userId));

        // Step 2: For TRIAL users, delete their auto-created workspace
        Mono<Void> deleteTrialWorkspace = (isTrialUser && deleteTrialWorkspaceCallback != null)
                ? deleteTrialWorkspaceCallback.apply(userId)
                : Mono.empty();

        // Step 3: Delete the user
        Mono<Void> deleteUser = userRepository.deleteById(userId)
                .doOnSuccess(v -> log.info("Successfully deleted user: {} (ID: {})", user.getUsername(), userId));

        // Execute all steps in sequence
        return deleteMemberships
                .then(deleteTrialWorkspace)
                .then(deleteUser)
                .onErrorResume(e -> {
                    log.error("Failed to delete user {} (ID: {})", user.getUsername(), userId, e);
                    return Mono.error(e);
                });
    }

    /**
     * Lists all users in the application.
     * 
     * @return a Flux of user summary responses
     */
    public Flux<UserSummaryResponse> listAllUsers() {
        return userRepository.findAll()
                .flatMap(user -> membershipRepository.findByUserId(user.getId())
                        .count()
                        .map(groupCount -> UserSummaryResponse.builder()
                                .id(user.getId())
                                .username(user.getUsername())
                                .roles(user.getRoles() == null ? List.of()
                                        : user.getRoles().stream().map(Role::name).toList())
                                .active(user.isActive())
                                .groupCount(groupCount.intValue())
                                .build()));
    }

    /**
     * Retrieves detailed information about a user by their ID.
     * 
     * @param userId the ID of the user
     * @return the user detail response with groups information
     */
    public Mono<UserDetailResponse> getUserDetail(String userId) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.empty())
                .flatMap(user -> {
                    // Load user's group memberships
                    Mono<List<GroupWithCountsResponse>> groupsMono = membershipRepository.findByUserId(userId)
                            .collectList()
                            .flatMapMany(memberships -> {
                                log.debug("User {} has {} membership records", userId, memberships.size());
                                if (memberships.isEmpty())
                                    return Flux.empty();
                                var groupIds = memberships.stream().map(GroupMembership::getGroupId).toList();
                                return groupRepository.findAllById(groupIds);
                            })
                            .flatMap(group -> {
                                // For each group, get user count and API count
                                Mono<Long> userCount = membershipRepository.findByGroupId(group.getId()).count();
                                Mono<Long> apiCount = apiGroupLinkRepository.findByGroupId(group.getId()).count();

                                return Mono.zip(userCount, apiCount)
                                        .map(tuple -> GroupWithCountsResponse.builder()
                                                .id(group.getId())
                                                .code(group.getCode())
                                                .name(group.getName())
                                                .description(group.getDescription())
                                                .createdAt(group.getCreatedAt())
                                                .userCount(tuple.getT1())
                                                .apiCount(tuple.getT2())
                                                .build());
                            })
                            .collectList()
                            .doOnNext(list -> log.debug("User {} resolved {} group summaries", userId, list.size()));

                    return groupsMono
                            .map(groups -> UserDetailResponse.builder()
                                    .id(user.getId())
                                    .username(user.getUsername())
                                    .roles(user.getRoles() == null ? List.of()
                                            : user.getRoles().stream().map(Role::name).toList())
                                    .createdAt(user.getCreatedAt())
                                    .active(user.isActive())
                                    .groupCount(groups.size())
                                    .groups(groups)
                                    .build())
                            .doOnNext(r -> log.debug("Built UserDetailResponse for {} with {} groups", userId,
                                    r.getGroupCount()));
                });
    }

    /**
     * Validates an activation token.
     * 
     * @param token the activation token to validate
     * @return Mono containing the user if token is valid, empty if invalid/expired
     */
    public Mono<User> validateActivationToken(String token) {
        return userRepository.findAll()
                .filter(user -> token.equals(user.getActivationToken()))
                .next()
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
     * If the user has ROLE_TRIAL, callback to create workspace group.
     * 
     * @param token    the activation token
     * @param password the new password to set
     * @param createTrialWorkspaceCallback callback to create trial workspace if needed
     * @return Mono containing the activated user
     */
    public Mono<User> activateAccount(String token, String password,
            java.util.function.Function<User, Mono<Group>> createTrialWorkspaceCallback) {
        return validateActivationToken(token)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Invalid or expired activation token")))
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
                                        savedUser.getRoles().contains(Role.ROLE_TRIAL);

                                if (hasTrialRole && createTrialWorkspaceCallback != null) {
                                    log.info("User {} has ROLE_TRIAL, creating automatic workspace",
                                            savedUser.getUsername());
                                    return createTrialWorkspaceCallback.apply(savedUser)
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
                        return Mono.error(new IllegalArgumentException("User is already active"));
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
                                String htmlContent = emailTemplateService.buildActivationEmail(email, newToken, rolesStr);
                                return emailService.sendHtmlEmail(email, "Activate Your Account - Alesqui Intelligence", htmlContent);
                            });
                });
    }

    /**
     * Initiates the password reset process for a user.
     * Generates a password reset token, saves it to the user, and sends a reset email.
     * 
     * @param email the user's email address
     * @return Mono signaling completion
     */
    public Mono<Void> requestPasswordReset(String email, ServerHttpRequest request) {
        return userRepository.findByUsername(email)
                .switchIfEmpty(Mono.defer(() -> {
                    // For security, don't reveal if the email exists or not
                    log.warn("[PasswordReset] Password reset requested for non-existent email: {}", email);
                    return Mono.empty(); // Return empty but don't error
                }))
                .flatMap(user -> {
                    if (!user.isActive()) {
                        // User must be activated first
                        log.warn("[PasswordReset] Password reset requested for inactive user: {}", email);
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
                                log.info("[PasswordReset] Sending password reset email to: {}", email);
                                String htmlContent = emailTemplateService.buildPasswordResetEmail(email, resetToken);
                                return emailService.sendHtmlEmail(email, "Password Reset Request - Alesqui Intelligence", htmlContent)
                                        .then(auditService.logAction(
                                                AuditAction.AUTH_PASSWORD_RESET,
                                                EntityType.USER,
                                                savedUser.getId(),
                                                savedUser.getUsername(),
                                                "Password reset requested - email sent",
                                                request
                                        ));
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
                        log.warn("[PasswordReset] Token expired for user: {}", user.getUsername());
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
     * @param token the password reset token
     * @param newPassword the new password to set
     * @param request the HTTP request for audit logging
     * @return Mono containing the updated user
     */
    public Mono<User> resetPassword(String token, String newPassword, ServerHttpRequest request) {
        return validatePasswordResetToken(token)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Invalid or expired password reset token")))
                .flatMap(user -> {
                    // Hash the new password
                    String hashedPassword = passwordEncoder.encode(newPassword);
                    user.setPassword(hashedPassword);
                    
                    // Clear the reset token
                    user.setPasswordResetToken(null);
                    user.setPasswordResetTokenExpiresAt(null);
                    
                    // Ensure user is active (in case they were pending)
                    user.setActive(true);

                    log.info("[PasswordReset] Password successfully reset for user: {}", user.getUsername());

                    return userRepository.save(user)
                            .flatMap(savedUser -> {
                                // Log password reset completion audit event
                                return auditService.logAction(
                                        AuditAction.AUTH_PASSWORD_RESET,
                                        EntityType.USER,
                                        savedUser.getId(),
                                        savedUser.getUsername(),
                                        "Password reset completed successfully",
                                        request
                                ).thenReturn(savedUser);
                            });
                });
    }

    /**
     * Logs a failed user operation for audit purposes.
     * Used in error handlers to track failed administrative actions.
     * 
     * @param action the action that was attempted
     * @param userId the user ID (if known)
     * @param username the username (if known)
     * @param errorMessage the error message
     * @param request the HTTP request for audit logging
     * @return Mono signaling completion
     */
    public Mono<Void> logUserOperationFailure(AuditAction action, String userId, String username,
                                             String errorMessage, ServerHttpRequest request) {
        return auditService.logFailure(
                action,
                EntityType.USER,
                userId != null ? userId : "unknown",
                username != null ? username : "unknown",
                errorMessage,
                request
        );
    }
}
