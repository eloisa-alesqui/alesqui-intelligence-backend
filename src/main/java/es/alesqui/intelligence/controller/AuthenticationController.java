package es.alesqui.intelligence.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import es.alesqui.intelligence.dto.security.AuthRequest;
import es.alesqui.intelligence.dto.security.AuthResponse;
import es.alesqui.intelligence.model.core.User;
import es.alesqui.intelligence.security.JwtService;
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
}
