package es.alesqui.intelligence.service.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import es.alesqui.intelligence.service.UnifiedApiService;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiConfigurationService {

	private final UnifiedApiService unifiedApiService;
	private final int defaultTimeout = 30; // Default timeout in seconds

	/**
	 * Get base URL for an API
	 */
	@Cacheable(value = "api-base-urls", key = "#apiName") 
	String getBaseUrl(String apiName) {
	  try {
	      return unifiedApiService.findByName(apiName)
	              .map(doc -> {
	                  if (doc.getApiConfiguration() != null && 
	                      doc.getApiConfiguration().getBaseUrl() != null && 
	                      !doc.getApiConfiguration().getBaseUrl().isEmpty()) {
	                      return doc.getApiConfiguration().getBaseUrl();
	                  }
	                  
	                  // Fallback to document baseUrl if config baseUrl is not set
	                  return doc.getBaseUrl();
	              })
	              .block();
	  } catch (Exception e) {
	      log.warn("Failed to get base URL for {}: {}", apiName, e.getMessage());
	      return null;
	  }
	}

	/**
	 * Get headers for an API
	 */
	@Cacheable(value = "api-headers", key = "#apiName") 
	Map<String, String> getHeaders(String apiName) {
		try {
			return unifiedApiService.findByName(apiName).map(doc -> {
				Map<String, String> headers = new HashMap<>();

				// Add global headers
				if (doc.getGlobalHeaders() != null) {
					headers.putAll(doc.getGlobalHeaders());
				}

				// Add config headers
				if (doc.getApiConfiguration() != null && doc.getApiConfiguration().getHeaders() != null) {
					headers.putAll(doc.getApiConfiguration().getHeaders());
				}

				return headers;
			}).block();
		} catch (Exception e) {
			log.warn("Failed to get headers for {}: {}", apiName, e.getMessage());
			return new HashMap<>();
		}
	}

	/**
	 * Get timeout for an API
	 */
	@Cacheable(value = "api-timeouts", key = "#apiName") 
	int getTimeout(String apiName) {
		try {
			return unifiedApiService.findByName(apiName).map(doc -> {
				if (doc.getApiConfiguration() != null) {
					return doc.getApiConfiguration().getTimeoutSeconds();
				}
				return defaultTimeout;
			}).block();
		} catch (Exception e) {
			log.warn("Failed to get timeout for {}: {}", apiName, e.getMessage());
			return defaultTimeout;
		}
	}

	/**
	 * Check if authentication is required for an API
	 */
	@Cacheable(value = "api-auth-required", key = "#apiName") 
	boolean isAuthRequired(String apiName) {
		try {
			return unifiedApiService.findByName(apiName).map(doc -> {
				if (doc.getApiConfiguration() != null) {
					return doc.getApiConfiguration().isAuthRequired();
				}
				return false;
			}).block();
		} catch (Exception e) {
			log.warn("Failed to check auth requirement for {}: {}", apiName, e.getMessage());
			return false;
		}
	}

	/**
	 * Get authentication token for an API
	 */
	@Cacheable(value = "api-auth-tokens", key = "#apiName") 
	String getAuthToken(String apiName) {
		try {
			return unifiedApiService.findByName(apiName).map(doc -> {
				if (doc.getApiConfiguration() != null) {
					return doc.getApiConfiguration().getAuthToken();
				}
				return null;
			}).block();
		} catch (Exception e) {
			log.warn("Failed to get auth token for {}: {}", apiName, e.getMessage());
			return null;
		}
	}

	/**
	 * Get authentication type for an API
	 */
	@Cacheable(value = "api-auth-types", key = "#apiName") 
	String getAuthType(String apiName) {
		try {
			return unifiedApiService.findByName(apiName).map(doc -> {
				if (doc.getApiConfiguration() != null) {
					return doc.getApiConfiguration().getAuthType();
				}
				return "Bearer";
			}).block();
		} catch (Exception e) {
			log.warn("Failed to get auth type for {}: {}", apiName, e.getMessage());
			return "Bearer";
		}
	}

	/**
	 * Get max retries for an API
	 */
	@Cacheable(value = "api-retries", key = "#apiName") 
	int getMaxRetries(String apiName) {
		try {
			return unifiedApiService.findByName(apiName).map(doc -> {
				if (doc.getApiConfiguration() != null) {
					return doc.getApiConfiguration().getMaxRetries();
				}
				return 3;
			}).block();
		} catch (Exception e) {
			log.warn("Failed to get max retries for {}: {}", apiName, e.getMessage());
			return 3;
		}
	}

	/**
	 * Check if logging is enabled for an API
	 */
	@Cacheable(value = "api-logging", key = "#apiName")
	public boolean isLoggingEnabled(String apiName) {
		try {
			return unifiedApiService.findByName(apiName).map(doc -> {
				if (doc.getApiConfiguration() != null) {
					return doc.getApiConfiguration().isEnableLogging();
				}
				return true;
			}).block();
		} catch (Exception e) {
			log.warn("Failed to check logging setting for {}: {}", apiName, e.getMessage());
			return true;
		}
	}

	/**
	 * Get default timeout
	 */
	public int getDefaultTimeout() {
		return defaultTimeout;
	}
}
