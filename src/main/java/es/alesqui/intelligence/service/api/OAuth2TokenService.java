package es.alesqui.intelligence.service.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import es.alesqui.intelligence.model.api_spec.unified.ApiConfiguration;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the lifecycle of OAuth 2.0 access tokens for the Client Credentials flow.
 * This service handles fetching, caching, and renewing tokens automatically.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2TokenService {

    private final ApiConfigurationService apiConfigurationService;
    private final WebClient.Builder webClientBuilder;

    // In-memory, thread-safe cache for storing active tokens.
    // Key: apiName, Value: The cached token object.
    private final Map<String, CachedToken> tokenCache = new ConcurrentHashMap<>();

    /**
     * Retrieves a valid access token for a given API.
     * It first checks the local cache for a non-expired token. If not found,
     * it requests a new one from the authorization server.
     *
     * @param apiName The unique name of the API requiring the token.
     * @return A valid access token, or null if it could not be obtained.
     */
    public String getAccessToken(String apiName) {
        // Use compute to ensure atomic operations on the cache, making it thread-safe.
        CachedToken token = tokenCache.compute(apiName, (key, existingToken) -> {
            if (existingToken != null && !existingToken.isExpired()) {
                log.debug("Returning cached token for API '{}'", apiName);
                return existingToken;
            }
            log.info("No valid token in cache for API '{}'. Fetching a new one.", apiName);
            return fetchAndCacheNewToken(apiName);
        });

        return (token != null) ? token.getAccessToken() : null;
    }

    /**
     * Fetches a new token from the authorization server and caches it.
     *
     * @param apiName The API to fetch the token for.
     * @return A new CachedToken object, or null on failure.
     */
    private CachedToken fetchAndCacheNewToken(String apiName) {
        ApiConfiguration.OAuth2ClientCredentialsConfig oauthConfig = getOAuthConfig(apiName);
        if (oauthConfig == null || oauthConfig.getTokenUrl() == null) {
            log.error("OAuth 2.0 configuration is missing or invalid for API '{}'", apiName);
            return null;
        }

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "client_credentials");
        formData.add("client_id", oauthConfig.getClientId());
        formData.add("client_secret", oauthConfig.getClientSecret());
        if (oauthConfig.getScopes() != null && !oauthConfig.getScopes().isEmpty()) {
            formData.add("scope", oauthConfig.getScopes());
        }

        try {
            WebClient webClient = webClientBuilder.baseUrl(oauthConfig.getTokenUrl()).build();
            OAuth2TokenResponse tokenResponse = webClient.post()
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body(BodyInserters.fromFormData(formData))
                    .retrieve()
                    .onStatus(
                        status -> status.is4xxClientError() || status.is5xxServerError(),
                        response -> response.bodyToMono(String.class)
                            .flatMap(errorBody -> {
                                log.error("Error response from token endpoint for '{}': {} - {}", apiName, response.statusCode(), errorBody);
                                return Mono.error(new RuntimeException("Failed to fetch OAuth token: " + response.statusCode()));
                            })
                    )
                    .bodyToMono(OAuth2TokenResponse.class)
                    .block(Duration.ofSeconds(10)); // Block to wait for the response synchronously

            if (tokenResponse != null && tokenResponse.getAccessToken() != null) {
                log.info("Successfully fetched new token for API '{}'", apiName);
                return new CachedToken(tokenResponse.getAccessToken(), tokenResponse.getExpiresIn());
            }

        } catch (Exception e) {
            log.error("Exception while fetching OAuth token for API '{}': {}", apiName, e.getMessage());
        }

        return null;
    }

    private ApiConfiguration.OAuth2ClientCredentialsConfig getOAuthConfig(String apiName) {
        return apiConfigurationService.getConfiguration(apiName)
                .getAuth()
                .getOauth2ClientCredentials();
    }

    /**
     * A simple DTO to map the JSON response from the OAuth 2.0 token endpoint.
     */
    @Data
    private static class OAuth2TokenResponse {
        @JsonProperty("access_token")
        private String accessToken;

        @JsonProperty("expires_in")
        private long expiresIn; // The lifetime in seconds of the access token

        @JsonProperty("token_type")
        private String tokenType;

        @JsonProperty("scope")
        private String scope;
    }

    /**
     * A wrapper class to hold the access token and its expiration time.
     */
    private static class CachedToken {
        private final String accessToken;
        private final Instant expiryTime;

        public CachedToken(String accessToken, long expiresInSeconds) {
            this.accessToken = accessToken;
            // Subtract a 60-second buffer to be safe and renew the token preemptively
            this.expiryTime = Instant.now().plusSeconds(expiresInSeconds - 60);
        }

        public String getAccessToken() {
            return accessToken;
        }

        public boolean isExpired() {
            return Instant.now().isAfter(expiryTime);
        }
    }
}
