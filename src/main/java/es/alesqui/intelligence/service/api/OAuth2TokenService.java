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
     * Reactively gets a valid access token for the given API, using a cache-aside pattern.
     * It first checks for a valid, non-expired token in the cache. If not found, it
     * fetches a new token, caches it, and then returns it.
     *
     * @param apiName The unique name of the API.
     * @return A Mono emitting the access token string, or an empty Mono if a token cannot be obtained.
     */
    public Mono<String> getAccessToken(String apiName) {
        return Mono.defer(() -> {
            CachedToken existingToken = tokenCache.get(apiName);

            if (existingToken != null && !existingToken.isExpired()) {
                log.debug("Returning cached token for API '{}'", apiName);
                return Mono.just(existingToken);
            }

            log.info("No valid token in cache for API '{}'. Fetching a new one.", apiName);
            return fetchAndCacheNewToken(apiName)
                    .doOnNext(newToken -> tokenCache.put(apiName, newToken));
        })
        .map(CachedToken::getAccessToken);
    }

    /**
     * Reactively fetches a new OAuth2 token and wraps it in a CachedToken object.
     * This method is fully non-blocking.
     *
     * @param apiName The name of the API.
     * @return A Mono emitting a CachedToken on success, or an empty Mono if the configuration is invalid or fetching fails.
     */
    private Mono<CachedToken> fetchAndCacheNewToken(String apiName) {
        return getOAuthConfig(apiName)
                .flatMap(oauthConfig -> {
                    if (oauthConfig == null || oauthConfig.getTokenUrl() == null || oauthConfig.getGrantType() == null) {
                        log.error("OAuth 2.0 configuration is missing or invalid for API '{}'", apiName);
                        return Mono.empty();
                    }

                    MultiValueMap<String, String> formData = buildFormData(oauthConfig);
                    WebClient webClient = webClientBuilder.baseUrl(oauthConfig.getTokenUrl()).build();

                    return webClient.post()
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
                            .doOnSuccess(tokenResponse -> {
                                 if(tokenResponse != null && tokenResponse.getAccessToken() != null) {
                                    log.info("Successfully fetched new token for API '{}' using grant_type '{}'", apiName, oauthConfig.getGrantType());
                                 }
                            });
                })
                .map(tokenResponse -> new CachedToken(tokenResponse.getAccessToken(), tokenResponse.getExpiresIn()))
                .doOnError(e -> log.error("Exception while fetching OAuth token for API '{}': {}", apiName, e.getMessage()))
                .onErrorResume(e -> Mono.empty());
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
     * Reactively retrieves the unified OAuth2Config for a given API.
     *
     * @param apiName The name of the API.
     * @return A Mono emitting the OAuth2Config object.
     */
    private Mono<ApiConfiguration.OAuth2Config> getOAuthConfig(String apiName) {
        return apiConfigurationService.getConfiguration(apiName)
                .map(config -> config.getAuth().getOauth2()); 
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