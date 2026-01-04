package es.alesqui.intelligence.service.trial;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import es.alesqui.intelligence.config.TrialConfigurationProperties;
import es.alesqui.intelligence.dto.admin.CreateUserRequest;
import es.alesqui.intelligence.dto.trial.TrialRegistrationRequest;
import es.alesqui.intelligence.dto.trial.TrialRegistrationResponse;
import es.alesqui.intelligence.model.core.User;
import es.alesqui.intelligence.model.core.enums.Role;
import es.alesqui.intelligence.repository.UserRepository;
import es.alesqui.intelligence.service.access.UserManagementService;
import es.alesqui.intelligence.service.security.RateLimitingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Service for handling trial user registration from public landing page.
 * 
 * Purpose:
 * - Orchestrates trial account creation flow
 * - Enforces rate limiting and validation rules
 * - Automatically sets trial dates and ROLE_TRIAL
 * - Triggers activation email and workspace creation
 * 
 * Business Rules:
 * - Trial duration: Configurable via trial.duration.days (default: 14 days)
 * - Rate limiting: Configurable via trial.rate-limit.* properties
 * - Email must be unique
 * - Activation email sent automatically
 * - Trial workspace created upon activation
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TrialRegistrationService {
    
    private final UserManagementService userManagementService;
    private final UserRepository userRepository;
    private final RateLimitingService rateLimitingService;
    private final TrialConfigurationProperties trialConfig;
    
    /**
     * Registers a new trial user.
     * 
     * Flow:
     * 1. Validate IP rate limit
     * 2. Check email uniqueness
     * 3. Calculate trial dates
     * 4. Create user with ROLE_TRIAL (activation email sent automatically)
     * 5. Record IP attempt
     * 6. Return success response
     * 
     * @param request the registration request containing email
     * @param ipAddress the client IP address for rate limiting
     * @return Mono containing the registration response
     * @throws ResponseStatusException 429 if rate limited, 409 if email exists
     */
    public Mono<TrialRegistrationResponse> registerTrialUser(
            TrialRegistrationRequest request, 
            String ipAddress) {
        
        String email = request.getEmail().trim().toLowerCase();
        
        log.info("[TrialRegistration] Processing trial registration for email: {} from IP: {}", 
            email, ipAddress);
        
        // Step 1: Check rate limit
        return rateLimitingService.isAllowed(ipAddress)
            .flatMap(allowed -> {
                if (!allowed) {
                    log.warn("[TrialRegistration] Rate limit exceeded for IP: {}", ipAddress);
                    return Mono.error(new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Too many trial registration attempts. Please try again later."
                    ));
                }
                
                // Step 2: Check if email already exists
                return userRepository.findByUsername(email)
                    .flatMap(existingUser -> {
                        log.warn("[TrialRegistration] Email already exists: {}", email);
                        return Mono.<TrialRegistrationResponse>error(
                            new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "An account with this email already exists."
                            )
                        );
                    })
                    .switchIfEmpty(Mono.defer(() -> createTrialUser(email, ipAddress)));
            });
    }
    
    /**
     * Creates the trial user with proper configuration.
     */
    private Mono<TrialRegistrationResponse> createTrialUser(String email, String ipAddress) {
        // Step 3: Calculate trial dates using configured duration
        Instant now = Instant.now();
        int trialDurationDays = trialConfig.getDurationDays();
        Instant trialEnd = now.plus(Duration.ofDays(trialDurationDays));
        
        log.debug("[TrialRegistration] Creating trial with duration: {} days (expires: {})", 
            trialDurationDays, trialEnd);
        
        // Step 4: Create user request
        CreateUserRequest createUserRequest = new CreateUserRequest();
        createUserRequest.setUsername(email);
        createUserRequest.setPassword(null); // Triggers activation email flow
        createUserRequest.setRoles(Set.of(Role.ROLE_TRIAL));
        
        // Create user (this will send activation email and set up for workspace creation)
        return userManagementService.createUser(createUserRequest)
            .flatMap(user -> {
                // Update user with trial dates
                user.setTrialStartDate(now);
                user.setTrialEndDate(trialEnd);
                
                return userRepository.save(user);
            })
            .flatMap(savedUser -> {
                // Step 5: Record IP attempt (after successful creation)
                return rateLimitingService.recordAttempt(ipAddress)
                    .thenReturn(savedUser);
            })
            .map(savedUser -> {
                // Step 6: Build response
                log.info("[TrialRegistration] Successfully created trial user: {} (expires: {})", 
                    email, trialEnd);
                
                return TrialRegistrationResponse.builder()
                    .message("Trial account created successfully. Please check your email to activate your account.")
                    .email(email)
                    .trialEndDate(trialEnd)
                    .trialDurationDays(trialConfig.getDurationDays())
                    .build();
            })
            .doOnError(error -> {
                if (!(error instanceof ResponseStatusException)) {
                    log.error("[TrialRegistration] Failed to create trial user: {}", email, error);
                }
            });
    }
    
    /**
     * Checks if a user's trial has expired.
     * 
     * @param user the user to check
     * @return true if trial has expired, false otherwise
     */
    public boolean isTrialExpired(User user) {
        if (user.getTrialEndDate() == null) {
            return false;
        }
        
        return Instant.now().isAfter(user.getTrialEndDate());
    }
    
    /**
     * Gets the number of days remaining in a trial.
     * 
     * @param user the trial user
     * @return days remaining, or 0 if expired/no trial
     */
    public long getDaysRemaining(User user) {
        if (user.getTrialEndDate() == null) {
            return 0;
        }
        
        Instant now = Instant.now();
        if (now.isAfter(user.getTrialEndDate())) {
            return 0;
        }
        
        Duration remaining = Duration.between(now, user.getTrialEndDate());
        return remaining.toDays();
    }
    
    /**
     * Lists all trial users.
     * 
     * @return Flux of all users with ROLE_TRIAL
     */
    public reactor.core.publisher.Flux<User> listAllTrialUsers() {
        return userRepository.findAll()
            .filter(user -> user.getRoles().contains(Role.ROLE_TRIAL));
    }
}
