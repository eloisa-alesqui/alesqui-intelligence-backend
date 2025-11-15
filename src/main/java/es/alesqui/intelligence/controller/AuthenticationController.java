package es.alesqui.intelligence.controller;

import org.springframework.http.HttpStatus;
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
import es.alesqui.intelligence.dto.security.ResendActivationRequest;
import es.alesqui.intelligence.dto.security.ValidateTokenRequest;
import es.alesqui.intelligence.dto.security.ValidateTokenResponse;
import es.alesqui.intelligence.model.core.User;
import es.alesqui.intelligence.model.core.enums.Role;
import es.alesqui.intelligence.security.JwtService;
import es.alesqui.intelligence.service.access.AccessAdminService;
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
    private final AccessAdminService accessAdminService;

    /**
     * Handles the user login request.
     *
     * This endpoint authenticates a user based on their username and password.
     * If the credentials are valid, it generates and returns a JWT access token
     * and a refresh token.
     *
     * @param authRequest A Mono containing the authentication request with user credentials.
     * @return A Mono containing the authentication response with JWTs.
     * Returns a Mono.error with 401 Unauthorized if authentication fails.
     */
    @PostMapping("/login")
    public Mono<AuthResponse> login(@Valid @RequestBody Mono<AuthRequest> authRequest) {
        return authRequest
                .flatMap(request -> {
                    log.debug("[Auth] Attempting login for '{}'", request.getUsername());
                    Authentication authenticationToken = new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    );
                    // The authenticationManager will use our ReactiveUserDetailsService and PasswordEncoder
                    // to validate the credentials.
                    return authenticationManager.authenticate(authenticationToken);
                })
                .doOnSuccess(auth -> log.debug("[Auth] Authentication success for '{}')", auth.getName()))
                .flatMap(authentication -> {
                    // The principal is the UserDetails object we loaded.
                    User user = (User) authentication.getPrincipal();
                    String accessToken = jwtService.generateToken(user);
                    String refreshToken = jwtService.generateRefreshToken(user);

                    return Mono.just(AuthResponse.builder()
                            .accessToken(accessToken)
                            .refreshToken(refreshToken)
                            .build());
                })
                .onErrorResume(e -> {
                    log.debug("[Auth] Authentication failed: {} - {}", e.getClass().getSimpleName(), e.getMessage());
                    return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
                });
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
        
        return accessAdminService.validateActivationToken(request.getToken())
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
     * Validates an activation token via GET request (for direct URL access).
     * 
     * @param token the activation token from query parameter
     * @return validation response with user details if valid
     */
    @GetMapping("/validate-token")
    public Mono<ValidateTokenResponse> validateTokenGet(@RequestParam String token) {
        log.debug("[Auth] Validating activation token via GET");
        
        return accessAdminService.validateActivationToken(token)
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
     * @return activation response
     */
    @PostMapping("/activate-account")
    public Mono<ActivateAccountResponse> activateAccount(@Valid @RequestBody ActivateAccountRequest request) {
        log.debug("[Auth] Activating account");
        
        return accessAdminService.activateAccount(request.getToken(), request.getPassword())
            .map(user -> ActivateAccountResponse.builder()
                .success(true)
                .message("Account activated successfully. You can now log in.")
                .email(user.getUsername())
                .build())
            .onErrorResume(e -> {
                log.warn("[Auth] Account activation failed: {}", e.getMessage());
                return Mono.just(ActivateAccountResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .build());
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
        
        return accessAdminService.resendActivationEmail(request.getEmail())
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
}
