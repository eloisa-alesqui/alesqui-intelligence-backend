package es.alesqui.postmangpt.model.unified;

import java.util.Map;

import org.springframework.data.mongodb.core.mapping.Field;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiConfiguration {

	/**
	 * Base URL for the API (overrides the document-level baseUrl if specified)
	 */
	@Field("baseUrl")
	@JsonProperty("baseUrl")
	private String baseUrl;

	/**
	 * Timeout in seconds for API calls
	 */
	@Field("timeoutSeconds")
	@JsonProperty("timeoutSeconds")
	@Builder.Default
	private int timeoutSeconds = 30;

	/**
	 * Custom headers for all requests to this API
	 */
	@Field("headers")
	@JsonProperty("headers")
	private Map<String, String> headers;

	/**
	 * Whether authentication is required 
	 */
	@Field("authRequired")
	@JsonProperty("authRequired")
	@Builder.Default
	private boolean authRequired = false;

	/**
	 * Authentication token
	 */
	@Field("authToken")
	@JsonProperty("authToken")
	private String authToken;

	/**
	 * Authentication type (Bearer, Basic, ApiKey)
	 */
	@Field("authType")
	@JsonProperty("authType")
	@Builder.Default
	private String authType = "Bearer";

	/**
	 * Maximum retry attempts
	 */
	@Field("maxRetries")
	@JsonProperty("maxRetries")
	@Builder.Default
	private int maxRetries = 3;

	/**
	 * Enable request/response logging
	 */
	@Field("enableLogging")
	@JsonProperty("enableLogging")
	@Builder.Default
	private boolean enableLogging = true;

	/**
	 * Rate limit requests per second (0 = no limit)
	 */
	@Field("rateLimit")
	@JsonProperty("rateLimit")
	@Builder.Default
	private int rateLimit = 0;

}
