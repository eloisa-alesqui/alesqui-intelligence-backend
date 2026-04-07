package es.alesqui.intelligence.service.access;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import es.alesqui.intelligence.config.DeploymentConfig;
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
import es.alesqui.intelligence.service.identity.UserService;
import es.alesqui.intelligence.service.notification.EmailService;
import es.alesqui.intelligence.service.notification.EmailTemplateService;
import es.alesqui.intelligence.service.security.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service responsible for administrative operations on users.
 * Handles user creation, updates, deletion, and queries.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserAdminService {

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
    private final UserService userService;
    private final DeploymentConfig deploymentConfig;

    /**
     * Creates a new user with the specified username, password, and roles.
     * For TRIAL users, automatically creates a workspace group.
     *
     * @param req     the request containing username, password, and roles
     * @param request the HTTP request for audit logging
     * @return Mono containing the newly created user
     */
    public Mono<User> createUser(CreateUserRequest req, ServerHttpRequest request) {
        return this.createUser(req, user -> trialWorkspaceService.createTrialWorkspace(user, request))
                .flatMap(user -> {
                    // Log user creation audit event
                    String rolesStr = user.getRoles().stream()
                            .map(Role::getLabel)
                            .collect(Collectors.joining(", "));
                    String details = "User created with roles: " + rolesStr + ", Active: "
                            + user.isActive();

                    // Check if there's an authenticated user (admin creating user) or not (trial
                    // registration)
                    return userService.getCurrentUser()
                            .flatMap(authenticatedUser -> {
                                // Admin is creating the user - log with admin as actor
                                return auditService.logAction(
                                        AuditAction.USER_CREATED,
                                        EntityType.USER,
                                        user.getId(),
                                        user.getUsername(),
                                        details,
                                        request);
                            })
                            .switchIfEmpty(Mono.defer(() -> {
                                // No authenticated user (trial registration) - log with
                                // created user as actor
                                log.debug(
                                        "No authenticated user for USER_CREATED audit, using created user as actor: {}",
                                        user.getUsername());
                                return auditService.logActionWithUser(
                                        AuditAction.USER_CREATED,
                                        EntityType.USER,
                                        user.getId(),
                                        user.getUsername(),
                                        user.getUsername(),
                                        user.getId(),
                                        details,
                                        request);
                            }))
                            .thenReturn(user);
                });
    }

    /**
     * Creates a new user with the specified username, password (optional), and
     * roles.
     * If the user has ROLE_TRIAL and is created with a password (immediately
     * active),
     * automatically creates a workspace group.
     *
     * Two creation modes:
     * 1. With password: User is immediately active and can log in
     * 2. Without password: User receives activation email with token to set
     * password
     *
     * @param req                          the request containing user creation details
     * @param createTrialWorkspaceCallback callback to create trial workspace if needed
     * @return the created user
     */
    public Mono<User> createUser(CreateUserRequest req,
            Function<User, Mono<Group>> createTrialWorkspaceCallback) {
        String username = req.getUsername().trim();
        boolean hasPassword = req.getPassword() != null && !req.getPassword().isBlank();
        boolean hasTrialRole = req.getRoles() != null && req.getRoles().contains(Role.ROLE_TRIAL);

        // Validate: ROLE_TRIAL cannot be created in CORPORATE mode
        if (hasTrialRole && deploymentConfig.isCorporate()) {
            return Mono.error(new IllegalArgumentException(
                "Trial users cannot be created in CORPORATE deployment mode. Please use ROLE_BUSINESS, ROLE_IT, or ROLE_SUPERADMIN."));
        }

        // Check if user already exists
        return userRepository.findByUsername(username)
                .flatMap(existingUser -> Mono.<User>error(
                        new IllegalArgumentException(
                                "User already exists with username: " + username)))
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
                                    // If ROLE_TRIAL user created with password,
                                    // create workspace immediately
                                    if (hasTrialRole && createTrialWorkspaceCallback != null) {
                                        log.info(
                                                "User {} has ROLE_TRIAL and was created with password, creating automatic workspace",
                                                savedUser.getUsername());
                                        return createTrialWorkspaceCallback
                                                .apply(savedUser)
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
                        // Get current user reactively for createdByAdmin field
                        return userService.getCurrentUser()
                                .map(User::getUsername)
                                .flatMap(createdByAdmin -> doSendActivationEmailAndSave(
                                        username, token, rolesStr, newUser, createdByAdmin))
                                .switchIfEmpty(Mono.defer(() -> doSendActivationEmailAndSave(
                                        username, token, rolesStr, newUser, null)))
                                .onErrorResume(e -> {
                                    // Handle any security context errors (e.g. trial registration)
                                    log.debug("No authenticated user creating this account (likely trial registration)");
                                    return doSendActivationEmailAndSave(username, token, rolesStr, newUser, null);
                                });
                    }
                }));
    }

    /**
     * Builds the activation email HTML, sends it, and only persists {@code newUser} if the send
     * succeeds. The three call-sites in {@link #createUser} share identical logic and differ only
     * in whether a {@code createdByAdmin} value is available.
     *
     * @param username       the target user's email / username
     * @param token          the activation token to embed in the email link
     * @param rolesStr       human-readable comma-separated role labels
     * @param newUser        the {@link User} entity to save on successful email delivery
     * @param createdByAdmin the username of the admin who triggered the creation,
     *                       or {@code null} for self-registration / trial flows
     * @return a {@link Mono} that emits the saved {@link User}, or errors if email delivery fails
     */
    private Mono<User> doSendActivationEmailAndSave(String username, String token,
            String rolesStr, User newUser, String createdByAdmin) {
        String htmlContent = emailTemplateService.buildActivationEmail(username, token, rolesStr, createdByAdmin);
        return emailService.sendHtmlEmail(username,
                        "Activate Your Account - Alesqui Intelligence",
                        htmlContent)
                .then(userRepository.save(newUser))
                .doOnError(e -> log.error(
                        "Failed to send activation email for user {}, not saving user",
                        username, e));
    }

    /**
     * Deletes a user from the system.
     * Prevents deleting own account and the last SUPERADMIN.
     * For TRIAL users, also deletes their auto-created workspace.
     *
     * @param userId          the ID of the user to delete
     * @param currentUsername the username of the current authenticated user (to prevent self-deletion)
     * @param request         the HTTP request for audit logging
     * @return Mono signaling completion
     */
    public Mono<Void> deleteUser(String userId, String currentUsername, ServerHttpRequest request) {
        // Load user first for audit logging before deletion
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("User not found: " + userId)))
                .flatMap(user -> {
                    // Perform deletion
                    return this.deleteUser(userId, currentUsername,
                            trialWorkspaceService::deleteTrialUserWorkspace)
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
     * @param userId                       the ID of the user to delete
     * @param currentUsername              the username of the authenticated user
     *                                     (to prevent self-deletion)
     * @param deleteTrialWorkspaceCallback callback to delete trial workspace if
     *                                     needed
     * @return Mono signaling completion
     */
    public Mono<Void> deleteUser(String userId, String currentUsername,
            Function<String, Mono<Void>> deleteTrialWorkspaceCallback) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("User not found: " + userId)))
                .flatMap(user -> {
                    // Validation 1: Prevent self-deletion
                    if (user.getUsername().equals(currentUsername)) {
                        return Mono.error(new IllegalStateException(
                                "Cannot delete your own account"));
                    }

                    // Validation 2: Check if user is the last SUPERADMIN
                    if (user.getRoles().contains(Role.ROLE_SUPERADMIN)) {
                        return userRepository.findAll()
                                .filter(u -> u.getRoles()
                                        .contains(Role.ROLE_SUPERADMIN))
                                .count()
                                .flatMap(count -> {
                                    if (count <= 1) {
                                        return Mono.error(
                                                new IllegalStateException(
                                                        "Cannot delete the last SUPERADMIN user"));
                                    }
                                    return proceedWithUserDeletion(user,
                                            deleteTrialWorkspaceCallback);
                                });
                    }

                    return proceedWithUserDeletion(user, deleteTrialWorkspaceCallback);
                });
    }

    /**
     * Performs the actual user deletion with cleanup.
     *
     * @param user                         the user to delete
     * @param deleteTrialWorkspaceCallback callback to delete trial workspace
     * @return Mono signaling completion
     */
    private Mono<Void> proceedWithUserDeletion(User user,
            Function<String, Mono<Void>> deleteTrialWorkspaceCallback) {
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
                .doOnSuccess(v -> log.info("Successfully deleted user: {} (ID: {})", user.getUsername(),
                        userId));

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
     * Updates the roles of a user by username.
     *
     * @param username the username of the user
     * @param req      the request containing new roles
     * @param request  the HTTP request for audit logging
     * @return the updated user
     */
    public Mono<User> updateUserRoles(String username, UpdateUserRolesRequest req, ServerHttpRequest request) {
        return doUpdateUserRoles(userRepository.findByUsername(username), req.getRoles(), request);
    }

    /**
     * Updates the roles of a user by user ID.
     *
     * @param userId  the ID of the user
     * @param req     the request containing new roles
     * @param request the HTTP request for audit logging
     * @return the updated user
     */
    public Mono<User> updateUserRolesById(String userId, UpdateUserRolesRequest req, ServerHttpRequest request) {
        return doUpdateUserRoles(userRepository.findById(userId), req.getRoles(), request);
    }

    /**
     * Shared implementation for role update operations.
     * Saves the user with the new roles and logs a {@link AuditAction#USER_ROLES_CHANGED} event.
     *
     * @param lookup   a {@code Mono<User>} that resolves the target user (e.g. by username or ID)
     * @param newRoles the set of roles to assign; replaces the user's current roles entirely
     * @param request  the HTTP request used for audit logging context
     * @return a {@code Mono} emitting the updated user, or an error if the user is not found
     */
    private Mono<User> doUpdateUserRoles(Mono<User> lookup, Set<Role> newRoles, ServerHttpRequest request) {
        return lookup
                .switchIfEmpty(Mono.error(new IllegalArgumentException("User not found")))
                .flatMap(user -> {
                    Set<Role> oldRoles = user.getRoles();
                    user.setRoles(newRoles.stream().collect(Collectors.toSet()));
                    return userRepository.save(user)
                            .flatMap(updatedUser -> {
                                String oldRolesStr = oldRoles.stream()
                                        .map(Role::getLabel)
                                        .collect(Collectors.joining(", "));
                                String newRolesStr = newRoles.stream()
                                        .map(Role::getLabel)
                                        .collect(Collectors.joining(", "));
                                return auditService.logAction(
                                        AuditAction.USER_ROLES_CHANGED,
                                        EntityType.USER,
                                        updatedUser.getId(),
                                        updatedUser.getUsername(),
                                        "Roles changed from [" + oldRolesStr
                                                + "] to [" + newRolesStr
                                                + "]",
                                        request).thenReturn(updatedUser);
                            });
                });
    }

    /**
     * Partially updates a user's information (username, password, and/or roles).
     *
     * @param userId  the ID of the user to update
     * @param req     the request containing fields to update (all optional)
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
                                            new IllegalStateException(
                                                    "Username already exists: "
                                                            + newUsername)))
                                    .switchIfEmpty(Mono.defer(() -> {
                                        originalUser.setUsername(newUsername);
                                        return Mono.just(originalUser);
                                    }))
                                    .flatMap(u -> continueUpdate(u, req,
                                            userBeforeUpdate, request));
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
                                            : user.getRoles().stream()
                                                    .map(Role::name)
                                                    .toList())
                                    .createdAt(user.getCreatedAt())
                                    .groupCount(groupCount)
                                    .build());
                });
    }

    /**
     * Helper method to continue updating user fields after username validation.
     */
    private Mono<User> continueUpdate(User user, UpdateUserRequest req, User userBeforeUpdate,
            ServerHttpRequest request) {
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
            if (user.getRoles().contains(Role.ROLE_SUPERADMIN)
                    && !req.getRoles().contains(Role.ROLE_SUPERADMIN)) {
                boolean finalPasswordChanged = passwordChanged;
                return userRepository.findAll()
                        .filter(u -> u.getRoles().contains(Role.ROLE_SUPERADMIN))
                        .count()
                        .flatMap(count -> {
                            if (count <= 1) {
                                return Mono.error(new IllegalStateException(
                                        "Cannot remove SUPERADMIN role from the last SUPERADMIN user"));
                            }
                            user.setRoles(req.getRoles().stream()
                                    .collect(Collectors.toSet()));
                            return userRepository.save(user)
                                    .flatMap(savedUser -> logUserUpdateAudit(
                                            savedUser, userBeforeUpdate,
                                            finalPasswordChanged, true,
                                            oldRoles, request));
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
                    .flatMap(savedUser -> logUserUpdateAudit(savedUser, userBeforeUpdate,
                            finalPasswordChanged, finalRolesChanged, oldRoles, request));
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
                            request));
        }

        // Log roles change
        if (rolesChanged) {
            String oldRolesStr = oldRoles.stream().map(Role::getLabel).collect(Collectors.joining(", "));
            String newRolesStr = updatedUser.getRoles().stream().map(Role::getLabel)
                    .collect(Collectors.joining(", "));
            auditMono = auditMono.then(
                    auditService.logAction(
                            AuditAction.USER_ROLES_CHANGED,
                            EntityType.USER,
                            updatedUser.getId(),
                            updatedUser.getUsername(),
                            "Roles changed from [" + oldRolesStr + "] to [" + newRolesStr
                                    + "]",
                            request));
        }

        return auditMono.thenReturn(updatedUser);
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
                                        : user.getRoles().stream()
                                                .map(Role::name)
                                                .toList())
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
    /**
     * Counts the total number of registered users in the system.
     *
     * @return a Mono emitting the total user count.
     */
    public Mono<Long> countUsers() {
        return userRepository.count();
    }

    public Mono<UserDetailResponse> getUserDetail(String userId) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.empty())
                .flatMap(user -> {
                    // Load user's group memberships
                    Mono<List<GroupWithCountsResponse>> groupsMono = membershipRepository
                            .findByUserId(userId)
                            .collectList()
                            .flatMapMany(memberships -> {
                                log.debug("User {} has {} membership records", userId,
                                        memberships.size());
                                if (memberships.isEmpty())
                                    return Flux.empty();
                                var groupIds = memberships.stream()
                                        .map(GroupMembership::getGroupId)
                                        .toList();
                                return groupRepository.findAllById(groupIds);
                            })
                            .flatMap(group -> {
                                // For each group, get user count and API count
                                Mono<Long> userCount = membershipRepository
                                        .findByGroupId(group.getId()).count();
                                Mono<Long> apiCount = apiGroupLinkRepository
                                        .findByGroupId(group.getId()).count();

                                return Mono.zip(userCount, apiCount)
                                        .map(tuple -> GroupWithCountsResponse
                                                .builder()
                                                .id(group.getId())
                                                .code(group.getCode())
                                                .name(group.getName())
                                                .description(group
                                                        .getDescription())
                                                .createdAt(group.getCreatedAt())
                                                .userCount(tuple.getT1())
                                                .apiCount(tuple.getT2())
                                                .build());
                            })
                            .collectList()
                            .doOnNext(list -> log.debug(
                                    "User {} resolved {} group summaries", userId,
                                    list.size()));

                    return groupsMono
                            .map(groups -> UserDetailResponse.builder()
                                    .id(user.getId())
                                    .username(user.getUsername())
                                    .roles(user.getRoles() == null ? List.of()
                                            : user.getRoles().stream()
                                                    .map(Role::name)
                                                    .toList())
                                    .createdAt(user.getCreatedAt())
                                    .active(user.isActive())
                                    .groupCount(groups.size())
                                    .groups(groups)
                                    .build())
                            .doOnNext(r -> log.debug(
                                    "Built UserDetailResponse for {} with {} groups",
                                    userId,
                                    r.getGroupCount()));
                });
    }
}
