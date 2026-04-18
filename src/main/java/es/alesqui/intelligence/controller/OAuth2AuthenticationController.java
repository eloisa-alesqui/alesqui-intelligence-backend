package es.alesqui.intelligence.controller;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import es.alesqui.intelligence.dto.security.oauth2.GoogleLinkConfirmRequest;
import es.alesqui.intelligence.dto.security.oauth2.GoogleLoginRequest;
import es.alesqui.intelligence.dto.security.oauth2.OAuth2AuthResponse;
import es.alesqui.intelligence.service.security.oauth2.OAuth2AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth/oauth2")
@RequiredArgsConstructor
@Slf4j
public class OAuth2AuthenticationController {

    private final OAuth2AuthService oauth2AuthService;

    /**
     * Google Sign-In endpoint. Accepts a Google id_token, validates it,
     * and returns JWT access/refresh tokens.
     *
     * If a LOCAL account already exists with the same email, returns
     * {@code linkRequired=true} with a signed {@code linkChallenge} instead of tokens.
     */
    @PostMapping("/google")
    public Mono<OAuth2AuthResponse> loginWithGoogle(
            @Valid @RequestBody GoogleLoginRequest request,
            ServerHttpRequest httpRequest) {
        return oauth2AuthService.loginWithGoogle(request, httpRequest);
    }

    /**
     * Account linking confirmation endpoint. Links an existing LOCAL account
     * to a Google identity using a valid {@code linkChallenge} token previously
     * issued by {@link #loginWithGoogle}.
     */
    @PostMapping("/google/link")
    public Mono<OAuth2AuthResponse> confirmLink(
            @Valid @RequestBody GoogleLinkConfirmRequest request,
            ServerHttpRequest httpRequest) {
        return oauth2AuthService.confirmGoogleLink(request, httpRequest);
    }
}
