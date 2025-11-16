package es.alesqui.intelligence.service.access;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import es.alesqui.intelligence.dto.admin.AssignApisRequest;
import es.alesqui.intelligence.dto.admin.AssignGroupsRequest;
import es.alesqui.intelligence.dto.admin.AssignUsersRequest;
import es.alesqui.intelligence.dto.admin.CreateUserRequest;
import es.alesqui.intelligence.dto.admin.GroupCreateRequest;
import es.alesqui.intelligence.dto.admin.GroupUpdateRequest;
import es.alesqui.intelligence.dto.admin.GroupSummaryResponse;
import es.alesqui.intelligence.dto.admin.GroupDetailResponse;
import es.alesqui.intelligence.dto.admin.GroupWithCountsResponse;
import es.alesqui.intelligence.dto.admin.ApiSummaryResponse;
import es.alesqui.intelligence.dto.admin.UserSummaryResponse;
import es.alesqui.intelligence.dto.admin.UserDetailResponse;
import es.alesqui.intelligence.dto.admin.UpdateUserRolesRequest;
import es.alesqui.intelligence.dto.admin.UpdateUserRequest;
import es.alesqui.intelligence.dto.admin.UpdateUserResponse;
import es.alesqui.intelligence.model.access.ApiGroupLink;
import es.alesqui.intelligence.model.access.Group;
import es.alesqui.intelligence.model.access.GroupMembership;
import es.alesqui.intelligence.model.core.User;
import es.alesqui.intelligence.model.core.enums.Role;
import es.alesqui.intelligence.repository.ApiGroupLinkRepository;
import es.alesqui.intelligence.repository.GroupMembershipRepository;
import es.alesqui.intelligence.repository.GroupRepository;
import es.alesqui.intelligence.repository.UserRepository;
import es.alesqui.intelligence.service.UnifiedApiService;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Administrative service operations restricted to SUPERADMIN role.
 * Provides reactive methods for managing groups, memberships, API links, and
 * user roles.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccessAdminService {

    /**
     * Repository for managing groups.
     */
    private final GroupRepository groupRepository;

    /**
     * Repository for managing group memberships.
     */
    private final GroupMembershipRepository membershipRepository;

    /**
     * Repository for managing API group links.
     */
    private final ApiGroupLinkRepository apiGroupLinkRepository;

    /**
     * Repository for managing users.
     */
    private final UserRepository userRepository;

    /**
     * Service for unified API operations.
     */
    private final UnifiedApiService unifiedApiService;

    /**
     * Password encoder for hashing passwords.
     */
    private final PasswordEncoder passwordEncoder;

    /**
     * Service for sending activation emails.
     */
    private final es.alesqui.intelligence.service.identity.ActivationEmailService activationEmailService;

    /**
     * Creates a new group.
     * 
     * @param req the request containing group creation details
     * @return the created group
     */
    public Mono<Group> createGroup(GroupCreateRequest req) {
        Group g = new Group();
        g.setCode(req.getCode().trim());
        g.setName(req.getName().trim());
        g.setDescription(req.getDescription());
        g.setCreatedAt(Instant.now());
        return groupRepository.existsByCodeIgnoreCase(g.getCode())
                .flatMap(exists -> exists
                        ? Mono.error(new IllegalArgumentException("Group code already exists: " + g.getCode()))
                        : groupRepository.save(g));
    }

    /**
     * Updates an existing group.
     * 
     * @param groupId the ID of the group to update
     * @param req     the request containing updated group details
     * @return the updated group
     */
    public Mono<Group> updateGroup(String groupId, GroupUpdateRequest req) {
        return groupRepository.findById(groupId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Group not found: " + groupId)))
                .flatMap(g -> {
                    g.setName(req.getName().trim());
                    g.setDescription(req.getDescription());
                    return groupRepository.save(g);
                });
    }

    /**
     * Deletes a group by its ID.
     * Prevents deletion if the group has any users or APIs associated with it.
     * 
     * @param groupId the ID of the group to delete
     * @return a Mono signaling completion
     */
    public Mono<Void> deleteGroup(String groupId) {
        return groupRepository.findById(groupId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Group not found: " + groupId)))
                .flatMap(group -> {
                    // Check if group has any memberships
                    Mono<Long> membershipCount = membershipRepository.findByGroupId(groupId).count();
                    // Check if group has any API links
                    Mono<Long> apiLinkCount = apiGroupLinkRepository.findByGroupId(groupId).count();

                    return Mono.zip(membershipCount, apiLinkCount)
                            .flatMap(tuple -> {
                                long userCount = tuple.getT1();
                                long apiCount = tuple.getT2();

                                if (userCount > 0 && apiCount > 0) {
                                    return Mono.error(new IllegalStateException(
                                            "Cannot delete group: it has " + userCount + " user(s) and " + apiCount
                                                    + " API(s) associated"));
                                } else if (userCount > 0) {
                                    return Mono.error(new IllegalStateException(
                                            "Cannot delete group: it has " + userCount + " user(s) associated"));
                                } else if (apiCount > 0) {
                                    return Mono.error(new IllegalStateException(
                                            "Cannot delete group: it has " + apiCount + " API(s) associated"));
                                }

                                return groupRepository.deleteById(groupId);
                            });
                });
    }

    /**
     * Assigns APIs to a group.
     * 
     * @param groupId the ID of the group
     * @param req     the request containing API IDs to assign
     * @return a Flux of assigned API group links
     */
    public Flux<ApiGroupLink> assignApis(String groupId, AssignApisRequest req) {
        // Validate group exists
        Mono<Group> groupMono = groupRepository.findById(groupId)
                .switchIfEmpty(Mono.error(new IllegalStateException("Group not found: " + groupId)));
        return groupMono.flatMapMany(g -> Flux.fromIterable(req.getApiIds())
                .flatMap(apiId -> unifiedApiService.findById(apiId)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("API not found: " + apiId)))
                        .then(apiGroupLinkRepository.existsByApiIdAndGroupId(apiId, groupId)
                                .flatMap(exists -> exists ? Mono.empty()
                                        : apiGroupLinkRepository.save(buildLink(apiId, groupId))))));
    }

    /**
     * Removes an API from a group by deleting the ApiGroupLink.
     * 
     * @param groupId the ID of the group
     * @param apiId   the ID of the API to remove
     * @return a Mono signaling completion
     */
    public Mono<Void> removeApiFromGroup(String groupId, String apiId) {
        return groupRepository.findById(groupId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Group not found: " + groupId)))
                .then(unifiedApiService.findById(apiId)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("API not found: " + apiId))))
                .then(apiGroupLinkRepository.existsByApiIdAndGroupId(apiId, groupId)
                        .flatMap(exists -> {
                            if (!exists) {
                                return Mono.error(new IllegalArgumentException(
                                        "API " + apiId + " is not linked to group " + groupId));
                            }
                            return apiGroupLinkRepository.findByApiId(apiId)
                                    .filter(link -> link.getGroupId().equals(groupId))
                                    .next()
                                    .flatMap(link -> apiGroupLinkRepository.deleteById(link.getId()));
                        }));
    }

    /**
     * Assigns users to a group.
     * 
     * @param groupId the ID of the group
     * @param req     the request containing user IDs to assign
     * @return a Flux of assigned group memberships
     */
    public Flux<GroupMembership> assignUsers(String groupId, AssignUsersRequest req) {
        Mono<Group> groupMono = groupRepository.findById(groupId)
                .switchIfEmpty(Mono.error(new IllegalStateException("Group not found: " + groupId)));
        return groupMono.flatMapMany(g -> Flux.fromIterable(req.getUserIds())
                .flatMap(userId -> userRepository.findById(userId)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("User not found: " + userId)))
                        .then(membershipRepository.existsByUserIdAndGroupId(userId, groupId)
                                .flatMap(exists -> exists ? Mono.empty()
                                        : membershipRepository.save(buildMembership(userId, groupId))))));
    }

    /**
     * Removes a user from a group by deleting the GroupMembership.
     * 
     * @param groupId the ID of the group
     * @param userId  the ID of the user to remove
     * @return a Mono signaling completion
     */
    public Mono<Void> removeUserFromGroup(String groupId, String userId) {
        return groupRepository.findById(groupId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Group not found: " + groupId)))
                .then(userRepository.findById(userId)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("User not found: " + userId))))
                .then(membershipRepository.existsByUserIdAndGroupId(userId, groupId)
                        .flatMap(exists -> {
                            if (!exists) {
                                return Mono.error(new IllegalArgumentException(
                                        "User " + userId + " is not a member of group " + groupId));
                            }
                            return membershipRepository.findByUserId(userId)
                                    .filter(membership -> membership.getGroupId().equals(groupId))
                                    .next()
                                    .flatMap(membership -> membershipRepository.deleteById(membership.getId()));
                        }));
    }

    /**
     * Assigns a user to multiple groups at once.
     * 
     * @param userId the ID of the user
     * @param req    the request containing group IDs to assign the user to
     * @return a Flux of assigned group memberships
     */
    public Flux<GroupMembership> assignGroupsToUser(String userId, AssignGroupsRequest req) {
        // Validate user exists
        Mono<User> userMono = userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("User not found: " + userId)));

        return userMono.flatMapMany(user -> Flux.fromIterable(req.getGroupIds())
                .flatMap(groupId -> groupRepository.findById(groupId)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("Group not found: " + groupId)))
                        .then(membershipRepository.existsByUserIdAndGroupId(userId, groupId)
                                .flatMap(exists -> exists ? Mono.empty()
                                        : membershipRepository.save(buildMembership(userId, groupId))))));
    }

    /**
     * Removes a group from a user by deleting the GroupMembership.
     * This is a convenience method with reversed parameter order for user-centric
     * endpoints.
     * 
     * @param userId  the ID of the user
     * @param groupId the ID of the group to remove the user from
     * @return a Mono signaling completion
     */
    public Mono<Void> removeGroupFromUser(String userId, String groupId) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("User not found: " + userId)))
                .then(groupRepository.findById(groupId)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("Group not found: " + groupId))))
                .then(membershipRepository.existsByUserIdAndGroupId(userId, groupId)
                        .flatMap(exists -> {
                            if (!exists) {
                                return Mono.error(new IllegalArgumentException(
                                        "User " + userId + " is not a member of group " + groupId));
                            }
                            return membershipRepository.findByUserId(userId)
                                    .filter(membership -> membership.getGroupId().equals(groupId))
                                    .next()
                                    .flatMap(membership -> membershipRepository.deleteById(membership.getId()));
                        }));
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
     * @param req the request containing user creation details
     * @return the created user
     */
    public Mono<User> createUser(CreateUserRequest req) {
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
                                    if (hasTrialRole) {
                                        log.info(
                                                "User {} has ROLE_TRIAL and was created with password, creating automatic workspace",
                                                savedUser.getUsername());
                                        return createTrialWorkspace(savedUser)
                                                .thenReturn(savedUser);
                                    }
                                    return Mono.just(savedUser);
                                });

                    } else {
                        // Mode 2: Pending activation, send email
                        String token = activationEmailService.generateActivationToken();
                        Instant expiration = activationEmailService.calculateTokenExpiration();

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
                        return activationEmailService.sendActivationEmail(username, token, rolesStr)
                                .then(userRepository.save(newUser))
                                .doOnError(e -> log.error(
                                        "Failed to send activation email for user {}, not saving user", username, e));
                    }
                }));
    }

    /**
     * Updates the roles of a user.
     * 
     * @param username the username of the user
     * @param req      the request containing new roles
     * @return the updated user
     */
    public Mono<User> updateUserRoles(String username, UpdateUserRolesRequest req) {
        Set<Role> newRoles = req.getRoles();
        return userRepository.findByUsername(username)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("User not found: " + username)))
                .flatMap(user -> {
                    user.setRoles(newRoles.stream().collect(Collectors.toSet()));
                    return userRepository.save(user);
                });
    }

    /**
     * Updates the roles of a user by user ID.
     * 
     * @param userId the ID of the user
     * @param req    the request containing new roles
     * @return the updated user
     */
    public Mono<User> updateUserRolesById(String userId, UpdateUserRolesRequest req) {
        Set<Role> newRoles = req.getRoles();
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("User not found: " + userId)))
                .flatMap(user -> {
                    user.setRoles(newRoles.stream().collect(Collectors.toSet()));
                    return userRepository.save(user);
                });
    }

    /**
     * Partially updates a user's information (username, password, and/or roles).
     * 
     * @param userId the ID of the user to update
     * @param req    the request containing fields to update (all optional)
     * @return the updated user response with group count
     */
    public Mono<UpdateUserResponse> updateUser(String userId, UpdateUserRequest req) {
        return userRepository.findById(userId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("User not found: " + userId)))
                .flatMap(user -> {
                    boolean updated = false;

                    // Update username if provided
                    if (req.getUsername() != null && !req.getUsername().trim().isEmpty()) {
                        String newUsername = req.getUsername().trim();
                        if (!newUsername.equals(user.getUsername())) {
                            // Check if username is already taken
                            return userRepository.findByUsername(newUsername)
                                    .flatMap(existingUser -> Mono.<User>error(
                                            new IllegalStateException("Username already exists: " + newUsername)))
                                    .switchIfEmpty(Mono.defer(() -> {
                                        user.setUsername(newUsername);
                                        return Mono.just(user);
                                    }))
                                    .flatMap(u -> continueUpdate(u, req));
                        }
                    }

                    return continueUpdate(user, req);
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
    private Mono<User> continueUpdate(User user, UpdateUserRequest req) {
        boolean updated = false;

        // Update password if provided
        if (req.getPassword() != null && !req.getPassword().trim().isEmpty()) {
            String encodedPassword = passwordEncoder.encode(req.getPassword());
            user.setPassword(encodedPassword);
            updated = true;
        }

        // Update roles if provided
        if (req.getRoles() != null && !req.getRoles().isEmpty()) {
            // Check if trying to remove last SUPERADMIN
            if (user.getRoles().contains(Role.ROLE_SUPERADMIN) && !req.getRoles().contains(Role.ROLE_SUPERADMIN)) {
                // Count total SUPERADMIN users
                return userRepository.findAll()
                        .filter(u -> u.getRoles().contains(Role.ROLE_SUPERADMIN))
                        .count()
                        .flatMap(count -> {
                            if (count <= 1) {
                                return Mono.error(new IllegalStateException(
                                        "Cannot remove SUPERADMIN role from the last SUPERADMIN user"));
                            }
                            user.setRoles(req.getRoles().stream().collect(Collectors.toSet()));
                            return userRepository.save(user);
                        });
            }
            user.setRoles(req.getRoles().stream().collect(Collectors.toSet()));
            updated = true;
        }

        if (updated) {
            return userRepository.save(user);
        }
        return Mono.just(user);
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
     * - For TRIAL users: deletes their auto-created workspace including all API
     * links
     * 
     * @param userId          the ID of the user to delete
     * @param currentUsername the username of the authenticated user (to prevent
     *                        self-deletion)
     * @return Mono signaling completion
     */
    public Mono<Void> deleteUser(String userId, String currentUsername) {
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
                                    return proceedWithUserDeletion(user);
                                });
                    }

                    return proceedWithUserDeletion(user);
                });
    }

    /**
     * Performs the actual user deletion with cleanup.
     * 
     * @param user the user to delete
     * @return Mono signaling completion
     */
    private Mono<Void> proceedWithUserDeletion(User user) {
        String userId = user.getId();
        boolean isTrialUser = user.getRoles().contains(Role.ROLE_TRIAL);

        log.info("Deleting user: {} (ID: {}, TRIAL: {})", user.getUsername(), userId, isTrialUser);

        // Step 1: Delete all group memberships
        Mono<Void> deleteMemberships = membershipRepository.findByUserId(userId)
                .flatMap(membership -> membershipRepository.deleteById(membership.getId()))
                .then()
                .doOnSuccess(v -> log.debug("Deleted all memberships for user {}", userId));

        // Step 2: For TRIAL users, delete their auto-created workspace
        Mono<Void> deleteTrialWorkspace = isTrialUser
                ? deleteTrialUserWorkspace(userId)
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
     * Deletes the auto-created workspace for a TRIAL user.
     * Includes deletion of all APIs and API links associated with the workspace.
     * 
     * @param userId the ID of the TRIAL user
     * @return Mono signaling completion
     */
    private Mono<Void> deleteTrialUserWorkspace(String userId) {
        String expectedGroupCode = "trial-" + userId;

        log.debug("Looking for trial workspace with code: {}", expectedGroupCode);

        return groupRepository.findByCodeIgnoreCase(expectedGroupCode)
                .flatMap(group -> {
                    log.info("Deleting trial workspace: {} (ID: {})", group.getName(), group.getId());

                    // Step 1: Check if other users are in this group (safety check)
                    return membershipRepository.findByGroupId(group.getId())
                            .count()
                            .flatMap(memberCount -> {
                                if (memberCount > 0) {
                                    log.warn("Trial workspace {} has {} other members, skipping deletion",
                                            group.getCode(), memberCount);
                                    return Mono.error(new IllegalStateException(
                                            "Trial workspace has other members, cannot delete"));
                                }

                                // Step 2: Delete APIs and their links for this group
                                Mono<Void> deleteApisAndLinks = apiGroupLinkRepository.findByGroupId(group.getId())
                                        .<Void>flatMap(link -> apiGroupLinkRepository.deleteById(link.getId())
                                                .then(unifiedApiService.deleteApi(link.getApiId()))
                                                .doOnSuccess(v -> log.debug("Deleted API {} and its link for group {}",
                                                        link.getApiId(), group.getId())))
                                        .then()
                                        .doOnSuccess(v -> log.debug("Deleted all APIs and links for group {}",
                                                group.getId()));

                                // Step 3: Delete the group itself
                                Mono<Void> deleteGroup = groupRepository.deleteById(group.getId())
                                        .doOnSuccess(v -> log.info("Deleted trial workspace: {}", group.getCode()));

                                return deleteApisAndLinks.then(deleteGroup);
                            });
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("No trial workspace found with code: {}", expectedGroupCode);
                    return Mono.empty();
                }));
    }

    /**
     * Automatically links an API to a TRIAL user's workspace if the user has ROLE_TRIAL.
     * This method is called after an API is created.
     * 
     * @param apiId the ID of the newly created API
     * @param username the username of the user who created the API
     * @return Mono signaling completion
     */
    public Mono<Void> autoLinkApiToTrialWorkspace(String apiId, String username) {
        return userRepository.findByUsername(username)
            .flatMap(user -> {
                // Check if user has ROLE_TRIAL
                boolean isTrialUser = user.getRoles() != null && user.getRoles().contains(Role.ROLE_TRIAL);
                
                if (!isTrialUser) {
                    log.debug("User {} is not a TRIAL user, skipping auto-link", username);
                    return Mono.empty();
                }
                
                // Find the user's trial workspace
                String workspaceCode = "trial-" + user.getId();
                log.info("User {} is TRIAL, auto-linking API {} to workspace {}", username, apiId, workspaceCode);
                
                return groupRepository.findByCodeIgnoreCase(workspaceCode)
                    .flatMap(group -> {
                        // Check if link already exists
                        return apiGroupLinkRepository.existsByApiIdAndGroupId(apiId, group.getId())
                            .flatMap(exists -> {
                                if (exists) {
                                    log.debug("API {} already linked to group {}", apiId, group.getId());
                                    return Mono.empty();
                                }
                                
                                // Create the link
                                ApiGroupLink link = buildLink(apiId, group.getId());
                                return apiGroupLinkRepository.save(link)
                                    .doOnSuccess(savedLink -> log.info(
                                        "Successfully auto-linked API {} to trial workspace {} for user {}", 
                                        apiId, workspaceCode, username))
                                    .then();
                            });
                    })
                    .switchIfEmpty(Mono.defer(() -> {
                        log.warn("Trial workspace {} not found for user {}, cannot auto-link API", 
                            workspaceCode, username);
                        return Mono.empty();
                    }));
            })
            .onErrorResume(e -> {
                log.error("Failed to auto-link API {} for user {}", apiId, username, e);
                return Mono.empty(); // Don't fail the API creation if auto-link fails
            });
    }

    /**
     * Lists all groups with user and API counts.
     * 
     * @return a Flux of group summary responses
     */
    public Flux<GroupSummaryResponse> listGroupsWithCounts() {
        return groupRepository.findAll()
                .flatMap(group -> Mono.zip(
                        membershipRepository.findByGroupId(group.getId()).count(),
                        apiGroupLinkRepository.findByGroupId(group.getId()).count())
                        .map(tuple -> GroupSummaryResponse.builder()
                                .id(group.getId())
                                .code(group.getCode())
                                .name(group.getName())
                                .description(group.getDescription())
                                .createdAt(group.getCreatedAt())
                                .userCount(tuple.getT1())
                                .apiCount(tuple.getT2())
                                .build()));
    }

    /**
     * Lists all orphan APIs (APIs not linked to any group).
     * 
     * @return a Flux of API summary responses for APIs without group links
     */
    public Flux<ApiSummaryResponse> listOrphanApis() {
        return unifiedApiService.findAll()
                .flatMap(api -> apiGroupLinkRepository.findByApiId(api.getId())
                        .hasElements()
                        .filter(hasLinks -> !hasLinks)
                        .map(hasLinks -> ApiSummaryResponse.builder()
                                .id(api.getId())
                                .name(api.getName())
                                .description(api.getDescription())
                                .active(api.isActive())
                                .version(api.getVersion())
                                .tags(api.getTags() == null ? List.of()
                                        : api.getTags().stream().map(t -> t.getName()).toList())
                                .isPublic(true)
                                .build()));
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
     * Builds an API group link.
     * 
     * @param apiId   the ID of the API
     * @param groupId the ID of the group
     * @return the created API group link
     */
    private ApiGroupLink buildLink(String apiId, String groupId) {
        ApiGroupLink l = new ApiGroupLink();
        l.setApiId(apiId);
        l.setGroupId(groupId);
        l.setCreatedAt(Instant.now());
        return l;
    }

    /**
     * Builds a group membership.
     * 
     * @param userId  the ID of the user
     * @param groupId the ID of the group
     * @return the created group membership
     */
    private GroupMembership buildMembership(String userId, String groupId) {
        GroupMembership m = new GroupMembership();
        m.setUserId(userId);
        m.setGroupId(groupId);
        m.setCreatedAt(Instant.now());
        return m;
    }

    /**
     * Creates a workspace group automatically for TRIAL users.
     * Group name: "{username}'s Workspace"
     * Group code: "trial-{userId}"
     * 
     * @param user the TRIAL user for whom to create the workspace
     * @return Mono of the created Group
     */
    private Mono<Group> createTrialWorkspace(User user) {
        String username = user.getUsername();
        String displayName = username.contains("@")
                ? username.substring(0, username.indexOf("@"))
                : username;

        Group workspace = new Group();
        workspace.setCode("trial-" + user.getId());
        workspace.setName(displayName + "'s Workspace");
        workspace.setDescription("Auto-created workspace for trial user");
        workspace.setCreatedBy(user.getId());
        workspace.setCreatedAt(Instant.now());

        log.info("Creating trial workspace for user {}: code={}, name={}",
                username, workspace.getCode(), workspace.getName());

        return groupRepository.save(workspace)
                .flatMap(savedGroup -> membershipRepository.save(buildMembership(user.getId(), savedGroup.getId()))
                        .thenReturn(savedGroup))
                .doOnSuccess(g -> log.info("Successfully created trial workspace {} for user {}",
                        g.getCode(), username))
                .doOnError(e -> log.error("Failed to create trial workspace for user {}", username, e));
    }

    /**
     * Retrieves detailed information about a group.
     * 
     * @param groupId the ID of the group
     * @return the group detail response
     */
    public Mono<GroupDetailResponse> getGroupDetail(String groupId) {
        return groupRepository.findById(groupId)
                .switchIfEmpty(Mono.empty())
                .flatMap(group -> {
                    // Load memberships -> users
                    Mono<List<UserSummaryResponse>> usersMono = membershipRepository.findByGroupId(groupId)
                            .collectList()
                            .flatMapMany(members -> {
                                log.debug("Group {} has {} raw membership records", groupId, members.size());
                                if (members.isEmpty())
                                    return Flux.empty();
                                var userIds = members.stream().map(GroupMembership::getUserId).toList();
                                return userRepository.findAllById(userIds);
                            })
                            .flatMap(user -> membershipRepository.findByUserId(user.getId())
                                    .count()
                                    .map(groupCount -> UserSummaryResponse.builder()
                                            .id(user.getId())
                                            .username(user.getUsername())
                                            .roles(user.getRoles() == null ? List.of()
                                                    : user.getRoles().stream().map(Role::name).toList())
                                            .active(user.isActive())
                                            .groupCount(groupCount.intValue())
                                            .build()))
                            .collectList()
                            .doOnNext(list -> log.debug("Group {} resolved {} user summaries", groupId, list.size()));

                    // Load API links -> unified documents
                    Mono<List<ApiSummaryResponse>> apisMono = apiGroupLinkRepository.findByGroupId(groupId)
                            .collectList()
                            .flatMapMany(links -> {
                                log.debug("Group {} has {} api link records", groupId, links.size());
                                if (links.isEmpty())
                                    return Flux.empty();
                                var apiIds = links.stream().map(ApiGroupLink::getApiId).distinct().toList();
                                return Flux.fromIterable(apiIds)
                                        .flatMap(id -> unifiedApiService.findById(id)
                                                .flatMap(doc -> apiGroupLinkRepository.findByApiId(doc.getId())
                                                        .hasElements()
                                                        .map(hasLinks -> ApiSummaryResponse.builder()
                                                                .id(doc.getId())
                                                                .name(doc.getName())
                                                                .description(doc.getDescription())
                                                                .active(doc.isActive())
                                                                .version(doc.getVersion())
                                                                .tags(doc.getTags() == null ? List.of()
                                                                        : doc.getTags().stream().map(t -> t.getName())
                                                                                .toList())
                                                                .isPublic(!hasLinks)
                                                                .build()))
                                                .onErrorResume(e -> {
                                                    log.warn("Failed to load unified API {} for group {}: {}", id,
                                                            groupId, e.getMessage());
                                                    return Mono.empty();
                                                }));
                            })
                            .collectList()
                            .doOnNext(list -> log.debug("Group {} resolved {} api summaries", groupId, list.size()));

                    return Mono.zip(usersMono, apisMono)
                            .map(tuple -> GroupDetailResponse.builder()
                                    .id(group.getId())
                                    .code(group.getCode())
                                    .name(group.getName())
                                    .description(group.getDescription())
                                    .createdAt(group.getCreatedAt())
                                    .userCount(tuple.getT1().size())
                                    .apiCount(tuple.getT2().size())
                                    .users(tuple.getT1())
                                    .apis(tuple.getT2())
                                    .build())
                            .doOnNext(r -> log.debug("Built GroupDetailResponse for {} with {} users and {} apis",
                                    groupId, r.getUserCount(), r.getApiCount()));
                });
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
     * If the user has ROLE_TRIAL, automatically creates a workspace group.
     * 
     * @param token    the activation token
     * @param password the new password to set
     * @return Mono containing the activated user
     */
    public Mono<User> activateAccount(String token, String password) {
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

                                if (hasTrialRole) {
                                    log.info("User {} has ROLE_TRIAL, creating automatic workspace",
                                            savedUser.getUsername());
                                    return createTrialWorkspace(savedUser)
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
                    String newToken = activationEmailService.generateActivationToken();
                    Instant newExpiration = activationEmailService.calculateTokenExpiration();

                    user.setActivationToken(newToken);
                    user.setActivationTokenExpiresAt(newExpiration);

                    String rolesStr = user.getRoles().stream()
                            .map(Role::getLabel)
                            .reduce((a, b) -> a + ", " + b)
                            .orElse("N/A");

                    log.info("Resending activation email to: {}", email);

                    return userRepository.save(user)
                            .flatMap(
                                    savedUser -> activationEmailService.sendActivationEmail(email, newToken, rolesStr));
                });
    }
}
