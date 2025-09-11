package es.alesqui.intelligence.service.api;

import es.alesqui.intelligence.model.unified.ApiConfiguration;
import es.alesqui.intelligence.model.unified.UnifiedApiDocument;
import es.alesqui.intelligence.service.UnifiedApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Service responsible for retrieving and caching the static configuration of an API.
 * It provides access to settings like timeouts, retries, and pre-configured credentials.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiConfigurationService {

    private final UnifiedApiService unifiedApiService;

    /**
     * Retrieves the entire ApiConfiguration for a given API name.
     * This method is cached, making subsequent calls for the same API name highly efficient.
     *
     * @param apiName The unique name of the API.
     * @return The ApiConfiguration object, or a default empty object if not found.
     */
    @Cacheable(value = "api-configurations", key = "#apiName")
    public ApiConfiguration getConfiguration(String apiName) {
        log.debug("Fetching configuration for API '{}' from database.", apiName);
        try {
            return unifiedApiService.findByName(apiName)
                    .map(UnifiedApiDocument::getApiConfiguration)
                    .blockOptional()
                    .orElseGet(() -> {
                        log.warn("No configuration found for API '{}'. Returning default configuration.", apiName);
                        return new ApiConfiguration(); // Return default config to prevent NPEs
                    });
        } catch (Exception e) {
            log.error("Failed to retrieve configuration for API '{}': {}", apiName, e.getMessage(), e);
            return new ApiConfiguration(); // Return default on error
        }
    }

    /**
     * Builds a map of HTTP headers based on the static authentication configuration.
     * This method handles API Key, Basic Auth, and pre-configured Bearer tokens.
     * Note: It does NOT handle dynamic OAuth 2.0 token fetching; that is managed
     * by the ApiExecutionService at the time of the call.
     *
     * @param apiName The unique name of the API.
     * @return A map of headers to be included in the request.
     */
    public Map<String, String> getHeaders(String apiName) {
        Map<String, String> headers = new HashMap<>();
        ApiConfiguration config = getConfiguration(apiName);

        if (config == null || config.getAuth() == null) {
            return headers;
        }

        ApiConfiguration.AuthenticationConfig auth = config.getAuth();

        switch (auth.getAuthType()) {
            case "api_key":
                if ("header".equalsIgnoreCase(auth.getAddApiKeyTo()) && auth.getApiKey() != null) {
                    headers.put(auth.getApiKeyName(), auth.getApiKey());
                }
                break;

            case "bearer":
                if (auth.getBearerToken() != null) {
                    headers.put("Authorization", "Bearer " + auth.getBearerToken());
                }
                break;

            case "basic":
                if (auth.getBasicAuthUsername() != null && auth.getBasicAuthPassword() != null) {
                    String credentials = auth.getBasicAuthUsername() + ":" + auth.getBasicAuthPassword();
                    String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());
                    headers.put("Authorization", "Basic " + encodedCredentials);
                }
                break;

            // The "oauth2_client_credentials" case is intentionally omitted.
            // The token is dynamic and will be fetched and added by ApiExecutionService.
            case "oauth2_client_credentials":
            case "none":
            default:
                // No static authentication headers are added for these types.
                break;
        }

        return headers;
    }

    /**
     * Gets the base URL for an API, falling back to the document's URL if not specified in the config.
     *
     * @param apiName The unique name of the API.
     * @return The base URL as a String.
     */
    public String getBaseUrl(String apiName) {
        ApiConfiguration config = getConfiguration(apiName);
        if (config.getBaseUrl() != null && !config.getBaseUrl().isEmpty()) {
            return config.getBaseUrl();
        }
        // Fallback to the document's base URL if the config one isn't set
        return unifiedApiService.findByName(apiName)
                .map(UnifiedApiDocument::getBaseUrl)
                .block();
    }
    
    /**
     * Gets the configured timeout in seconds for an API.
     *
     * @param apiName The unique name of the API.
     * @return The timeout in seconds.
     */
    public int getTimeout(String apiName) {
        return getConfiguration(apiName).getTimeoutSeconds();
    }

    /**
     * Gets the configured maximum number of retries for an API.
     *
     * @param apiName The unique name of the API.
     * @return The max number of retries.
     */
    public int getMaxRetries(String apiName) {
        return getConfiguration(apiName).getMaxRetries();
    }

    /**
     * Checks if logging is enabled for the API.
     *
     * @param apiName The unique name of the API.
     * @return true if logging is enabled, false otherwise.
     */
    public boolean isLoggingEnabled(String apiName) {
        return getConfiguration(apiName).isEnableLogging();
    }

    /**
     * Checks if authentication is required based on the configured auth type.
     *
     * @param apiName The unique name of the API.
     * @return true if authType is not "none", false otherwise.
     */
    public boolean isAuthRequired(String apiName) {
        return Optional.ofNullable(getConfiguration(apiName).getAuth())
                .map(auth -> !"none".equalsIgnoreCase(auth.getAuthType()))
                .orElse(false);
    }
}