package es.alesqui.intelligence.service.security.oauth2;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import es.alesqui.intelligence.config.GoogleOAuth2Properties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class GoogleTokenVerificationServiceTest {

    private static WireMockServer wireMock;
    private GoogleTokenVerificationService service;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void setUp() {
        GoogleOAuth2Properties props = new GoogleOAuth2Properties();
        props.setClientId("test-client-id");
        props.setTokeninfoUrl("http://localhost:" + wireMock.port() + "/tokeninfo");
        props.setAllowedIssuers(List.of("accounts.google.com", "https://accounts.google.com"));
        service = new GoogleTokenVerificationService(WebClient.builder(), props);
    }

    @AfterEach
    void resetWireMock() {
        wireMock.resetAll();
    }

    private String buildTokenInfoJson(String aud, String iss, String emailVerified, long exp) {
        return """
                {
                  "iss": "%s",
                  "aud": "%s",
                  "sub": "google-sub-123",
                  "email": "user@example.com",
                  "email_verified": "%s",
                  "name": "Test User",
                  "exp": "%d"
                }
                """.formatted(iss, aud, emailVerified, exp);
    }

    @Nested
    @DisplayName("verify() — success")
    class VerifySuccess {

        @Test
        @DisplayName("valid token with all correct claims returns GoogleTokenInfo")
        void verify_validToken_returnsTokenInfo() {
            long futureExp = Instant.now().plusSeconds(3600).getEpochSecond();
            wireMock.stubFor(get(urlPathEqualTo("/tokeninfo"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(buildTokenInfoJson("test-client-id", "accounts.google.com", "true", futureExp))));

            StepVerifier.create(service.verify("valid-token"))
                    .assertNext(info -> {
                        assertThat(info.getSub()).isEqualTo("google-sub-123");
                        assertThat(info.getEmail()).isEqualTo("user@example.com");
                        assertThat(info.getEmailVerified()).isEqualTo("true");
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("verify() — claim validation failures")
    class VerifyClaimFailures {

        @Test
        @DisplayName("wrong audience returns 401")
        void verify_wrongAudience_returns401() {
            long futureExp = Instant.now().plusSeconds(3600).getEpochSecond();
            wireMock.stubFor(get(urlPathEqualTo("/tokeninfo"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(buildTokenInfoJson("other-client-id", "accounts.google.com", "true", futureExp))));

            StepVerifier.create(service.verify("wrong-aud-token"))
                    .expectErrorMatches(e -> e instanceof ResponseStatusException rse
                            && rse.getStatusCode().value() == 401)
                    .verify();
        }

        @Test
        @DisplayName("invalid issuer returns 401")
        void verify_invalidIssuer_returns401() {
            long futureExp = Instant.now().plusSeconds(3600).getEpochSecond();
            wireMock.stubFor(get(urlPathEqualTo("/tokeninfo"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(buildTokenInfoJson("test-client-id", "evil.attacker.com", "true", futureExp))));

            StepVerifier.create(service.verify("wrong-iss-token"))
                    .expectErrorMatches(e -> e instanceof ResponseStatusException rse
                            && rse.getStatusCode().value() == 401)
                    .verify();
        }

        @Test
        @DisplayName("email not verified returns 401")
        void verify_emailNotVerified_returns401() {
            long futureExp = Instant.now().plusSeconds(3600).getEpochSecond();
            wireMock.stubFor(get(urlPathEqualTo("/tokeninfo"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(buildTokenInfoJson("test-client-id", "accounts.google.com", "false", futureExp))));

            StepVerifier.create(service.verify("unverified-email-token"))
                    .expectErrorMatches(e -> e instanceof ResponseStatusException rse
                            && rse.getStatusCode().value() == 401)
                    .verify();
        }

        @Test
        @DisplayName("expired token returns 401")
        void verify_expiredToken_returns401() {
            long pastExp = Instant.now().minusSeconds(1000).getEpochSecond();
            wireMock.stubFor(get(urlPathEqualTo("/tokeninfo"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(buildTokenInfoJson("test-client-id", "accounts.google.com", "true", pastExp))));

            StepVerifier.create(service.verify("expired-token"))
                    .expectErrorMatches(e -> e instanceof ResponseStatusException rse
                            && rse.getStatusCode().value() == 401)
                    .verify();
        }
    }

    @Nested
    @DisplayName("verify() — HTTP errors")
    class VerifyHttpErrors {

        @Test
        @DisplayName("Google rejects token with 400 → returns 401")
        void verify_googleRejects_returns401() {
            wireMock.stubFor(get(urlPathEqualTo("/tokeninfo"))
                    .willReturn(aResponse()
                            .withStatus(400)
                            .withBody("{\"error\":\"invalid_token\"}")));

            StepVerifier.create(service.verify("bad-token"))
                    .expectErrorMatches(e -> e instanceof ResponseStatusException rse
                            && rse.getStatusCode().value() == 401)
                    .verify();
        }

        @Test
        @DisplayName("Google unavailable (connection fault) returns 503")
        void verify_googleUnavailable_returns503() {
            wireMock.stubFor(get(urlPathEqualTo("/tokeninfo"))
                    .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

            StepVerifier.create(service.verify("any-token"))
                    .expectErrorMatches(e -> e instanceof ResponseStatusException rse
                            && rse.getStatusCode().value() == 503)
                    .verify();
        }
    }
}
