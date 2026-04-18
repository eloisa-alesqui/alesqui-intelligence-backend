package es.alesqui.intelligence.controller;

import es.alesqui.intelligence.config.TestSecurityConfig;
import es.alesqui.intelligence.dto.security.oauth2.GoogleLinkConfirmRequest;
import es.alesqui.intelligence.dto.security.oauth2.GoogleLoginRequest;
import es.alesqui.intelligence.dto.security.oauth2.OAuth2AuthResponse;
import es.alesqui.intelligence.security.JwtService;
import es.alesqui.intelligence.service.security.oauth2.OAuth2AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@WebFluxTest(OAuth2AuthenticationController.class)
@Import(TestSecurityConfig.class)
class OAuth2AuthenticationControllerTest {

    @Autowired
    WebTestClient webClient;

    @MockitoBean
    OAuth2AuthService oauth2AuthService;

    @MockitoBean
    JwtService jwtService;

    @MockitoBean
    ReactiveUserDetailsService userDetailsService;

    // ──────────────────────────── POST /api/auth/oauth2/google ────────────────────────────

    @Test
    void loginWithGoogle_validRequest_returns200WithTokens() {
        OAuth2AuthResponse response = OAuth2AuthResponse.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .authProvider("GOOGLE")
                .newUser(false)
                .linkRequired(false)
                .build();

        given(oauth2AuthService.loginWithGoogle(any(), any())).willReturn(Mono.just(response));

        webClient.post().uri("/api/auth/oauth2/google")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(GoogleLoginRequest.builder().idToken("valid-id-token").build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(OAuth2AuthResponse.class)
                .value(r -> {
                    assertThat(r.getAccessToken()).isEqualTo("access-token");
                    assertThat(r.getRefreshToken()).isEqualTo("refresh-token");
                    assertThat(r.isLinkRequired()).isFalse();
                });
    }

    @Test
    void loginWithGoogle_blankIdToken_returns400() {
        webClient.post().uri("/api/auth/oauth2/google")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(GoogleLoginRequest.builder().idToken("").build())
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void loginWithGoogle_linkRequired_returns200WithChallenge() {
        OAuth2AuthResponse response = OAuth2AuthResponse.builder()
                .linkRequired(true)
                .linkChallenge("link-challenge-jwt")
                .authProvider("GOOGLE")
                .build();

        given(oauth2AuthService.loginWithGoogle(any(), any())).willReturn(Mono.just(response));

        webClient.post().uri("/api/auth/oauth2/google")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(GoogleLoginRequest.builder().idToken("valid-id-token").build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(OAuth2AuthResponse.class)
                .value(r -> {
                    assertThat(r.isLinkRequired()).isTrue();
                    assertThat(r.getLinkChallenge()).isNotNull();
                    assertThat(r.getAccessToken()).isNull();
                });
    }

    @Test
    void loginWithGoogle_serviceThrows401_returns401() {
        given(oauth2AuthService.loginWithGoogle(any(), any()))
                .willReturn(Mono.error(new ResponseStatusException(UNAUTHORIZED, "Invalid Google id_token")));

        webClient.post().uri("/api/auth/oauth2/google")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(GoogleLoginRequest.builder().idToken("bad-token").build())
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void loginWithGoogle_serviceThrows429_returns429() {
        given(oauth2AuthService.loginWithGoogle(any(), any()))
                .willReturn(Mono.error(new ResponseStatusException(TOO_MANY_REQUESTS, "Too many requests")));

        webClient.post().uri("/api/auth/oauth2/google")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(GoogleLoginRequest.builder().idToken("any-token").build())
                .exchange()
                .expectStatus().isEqualTo(TOO_MANY_REQUESTS);
    }

    @Test
    void loginWithGoogle_serviceThrows403_returns403() {
        given(oauth2AuthService.loginWithGoogle(any(), any()))
                .willReturn(Mono.error(new ResponseStatusException(FORBIDDEN, "Corporate mode: invitation required")));

        webClient.post().uri("/api/auth/oauth2/google")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(GoogleLoginRequest.builder().idToken("any-token").build())
                .exchange()
                .expectStatus().isForbidden();
    }

    // ──────────────────────────── POST /api/auth/oauth2/google/link ────────────────────────────

    @Test
    void confirmLink_validRequest_returns200WithTokens() {
        OAuth2AuthResponse response = OAuth2AuthResponse.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .authProvider("GOOGLE")
                .newUser(false)
                .linkRequired(false)
                .build();

        given(oauth2AuthService.confirmGoogleLink(any(), any())).willReturn(Mono.just(response));

        webClient.post().uri("/api/auth/oauth2/google/link")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(GoogleLinkConfirmRequest.builder()
                        .idToken("valid-id-token")
                        .linkChallenge("valid-challenge")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(OAuth2AuthResponse.class)
                .value(r -> assertThat(r.getAccessToken()).isEqualTo("access-token"));
    }

    @Test
    void confirmLink_blankIdToken_returns400() {
        webClient.post().uri("/api/auth/oauth2/google/link")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(GoogleLinkConfirmRequest.builder()
                        .idToken("")
                        .linkChallenge("valid-challenge")
                        .build())
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void confirmLink_blankLinkChallenge_returns400() {
        webClient.post().uri("/api/auth/oauth2/google/link")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(GoogleLinkConfirmRequest.builder()
                        .idToken("valid-id-token")
                        .linkChallenge("")
                        .build())
                .exchange()
                .expectStatus().isBadRequest();
    }
}
