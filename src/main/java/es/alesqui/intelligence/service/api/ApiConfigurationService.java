package es.alesqui.intelligence.service.api;

import es.alesqui.intelligence.model.api_spec.unified.ApiConfiguration;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedApiDocument;
import es.alesqui.intelligence.service.UnifiedApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Service responsible for retrieving and caching the static configuration of an
 * API. It provides access to settings like timeouts, retries, and
 * pre-configured credentials.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiConfigurationService {

	private final UnifiedApiService unifiedApiService;

	/**
	 * Reactively retrieves the entire ApiConfiguration for a given API name. This
	 * method is cached, making subsequent calls for the same API name highly
	 * efficient.
	 *
	 * @param apiName The unique name of the API.
	 * @return A Mono emitting the ApiConfiguration, or a default empty object if
	 *         not found or on error.
	 */
	@Cacheable(value = "api-configurations", key = "#apiName")
	public Mono<ApiConfiguration> getConfiguration(String apiName) {
		log.debug("Fetching configuration for API '{}' from database.", apiName);
		return unifiedApiService.findByName(apiName).map(UnifiedApiDocument::getApiConfiguration)
				.defaultIfEmpty(new ApiConfiguration())
				.doOnError(
						e -> log.error("Failed to retrieve configuration for API '{}': {}", apiName, e.getMessage(), e))
				.onErrorReturn(new ApiConfiguration());
	}

	/**
	 * Reactively builds a map of HTTP headers based on the static authentication
	 * configuration. This method handles API Key, Basic Auth, and pre-configured
	 * Bearer tokens. Note: It does NOT handle dynamic OAuth 2.0 token fetching;
	 * that is managed by the ApiExecutionService at the time of the call.
	 *
	 * @param apiName The unique name of the API.
	 * @return A Mono emitting a map of headers to be included in the request.
	 */
	public Mono<Map<String, String>> getHeaders(String apiName) {
		return getConfiguration(apiName).map(config -> {
			Map<String, String> headers = new HashMap<>();

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

			// The "oauth2" case is intentionally omitted.
			// The token is dynamic and will be fetched and added by ApiExecutionService.
			case "oauth2":
			case "none":
			default:
				// No static authentication headers are added for these types.
				break;
			}

			return headers;
		});
	}

	/**
	 * Reactively gets the base URL for an API, falling back to the document's URL
	 * if not specified in the config.
	 *
	 * @param apiName The unique name of the API.
	 * @return A Mono emitting the base URL as a String.
	 */
	public Mono<String> getBaseUrl(String apiName) {
		return getConfiguration(apiName).flatMap(config -> {
			if (config.getBaseUrl() != null && !config.getBaseUrl().isEmpty()) {
				return Mono.just(config.getBaseUrl());
			}
			return unifiedApiService.findByName(apiName).map(UnifiedApiDocument::getBaseUrl);
		});
	}

	/**
	 * Reactively gets the configured timeout in seconds for an API.
	 *
	 * @param apiName The unique name of the API.
	 * @return A Mono emitting the timeout in seconds.
	 */
	public Mono<Integer> getTimeout(String apiName) {
		return getConfiguration(apiName).map(ApiConfiguration::getTimeoutSeconds);
	}

	/**
	 * Reactively gets the configured maximum number of retries for an API.
	 *
	 * @param apiName The unique name of the API.
	 * @return A Mono emitting the max number of retries.
	 */
	public Mono<Integer> getMaxRetries(String apiName) {
		return getConfiguration(apiName).map(ApiConfiguration::getMaxRetries);
	}

	/**
	 * Reactively checks if logging is enabled for the API.
	 *
	 * @param apiName The unique name of the API.
	 * @return A Mono emitting true if logging is enabled, false otherwise.
	 */
	public Mono<Boolean> isLoggingEnabled(String apiName) {
		return getConfiguration(apiName).map(ApiConfiguration::isEnableLogging);
	}

	/**
	 * Reactively checks if authentication is required based on the configured auth
	 * type.
	 *
	 * @param apiName The unique name of the API.
	 * @return A Mono emitting true if authType is not "none", false otherwise.
	 */
	public Mono<Boolean> isAuthRequired(String apiName) {
		return getConfiguration(apiName).map(config -> Optional.ofNullable(config.getAuth())
				.map(auth -> !"none".equalsIgnoreCase(auth.getAuthType())).orElse(false));
	}
}