package es.alesqui.intelligence.controller;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import es.alesqui.intelligence.dto.security.oauth2.GoogleLinkConfirmRequest;
import es.alesqui.intelligence.dto.security.oauth2.GoogleLoginRequest;
import es.alesqui.intelligence.dto.security.oauth2.OAuth2AuthResponse;
import es.alesqui.intelligence.model.core.User;
import es.alesqui.intelligence.model.core.enums.AuthProvider;
import es.alesqui.intelligence.model.core.enums.Role;
import es.alesqui.intelligence.repository.UserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;
import java.util.Base64;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class OAuth2AuthenticationControllerIT {

    private static WireMockServer wireMock;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("app.oauth2.google.tokeninfo-url",
                () -> "http://localhost:" + wireMock.port() + "/tokeninfo");
        registry.add("app.jwt.secret",
                () -> Base64.getEncoder().encodeToString("this-is-a-test-secret-key-32byte".getBytes()));
        registry.add("app.deployment.mode", () -> "TRIAL");
        // Stub SMTP so MailSenderAutoConfiguration can resolve placeholders
        registry.add("SMTP_HOST", () -> "localhost");
        registry.add("SMTP_PORT", () -> "25");
        registry.add("SMTP_USER", () -> "test");
        registry.add("SMTP_PASSWORD", () -> "test");
        // Stub OpenAI key so spring-ai auto-config can resolve the placeholder
        registry.add("OPENAI_API_KEY", () -> "test-api-key");
    }

    @Autowired
    WebTestClient webClient;

    @Autowired
    UserRepository userRepository;

    @BeforeEach
    void cleanUp() {
        userRepository.deleteAll().block();
        wireMock.resetAll();
    }

    private void stubValidTokenInfo(String sub, String email) {
        long futureExp = Instant.now().plusSeconds(3600).getEpochSecond();
        String json = """
                {
                  "iss": "accounts.google.com",
                  "aud": "test-client-id",
                  "sub": "%s",
                  "email": "%s",
                  "email_verified": "true",
                  "name": "Test User",
                  "exp": "%d"
                }
                """.formatted(sub, email, futureExp);

        wireMock.stubFor(get(urlPathEqualTo("/tokeninfo"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(json)));
    }

    @Test
    @DisplayName("new trial user via Google → 200 with tokens, user saved to Mongo with authProvider=GOOGLE")
    void loginWithGoogle_newTrialUser_returns200WithTokens() {
        stubValidTokenInfo("google-sub-123", "user@example.com");

        webClient.post().uri("/api/auth/oauth2/google")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(GoogleLoginRequest.builder().idToken("valid-token").build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(OAuth2AuthResponse.class)
                .value(response -> {
                    assertThat(response.getAccessToken()).isNotNull();
                    assertThat(response.getRefreshToken()).isNotNull();
                    assertThat(response.isNewUser()).isTrue();
                    assertThat(response.isLinkRequired()).isFalse();
                });

        User savedUser = userRepository.findByUsername("user@example.com").block();
        assertThat(savedUser).isNotNull();
        assertThat(savedUser.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(savedUser.getProviderId()).isEqualTo("google-sub-123");
    }

    @Test
    @DisplayName("invalid id_token (Google returns 400) → 401")
    void loginWithGoogle_invalidIdToken_returns401() {
        wireMock.stubFor(get(urlPathEqualTo("/tokeninfo"))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withBody("{\"error\":\"invalid_token\"}")));

        webClient.post().uri("/api/auth/oauth2/google")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(GoogleLoginRequest.builder().idToken("bad-token").build())
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("existing LOCAL account → 200 with linkRequired=true, linkChallenge, no accessToken")
    void loginWithGoogle_existingLocalAccount_returnsLinkRequired() {
        User localUser = User.builder()
                .username("user@example.com")
                .password("$2a$10$hashedpassword")
                .authProvider(AuthProvider.LOCAL)
                .roles(Set.of(Role.ROLE_IT))
                .isActive(true)
                .createdAt(Instant.now())
                .build();
        userRepository.save(localUser).block();

        stubValidTokenInfo("google-sub-123", "user@example.com");

        webClient.post().uri("/api/auth/oauth2/google")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(GoogleLoginRequest.builder().idToken("valid-token").build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(OAuth2AuthResponse.class)
                .value(response -> {
                    assertThat(response.isLinkRequired()).isTrue();
                    assertThat(response.getLinkChallenge()).isNotNull();
                    assertThat(response.getAccessToken()).isNull();
                    assertThat(response.getRefreshToken()).isNull();
                });
    }

    @Test
    @DisplayName("confirm link full flow → 200 with tokens; user has authProvider=GOOGLE, password preserved")
    void confirmLink_fullFlow_linksAccountAndReturnsTokens() {
        // Step 1: insert a LOCAL user
        User localUser = User.builder()
                .username("user@example.com")
                .password("$2a$10$hashedpassword")
                .authProvider(AuthProvider.LOCAL)
                .roles(Set.of(Role.ROLE_IT))
                .isActive(true)
                .createdAt(Instant.now())
                .build();
        userRepository.save(localUser).block();

        stubValidTokenInfo("google-sub-123", "user@example.com");

        // Step 2: first POST to /google → get linkChallenge
        OAuth2AuthResponse linkResponse = webClient.post().uri("/api/auth/oauth2/google")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(GoogleLoginRequest.builder().idToken("valid-token").build())
                .exchange()
                .expectStatus().isOk()
                .returnResult(OAuth2AuthResponse.class)
                .getResponseBody()
                .blockFirst();

        assertThat(linkResponse).isNotNull();
        assertThat(linkResponse.isLinkRequired()).isTrue();
        String linkChallenge = linkResponse.getLinkChallenge();
        assertThat(linkChallenge).isNotNull();

        // Step 3: second POST to /google/link with the challenge
        wireMock.resetAll();
        stubValidTokenInfo("google-sub-123", "user@example.com");

        webClient.post().uri("/api/auth/oauth2/google/link")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(GoogleLinkConfirmRequest.builder()
                        .idToken("valid-token")
                        .linkChallenge(linkChallenge)
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(OAuth2AuthResponse.class)
                .value(response -> {
                    assertThat(response.getAccessToken()).isNotNull();
                    assertThat(response.getRefreshToken()).isNotNull();
                    assertThat(response.isLinkRequired()).isFalse();
                });

        // Verify user in Mongo has GOOGLE provider and original password preserved
        User updatedUser = userRepository.findByUsername("user@example.com").block();
        assertThat(updatedUser).isNotNull();
        assertThat(updatedUser.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(updatedUser.getProviderId()).isEqualTo("google-sub-123");
        assertThat(updatedUser.getPassword()).isEqualTo("$2a$10$hashedpassword");
    }
}
