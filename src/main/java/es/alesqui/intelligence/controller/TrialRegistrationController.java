package es.alesqui.intelligence.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import es.alesqui.intelligence.config.DeploymentConfig;
import es.alesqui.intelligence.dto.trial.TrialRegistrationRequest;
import es.alesqui.intelligence.dto.trial.TrialRegistrationResponse;
import es.alesqui.intelligence.service.trial.TrialRegistrationService;
import es.alesqui.intelligence.util.IpAddressExtractor;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Public REST controller for trial user registration.
 * 
 * Purpose:
 * - Provides public endpoint for landing page trial registration
 * - No authentication required (permitAll in SecurityConfig)
 * - Rate limited by IP address to prevent abuse
 * - Logs all registration attempts for security auditing
 * 
 * Endpoints:
 * - POST /api/public/trial-registration - Register for trial account
 * 
 * Security Considerations:
 * - Public endpoint - accessible without authentication
 * - Rate limiting enforced at service level (1 per IP per 24h)
 * - Input validation via Jakarta Validation
 * - All attempts logged with IP address for audit
 * - Consider adding reCAPTCHA in future for additional bot protection
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@Slf4j
public class TrialRegistrationController {
    
    private final TrialRegistrationService trialRegistrationService;
    private final DeploymentConfig deploymentConfig;
    
    /**
     * Registers a new trial user account.
     * 
     * Process:
     * 1. Validates email format (Jakarta Validation)
     * 2. Extracts client IP address
     * 3. Checks rate limit (1 per IP per 24h)
     * 4. Validates email uniqueness
     * 5. Creates user with ROLE_TRIAL
     * 6. Sets trial dates (14 days)
     * 7. Sends activation email
     * 8. Returns success response
     * 
     * Success Response (201 Created):
     * {
     *   "message": "Trial account created successfully. Please check your email to activate your account.",
     *   "email": "user@example.com",
     *   "trialEndDate": "2026-01-17T10:30:00Z",
     *   "trialDurationDays": 14
     * }
     * 
     * Error Responses:
     * - 400 Bad Request: Invalid email format
     * - 409 Conflict: Email already exists
     * - 429 Too Many Requests: Rate limit exceeded
     * - 500 Internal Server Error: Unexpected error
     * 
     * @param request the registration request containing email
     * @param serverRequest the HTTP request for IP extraction
     * @return Mono containing the registration response
     */
    @PostMapping("/trial-registration")
    public Mono<TrialRegistrationResponse> registerTrial(
            @Valid @RequestBody TrialRegistrationRequest request,
            ServerHttpRequest serverRequest) {
        
        // Validate that trial registration is only available in TRIAL mode
        if (deploymentConfig.isCorporate()) {
            log.warn("[TrialRegistration] Trial registration attempted in CORPORATE mode, rejecting");
            return Mono.error(new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Trial registration is not available in this deployment. Please contact your administrator."
            ));
        }
        
        // Extract client IP address for rate limiting
        String ipAddress = IpAddressExtractor.extract(serverRequest);
        
        log.info("[TrialRegistration] Received trial registration request for email: {} from IP: {}", 
            request.getEmail(), ipAddress);
        
        return trialRegistrationService.registerTrialUser(request, ipAddress, serverRequest)
            .doOnSuccess(response -> 
                log.info("[TrialRegistration] Successfully registered trial user: {}", response.getEmail())
            )
            .doOnError(error -> {
                if (error instanceof ResponseStatusException) {
                    ResponseStatusException rse = (ResponseStatusException) error;
                    log.warn("[TrialRegistration] Registration failed for IP {}: {} - {}", 
                        ipAddress, rse.getStatusCode(), rse.getReason());
                } else {
                    log.error("[TrialRegistration] Unexpected error during registration from IP {}", 
                        ipAddress, error);
                }
            });
    }
    
}
