package es.alesqui.intelligence.service.security.oauth2;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import es.alesqui.intelligence.config.DeploymentConfig;
import es.alesqui.intelligence.config.GoogleOAuth2Properties;
import es.alesqui.intelligence.config.TrialConfigurationProperties;
import es.alesqui.intelligence.dto.security.oauth2.GoogleLinkConfirmRequest;
import es.alesqui.intelligence.dto.security.oauth2.GoogleLoginRequest;
import es.alesqui.intelligence.dto.security.oauth2.GoogleTokenInfo;
import es.alesqui.intelligence.dto.security.oauth2.OAuth2AuthResponse;
import es.alesqui.intelligence.model.audit.AuditAction;
import es.alesqui.intelligence.model.audit.EntityType;
import es.alesqui.intelligence.model.core.User;
import es.alesqui.intelligence.model.core.enums.AuthProvider;
import es.alesqui.intelligence.model.core.enums.Role;
import es.alesqui.intelligence.repository.UserRepository;
import es.alesqui.intelligence.security.JwtService;
import es.alesqui.intelligence.service.access.TrialWorkspaceService;
import es.alesqui.intelligence.service.audit.AuditService;
import es.alesqui.intelligence.service.security.RateLimitingService;
import static es.alesqui.intelligence.util.IpAddressExtractor.extract;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Orchestrates the Google OAuth2 login and account-linking flows.
 *
 * This service is the single entry point for everything that happens after
 * GoogleTokenVerificationService confirms a Google id_token is genuine.
 * It decides what to do with the verified identity based on the current
 * state of the user database:
 *
 *   - Existing Google account (matched by providerId): log the user in and
 *     return JWT tokens immediately.
 *
 *   - No account yet (TRIAL deployment): create a new TRIAL user account,
 *     set up the trial workspace, and return JWT tokens.
 *
 *   - No account yet (CORPORATE deployment): reject the request with 403.
 *     User creation in CORPORATE mode requires an administrator invitation.
 *
 *   - Existing LOCAL account with the same email: do not issue tokens yet.
 *     Return a linkRequired response containing a short-lived linkChallenge
 *     so the frontend can ask the user for explicit confirmation before
 *     merging the two identities.
 *
 * All public endpoints are rate-limited per IP address before any token
 * validation or database access occurs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OAuth2AuthService {

    private final GoogleTokenVerificationService tokenVerificationService;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final RateLimitingService rateLimitingService;
    private final TrialWorkspaceService trialWorkspaceService;
    private final DeploymentConfig deploymentConfig;
    private final TrialConfigurationProperties trialConfig;
    private final GoogleOAuth2Properties googleProps;

    /**
     * Entry point for the Google Sign-In flow.
     *
     * Enforces rate limiting, delegates token validation to
     * GoogleTokenVerificationService, then routes to the appropriate
     * outcome depending on whether the user already exists in the database.
     *
     * @param request     contains the Google id_token sent by the frontend
     * @param httpRequest the incoming HTTP request, used for IP extraction and audit logging
     * @return a Mono emitting an OAuth2AuthResponse with JWT tokens on success,
     *         or with linkRequired=true if a LOCAL account conflict is detected,
     *         or an error Mono with an appropriate HTTP status on failure
     */
    public Mono<OAuth2AuthResponse> loginWithGoogle(GoogleLoginRequest request, ServerHttpRequest httpRequest) {
        if (!googleProps.isEnabled()) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Google OAuth2 login is not enabled"));
        }

        String ipAddress = extract(httpRequest);

        return rateLimitingService.isOAuth2Allowed(ipAddress)
                .flatMap(allowed -> {
                    if (!allowed) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please try again later."));
                    }
                    return rateLimitingService.recordOAuth2Attempt(ipAddress)
                            .then(tokenVerificationService.verify(request.getIdToken()));
                })
                .flatMap(tokenInfo -> resolveUserForLogin(tokenInfo, httpRequest))
                .onErrorResume(ResponseStatusException.class, e -> {
                    log.warn("[GoogleOAuth2] Login failed: {}", e.getReason());
                    return auditService.logFailureWithUser(
                            AuditAction.AUTH_OAUTH2_LOGIN_FAILED,
                            EntityType.USER,
                            null,
                            null,
                            null,
                            e.getReason(),
                            httpRequest)
                        .then(Mono.error(e));
                });
    }

    /**
     * Entry point for the account-linking confirmation flow.
     *
     * Called when the user has seen the conflict modal and explicitly chosen
     * to link their LOCAL account to their Google identity. Re-validates the
     * id_token to confirm the user still controls the Google account, then
     * verifies the linkChallenge to confirm that this backend previously
     * authorized this specific linking operation.
     *
     * @param request     contains the Google id_token and the linkChallenge
     *                    that was issued by loginWithGoogle
     * @param httpRequest the incoming HTTP request, used for IP extraction and audit logging
     * @return a Mono emitting an OAuth2AuthResponse with JWT tokens on success,
     *         or an error Mono with 401 if the linkChallenge is invalid or expired
     */
    public Mono<OAuth2AuthResponse> confirmGoogleLink(GoogleLinkConfirmRequest request, ServerHttpRequest httpRequest) {
        if (!googleProps.isEnabled()) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Google OAuth2 login is not enabled"));
        }

        String ipAddress = extract(httpRequest);

        return rateLimitingService.isOAuth2Allowed(ipAddress)
                .flatMap(allowed -> {
                    if (!allowed) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please try again later."));
                    }
                    return rateLimitingService.recordOAuth2Attempt(ipAddress)
                            .then(tokenVerificationService.verify(request.getIdToken()));
                })
                .flatMap(tokenInfo -> performLink(tokenInfo, request.getLinkChallenge(), httpRequest))
                .onErrorResume(ResponseStatusException.class, e -> {
                    log.warn("[GoogleOAuth2] Link confirmation failed: {}", e.getReason());
                    return auditService.logFailureWithUser(
                            AuditAction.AUTH_OAUTH2_LOGIN_FAILED,
                            EntityType.USER,
                            null,
                            null,
                            null,
                            e.getReason(),
                            httpRequest)
                        .then(Mono.error(e));
                });
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Determines which outcome applies for a verified Google identity and
     * delegates to the appropriate handler.
     *
     * Lookup order:
     *   1. Search by (GOOGLE, sub) — fastest path for returning users.
     *   2. Search by email — detects conflicts with LOCAL accounts and
     *      handles the case of a new user who has never signed in before.
     *
     * @param tokenInfo the verified claims from Google's tokeninfo endpoint
     * @param httpRequest used for audit logging
     * @return a Mono emitting the appropriate OAuth2AuthResponse
     */
    private Mono<OAuth2AuthResponse> resolveUserForLogin(GoogleTokenInfo tokenInfo, ServerHttpRequest httpRequest) {
        String sub = tokenInfo.getSub();
        String email = tokenInfo.getEmail().trim().toLowerCase();

        return userRepository.findByAuthProviderAndProviderId(AuthProvider.GOOGLE, sub)
                .flatMap(user -> {
                    log.info("[GoogleOAuth2] Existing Google user logged in: {}", user.getUsername());
                    return emitTokens(user, false, httpRequest);
                })
                .switchIfEmpty(Mono.defer(() ->
                    userRepository.findByUsername(email)
                            .flatMap(existingUser -> handleExistingEmailUser(existingUser, tokenInfo, httpRequest))
                            .switchIfEmpty(Mono.defer(() -> createNewOAuth2User(tokenInfo, httpRequest)))
                ));
    }

    /**
     * Handles the case where a user account already exists for the email
     * address in the Google token but was not found by providerId.
     *
     * If the existing account has authProvider=GOOGLE (data inconsistency,
     * e.g. the providerId field was somehow lost), the providerId is repaired
     * silently and tokens are issued.
     *
     * If the existing account has authProvider=LOCAL (the normal conflict
     * case), no tokens are issued. Instead a linkChallenge is returned so
     * the frontend can ask the user whether to merge the accounts.
     *
     * @param existingUser the account found in the database by email
     * @param tokenInfo    the verified Google claims
     * @param httpRequest  used for audit logging
     * @return a Mono emitting tokens if the GOOGLE inconsistency case applies,
     *         or a linkRequired response if a LOCAL conflict is detected
     */
    private Mono<OAuth2AuthResponse> handleExistingEmailUser(User existingUser, GoogleTokenInfo tokenInfo,
            ServerHttpRequest httpRequest) {
        String sub = tokenInfo.getSub();
        AuthProvider provider = existingUser.getAuthProvider() != null
                ? existingUser.getAuthProvider()
                : AuthProvider.LOCAL;

        if (provider == AuthProvider.GOOGLE) {
            log.warn("[GoogleOAuth2] Inconsistency: found by email with GOOGLE provider but not by sub for {}",
                    existingUser.getUsername());
            existingUser.setProviderId(sub);
            return userRepository.save(existingUser)
                    .flatMap(u -> emitTokens(u, false, httpRequest));
        }

        log.info("[GoogleOAuth2] LOCAL account exists for {}, returning linkRequired", existingUser.getUsername());
        String challenge = jwtService.generateLinkChallenge(
                existingUser.getUsername(), sub, googleProps.getLinkChallengeTtl());

        return Mono.just(OAuth2AuthResponse.builder()
                .linkRequired(true)
                .linkChallenge(challenge)
                .authProvider(AuthProvider.GOOGLE.name())
                .build());
    }

    /**
     * Creates a brand-new user account for a Google identity that has no
     * existing account in the database.
     *
     * Only allowed in TRIAL deployment mode. In CORPORATE mode the request
     * is rejected with 403 because user provisioning requires an admin invitation.
     *
     * The new account is immediately active (no activation email needed because
     * Google has already verified the email address). A trial workspace group
     * is created automatically, matching the same setup that email-based trial
     * registration produces.
     *
     * @param tokenInfo   the verified Google claims; email and sub are extracted from here
     * @param httpRequest used for workspace creation and audit logging
     * @return a Mono emitting tokens for the newly created user,
     *         or an error Mono with 403 in CORPORATE mode
     */
    private Mono<OAuth2AuthResponse> createNewOAuth2User(GoogleTokenInfo tokenInfo, ServerHttpRequest httpRequest) {
        String email = tokenInfo.getEmail().trim().toLowerCase();
        String sub = tokenInfo.getSub();

        if (deploymentConfig.isCorporate()) {
            log.warn("[GoogleOAuth2] New user via Google blocked in CORPORATE mode: {}", email);
            return Mono.error(new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Your organization requires an invitation. Please contact your administrator."));
        }

        Instant now = Instant.now();
        int trialDurationDays = trialConfig.getDurationDays();

        User newUser = User.builder()
                .username(email)
                .password(null)
                .roles(Set.of(Role.ROLE_TRIAL))
                .isActive(true)
                .authProvider(AuthProvider.GOOGLE)
                .providerId(sub)
                .createdAt(now)
                .trialStartDate(now)
                .trialEndDate(now.plus(Duration.ofDays(trialDurationDays)))
                .build();

        log.info("[GoogleOAuth2] Creating new TRIAL user via Google: {}", email);

        return userRepository.save(newUser)
                .flatMap(savedUser ->
                    Mono.when(
                            trialWorkspaceService.createTrialWorkspace(savedUser, httpRequest),
                            auditService.logActionWithUser(
                                    AuditAction.USER_CREATED_OAUTH2,
                                    EntityType.USER,
                                    savedUser.getId(),
                                    savedUser.getUsername(),
                                    savedUser.getUsername(),
                                    savedUser.getId(),
                                    "User created via Google OAuth2, TRIAL mode",
                                    httpRequest)
                    ).thenReturn(savedUser)
                )
                .flatMap(savedUser -> emitTokens(savedUser, true, httpRequest));
    }

    /**
     * Merges an existing LOCAL account with a Google identity after the user
     * has explicitly confirmed the operation via the frontend modal.
     *
     * The linkChallenge is validated first, before any database write, to
     * prevent unauthorized account takeover. The challenge binds the operation
     * to a specific email and Google sub, so it cannot be replayed against a
     * different account.
     *
     * After a successful link the user can sign in with either their email
     * and password (LOCAL) or with Google. Their existing password is preserved.
     *
     * @param tokenInfo     the verified Google claims; email and sub must match
     *                      what is encoded in the linkChallenge
     * @param linkChallenge the short-lived JWT previously issued by loginWithGoogle
     * @param httpRequest   used for audit logging
     * @return a Mono emitting tokens for the newly linked account,
     *         or an error Mono with 401 if the challenge is invalid or expired
     */
    private Mono<OAuth2AuthResponse> performLink(GoogleTokenInfo tokenInfo, String linkChallenge,
            ServerHttpRequest httpRequest) {
        String sub = tokenInfo.getSub();
        String email = tokenInfo.getEmail().trim().toLowerCase();

        if (!jwtService.isValidLinkChallenge(linkChallenge, email, sub)) {
            log.warn("[GoogleOAuth2] Invalid or expired link challenge for {}", email);
            return Mono.error(new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Invalid or expired link challenge"));
        }

        return userRepository.findByUsername(email)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Account not found")))
                .flatMap(user -> {
                    user.setAuthProvider(AuthProvider.GOOGLE);
                    user.setProviderId(sub);
                    return userRepository.save(user);
                })
                .flatMap(user ->
                    auditService.logActionWithUser(
                            AuditAction.USER_LINKED_GOOGLE,
                            EntityType.USER,
                            user.getId(),
                            user.getUsername(),
                            user.getUsername(),
                            user.getId(),
                            "Account linked to Google OAuth2",
                            httpRequest)
                    .thenReturn(user)
                )
                .flatMap(user -> emitTokens(user, false, httpRequest));
    }

    /**
     * Generates application-level JWT tokens for a user and records the login
     * event in the audit log.
     *
     * This method is the final step in every successful path through this
     * service: returning Google user, newly created user, and linked user all
     * end here.
     *
     * @param user        the user to generate tokens for
     * @param newUser     true if the account was just created in this request
     * @param httpRequest used for audit logging
     * @return a Mono emitting the completed OAuth2AuthResponse with access and
     *         refresh tokens
     */
    private Mono<OAuth2AuthResponse> emitTokens(User user, boolean newUser, ServerHttpRequest httpRequest) {
        String accessToken = jwtService.generateToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        String providerName = user.getAuthProvider() != null
                ? user.getAuthProvider().name()
                : AuthProvider.LOCAL.name();

        return auditService.logActionWithUser(
                AuditAction.AUTH_OAUTH2_LOGIN,
                EntityType.USER,
                user.getId(),
                user.getUsername(),
                user.getUsername(),
                user.getId(),
                "OAuth2 login via " + providerName,
                httpRequest)
        .thenReturn(OAuth2AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .authProvider(providerName)
                .newUser(newUser)
                .linkRequired(false)
                .build());
    }
}
