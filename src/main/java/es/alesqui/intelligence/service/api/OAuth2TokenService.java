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
 * Manages the lifecycle of OAuth 2.0 access tokens for various grant types.
 * This service handles fetching, caching, and renewing tokens automatically based on API configuration.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2TokenService {

    private final ApiConfigurationService apiConfigurationService;
    private final WebClient.Builder webClientBuilder;

    private final Map<String, CachedToken> tokenCache = new ConcurrentHashMap<>();

    /**
     * Retrieves a valid access token for a given API.
     * It first checks the local cache for a non-expired token. If not found or expired,
     * it requests a new one from the authorization server.
     *
     * @param apiName The unique name of the API requiring the token.
     * @return A valid access token, or null if it could not be obtained.
     */
    public String getAccessToken(String apiName) {
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
     * Fetches a new token from the authorization server based on the API's configuration
     * and caches it upon success.
     *
     * @param apiName The API to fetch the token for.
     * @return A new CachedToken object, or null on failure.
     */
    private CachedToken fetchAndCacheNewToken(String apiName) {
        ApiConfiguration.OAuth2Config oauthConfig = getOAuthConfig(apiName);
        if (oauthConfig == null || oauthConfig.getTokenUrl() == null || oauthConfig.getGrantType() == null) {
            log.error("OAuth 2.0 configuration is missing or invalid for API '{}'", apiName);
            return null;
        }

        MultiValueMap<String, String> formData = buildFormData(oauthConfig);

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
                    .block(Duration.ofSeconds(15)); 

            if (tokenResponse != null && tokenResponse.getAccessToken() != null) {
                log.info("Successfully fetched new token for API '{}' using grant_type '{}'", apiName, oauthConfig.getGrantType());
                return new CachedToken(tokenResponse.getAccessToken(), tokenResponse.getExpiresIn());
            }

        } catch (Exception e) {
            log.error("Exception while fetching OAuth token for API '{}': {}", apiName, e.getMessage());
        }

        return null;
    }

    /**
     * Constructs the form data for the token request based on the OAuth 2.0 configuration.
     *
     * @param oauthConfig The OAuth 2.0 configuration object.
     * @return A MultiValueMap containing the appropriate form fields for the grant type.
     */
    private MultiValueMap<String, String> buildFormData(ApiConfiguration.OAuth2Config oauthConfig) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        String grantType = oauthConfig.getGrantType();
        
        formData.add("grant_type", grantType);
        formData.add("client_id", oauthConfig.getClientId());
        formData.add("client_secret", oauthConfig.getClientSecret());

        if (oauthConfig.getScopes() != null && !oauthConfig.getScopes().isEmpty()) {
            formData.add("scope", oauthConfig.getScopes());
        }
        
        // Add fields specific to the "password" grant type
        if ("password".equalsIgnoreCase(grantType)) {
            formData.add("username", oauthConfig.getUsername());
            formData.add("password", oauthConfig.getPassword());
        }

        return formData;
    }

    /**
     * Retrieves the unified OAuth2Config for a given API.
     *
     * @param apiName The name of the API.
     * @return The OAuth2Config object.
     */
    private ApiConfiguration.OAuth2Config getOAuthConfig(String apiName) {
        // This assumes ApiConfigurationService can provide the full config by name
        return apiConfigurationService.getConfiguration(apiName)
                .getAuth()
                .getOauth2(); 
    }

    // --- INNER CLASSES (OAuth2TokenResponse and CachedToken) remain the same ---

    @Data
    private static class OAuth2TokenResponse {
        @JsonProperty("access_token")
        private String accessToken;

        @JsonProperty("expires_in")
        private long expiresIn;

        @JsonProperty("token_type")
        private String tokenType;

        @JsonProperty("scope")
        private String scope;
    }

    private static class CachedToken {
        private final String accessToken;
        private final Instant expiryTime;

        public CachedToken(String accessToken, long expiresInSeconds) {
            this.accessToken = accessToken;
            this.expiryTime = Instant.now().plusSeconds(Math.max(60, expiresInSeconds) - 60); // Ensure at least a 60s lifetime
        }

        public String getAccessToken() {
            return accessToken;
        }

        public boolean isExpired() {
            return Instant.now().isAfter(expiryTime);
        }
    }
}