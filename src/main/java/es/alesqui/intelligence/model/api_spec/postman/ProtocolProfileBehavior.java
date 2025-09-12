package es.alesqui.intelligence.model.api_spec.postman;

import org.springframework.data.mongodb.core.mapping.Field;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Set of configurations used to alter the usual behavior of sending the request
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProtocolProfileBehavior {

	/**
	 * Disable SSL certificate verification
	 */
	@Field("disableBodyPruning")
	@JsonProperty("disableBodyPruning")
	@Builder.Default
	private Boolean disableBodyPruning = false;

	/**
	 * Disable cookie jar
	 */
	@Field("disableCookies")
	@JsonProperty("disableCookies")
	@Builder.Default
	private Boolean disableCookies = false;

	/**
	 * Disable URL encoding
	 */
	@Field("disableUrlEncoding")
	@JsonProperty("disableUrlEncoding")
	@Builder.Default
	private Boolean disableUrlEncoding = false;

	/**
	 * Follow redirects automatically
	 */
	@Field("followRedirects")
	@JsonProperty("followRedirects")
	@Builder.Default
	private Boolean followRedirects = true;

	/**
	 * Follow original HTTP method when redirected
	 */
	@Field("followOriginalHttpMethod")
	@JsonProperty("followOriginalHttpMethod")
	@Builder.Default
	private Boolean followOriginalHttpMethod = false;

	/**
	 * Follow authorization header when redirected
	 */
	@Field("followAuthorizationHeader")
	@JsonProperty("followAuthorizationHeader")
	@Builder.Default
	private Boolean followAuthorizationHeader = false;

	/**
	 * Remove referer header on redirect
	 */
	@Field("removeRefererHeaderOnRedirect")
	@JsonProperty("removeRefererHeaderOnRedirect")
	@Builder.Default
	private Boolean removeRefererHeaderOnRedirect = false;

	/**
	 * Strict SSL certificate verification
	 */
	@Field("strictSSL")
	@JsonProperty("strictSSL")
	@Builder.Default
	private Boolean strictSSL = true;

	/**
	 * Maximum number of redirects to follow
	 */
	@Field("maxRedirects")
	@JsonProperty("maxRedirects")
	private Integer maxRedirects;

	/**
	 * Request timeout in milliseconds
	 */
	@Field("requestTimeout")
	@JsonProperty("requestTimeout")
	private Integer requestTimeout;

	/**
	 * Response timeout in milliseconds
	 */
	@Field("responseTimeout")
	@JsonProperty("responseTimeout")
	private Integer responseTimeout;

	/**
	 * Multiple value headers handling
	 */
	@Field("multipleValueHeaders")
	@JsonProperty("multipleValueHeaders")
	@Builder.Default
	private Boolean multipleValueHeaders = false;

	/**
	 * Automatically add Content-Length header
	 */
	@Field("automaticContentLength")
	@JsonProperty("automaticContentLength")
	@Builder.Default
	private Boolean automaticContentLength = true;

	/**
	 * Disable system proxy
	 */
	@Field("disableSystemProxy")
	@JsonProperty("disableSystemProxy")
	@Builder.Default
	private Boolean disableSystemProxy = false;

	/**
	 * Creates a default protocol profile behavior
	 * 
	 * @return Default ProtocolProfileBehavior instance
	 */
	public static ProtocolProfileBehavior defaultBehavior() {
		return ProtocolProfileBehavior.builder().build();
	}

	/**
	 * Creates a strict protocol profile behavior
	 * 
	 * @return Strict ProtocolProfileBehavior instance
	 */
	public static ProtocolProfileBehavior strict() {
		return ProtocolProfileBehavior.builder().strictSSL(true).followRedirects(false).disableCookies(false)
				.disableUrlEncoding(false).build();
	}

	/**
	 * Creates a lenient protocol profile behavior
	 * 
	 * @return Lenient ProtocolProfileBehavior instance
	 */
	public static ProtocolProfileBehavior lenient() {
		return ProtocolProfileBehavior.builder().strictSSL(false).followRedirects(true).followOriginalHttpMethod(true)
				.followAuthorizationHeader(true).maxRedirects(10).build();
	}

	/**
	 * Creates a no-redirect protocol profile behavior
	 * 
	 * @return No-redirect ProtocolProfileBehavior instance
	 */
	public static ProtocolProfileBehavior noRedirects() {
		return ProtocolProfileBehavior.builder().followRedirects(false).maxRedirects(0).build();
	}

	/**
	 * Creates a protocol profile behavior for testing
	 * 
	 * @return Testing ProtocolProfileBehavior instance
	 */
	public static ProtocolProfileBehavior forTesting() {
		return ProtocolProfileBehavior.builder().strictSSL(false).disableCookies(true).followRedirects(false)
				.requestTimeout(30000).responseTimeout(30000).build();
	}

	/**
	 * Creates a protocol profile behavior for production
	 * 
	 * @return Production ProtocolProfileBehavior instance
	 */
	public static ProtocolProfileBehavior forProduction() {
		return ProtocolProfileBehavior.builder().strictSSL(true).followRedirects(true).followOriginalHttpMethod(false)
				.followAuthorizationHeader(false).maxRedirects(5).requestTimeout(60000).responseTimeout(60000).build();
	}

	// Fluent API methods

	/**
	 * Disables body pruning
	 * 
	 * @return this instance for method chaining
	 */
	public ProtocolProfileBehavior disableBodyPruning() {
		this.disableBodyPruning = true;
		return this;
	}

	/**
	 * Enables body pruning
	 * 
	 * @return this instance for method chaining
	 */
	public ProtocolProfileBehavior enableBodyPruning() {
		this.disableBodyPruning = false;
		return this;
	}

	/**
	 * Disables cookies
	 * 
	 * @return this instance for method chaining
	 */
	public ProtocolProfileBehavior disableCookies() {
		this.disableCookies = true;
		return this;
	}

	/**
	 * Enables cookies
	 * 
	 * @return this instance for method chaining
	 */
	public ProtocolProfileBehavior enableCookies() {
		this.disableCookies = false;
		return this;
	}

	/**
	 * Disables URL encoding
	 * 
	 * @return this instance for method chaining
	 */
	public ProtocolProfileBehavior disableUrlEncoding() {
		this.disableUrlEncoding = true;
		return this;
	}

	/**
	 * Enables URL encoding
	 * 
	 * @return this instance for method chaining
	 */
	public ProtocolProfileBehavior enableUrlEncoding() {
		this.disableUrlEncoding = false;
		return this;
	}

	/**
	 * Enables following redirects
	 * 
	 * @return this instance for method chaining
	 */
	public ProtocolProfileBehavior followRedirects() {
		this.followRedirects = true;
		return this;
	}

	/**
	 * Disables following redirects
	 * 
	 * @return this instance for method chaining
	 */
	public ProtocolProfileBehavior dontFollowRedirects() {
		this.followRedirects = false;
		return this;
	}

	/**
	 * Enables following original HTTP method on redirect
	 * 
	 * @return this instance for method chaining
	 */
	public ProtocolProfileBehavior followOriginalHttpMethod() {
		this.followOriginalHttpMethod = true;
		return this;
	}

	/**
	 * Enables following authorization header on redirect
	 * 
	 * @return this instance for method chaining
	 */
	public ProtocolProfileBehavior followAuthorizationHeader() {
		this.followAuthorizationHeader = true;
		return this;
	}

	/**
	 * Enables removing referer header on redirect
	 * 
	 * @return this instance for method chaining
	 */
	public ProtocolProfileBehavior removeRefererHeaderOnRedirect() {
		this.removeRefererHeaderOnRedirect = true;
		return this;
	}

	/**
	 * Enables strict SSL
	 * 
	 * @return this instance for method chaining
	 */
	public ProtocolProfileBehavior strictSSL() {
		this.strictSSL = true;
		return this;
	}

	/**
	 * Disables strict SSL
	 * 
	 * @return this instance for method chaining
	 */
	public ProtocolProfileBehavior lenientSSL() {
		this.strictSSL = false;
		return this;
	}

	/**
	 * Sets the maximum number of redirects
	 * 
	 * @param maxRedirects Maximum redirects
	 * @return this instance for method chaining
	 */
	public ProtocolProfileBehavior withMaxRedirects(Integer maxRedirects) {
		this.maxRedirects = maxRedirects;
		return this;
	}

	/**
	 * Sets the request timeout
	 * 
	 * @param requestTimeout Request timeout in milliseconds
	 * @return this instance for method chaining
	 */
	public ProtocolProfileBehavior withRequestTimeout(Integer requestTimeout) {
		this.requestTimeout = requestTimeout;
		return this;
	}

	/**
	 * Sets the response timeout
	 * 
	 * @param responseTimeout Response timeout in milliseconds
	 * @return this instance for method chaining
	 */
	public ProtocolProfileBehavior withResponseTimeout(Integer responseTimeout) {
		this.responseTimeout = responseTimeout;
		return this;
	}

	/**
	 * Sets both request and response timeout
	 * 
	 * @param timeout Timeout in milliseconds
	 * @return this instance for method chaining
	 */
	public ProtocolProfileBehavior withTimeout(Integer timeout) {
		this.requestTimeout = timeout;
		this.responseTimeout = timeout;
		return this;
	}

	/**
	 * Enables multiple value headers
	 * 
	 * @return this instance for method chaining
	 */
	public ProtocolProfileBehavior enableMultipleValueHeaders() {
		this.multipleValueHeaders = true;
		return this;
	}

	/**
	 * Disables multiple value headers
	 * 
	 * @return this instance for method chaining
	 */
	public ProtocolProfileBehavior disableMultipleValueHeaders() {
		this.multipleValueHeaders = false;
		return this;
	}

	/**
	 * Enables automatic Content-Length header
	 * 
	 * @return this instance for method chaining
	 */
	public ProtocolProfileBehavior enableAutomaticContentLength() {
		this.automaticContentLength = true;
		return this;
	}

	/**
	 * Disables automatic Content-Length header
	 * 
	 * @return this instance for method chaining
	 */
	public ProtocolProfileBehavior disableAutomaticContentLength() {
		this.automaticContentLength = false;
		return this;
	}

	/**
	 * Disables system proxy
	 * 
	 * @return this instance for method chaining
	 */
	public ProtocolProfileBehavior disableSystemProxy() {
		this.disableSystemProxy = true;
		return this;
	}

	/**
	 * Enables system proxy
	 * 
	 * @return this instance for method chaining
	 */
	public ProtocolProfileBehavior enableSystemProxy() {
		this.disableSystemProxy = false;
		return this;
	}

	// Utility methods

	/**
	 * Checks if redirects are enabled
	 * 
	 * @return true if redirects are followed
	 */
	public boolean isRedirectsEnabled() {
		return Boolean.TRUE.equals(followRedirects);
	}

	/**
	 * Checks if SSL verification is strict
	 * 
	 * @return true if SSL is strict
	 */
	public boolean isStrictSSL() {
		return Boolean.TRUE.equals(strictSSL);
	}

	/**
	 * Checks if cookies are enabled
	 * 
	 * @return true if cookies are enabled
	 */
	public boolean isCookiesEnabled() {
		return !Boolean.TRUE.equals(disableCookies);
	}

	/**
	 * Checks if URL encoding is enabled
	 * 
	 * @return true if URL encoding is enabled
	 */
	public boolean isUrlEncodingEnabled() {
		return !Boolean.TRUE.equals(disableUrlEncoding);
	}

	/**
	 * Checks if body pruning is enabled
	 * 
	 * @return true if body pruning is enabled
	 */
	public boolean isBodyPruningEnabled() {
		return !Boolean.TRUE.equals(disableBodyPruning);
	}

	/**
	 * Gets the effective max redirects (default 5 if not set)
	 * 
	 * @return Maximum redirects
	 */
	public int getEffectiveMaxRedirects() {
		if (maxRedirects != null) {
			return maxRedirects;
		}
		return isRedirectsEnabled() ? 5 : 0;
	}

	/**
	 * Gets the effective request timeout (default 0 = no timeout)
	 * 
	 * @return Request timeout in milliseconds
	 */
	public int getEffectiveRequestTimeout() {
		return requestTimeout != null ? requestTimeout : 0;
	}

	/**
	 * Gets the effective response timeout (default 0 = no timeout)
	 * 
	 * @return Response timeout in milliseconds
	 */
	public int getEffectiveResponseTimeout() {
		return responseTimeout != null ? responseTimeout : 0;
	}

	/**
	 * Checks if any timeout is configured
	 * 
	 * @return true if timeout is configured
	 */
	public boolean hasTimeout() {
		return (requestTimeout != null && requestTimeout > 0) || (responseTimeout != null && responseTimeout > 0);
	}

	/**
	 * Checks if this behavior is configured for security
	 * 
	 * @return true if configured for security (strict SSL, no redirects, etc.)
	 */
	public boolean isSecurityOriented() {
		return Boolean.TRUE.equals(strictSSL) && !Boolean.TRUE.equals(followRedirects)
				&& !Boolean.TRUE.equals(followAuthorizationHeader);
	}

	/**
	 * Checks if this behavior is configured for performance
	 * 
	 * @return true if configured for performance
	 */
	public boolean isPerformanceOriented() {
		return Boolean.TRUE.equals(disableCookies) && Boolean.TRUE.equals(disableBodyPruning) && hasTimeout();
	}

	/**
	 * Creates a copy of this protocol profile behavior
	 * 
	 * @return Copy of this instance
	 */
	public ProtocolProfileBehavior copy() {
		return ProtocolProfileBehavior.builder().disableBodyPruning(this.disableBodyPruning)
				.disableCookies(this.disableCookies).disableUrlEncoding(this.disableUrlEncoding)
				.followRedirects(this.followRedirects).followOriginalHttpMethod(this.followOriginalHttpMethod)
				.followAuthorizationHeader(this.followAuthorizationHeader)
				.removeRefererHeaderOnRedirect(this.removeRefererHeaderOnRedirect).strictSSL(this.strictSSL)
				.maxRedirects(this.maxRedirects).requestTimeout(this.requestTimeout)
				.responseTimeout(this.responseTimeout).multipleValueHeaders(this.multipleValueHeaders)
				.automaticContentLength(this.automaticContentLength).disableSystemProxy(this.disableSystemProxy)
				.build();
	}

	/**
	 * Merges this behavior with another, giving preference to non-null values from
	 * the other
	 * 
	 * @param other Other protocol profile behavior
	 * @return Merged protocol profile behavior
	 */
	public ProtocolProfileBehavior merge(ProtocolProfileBehavior other) {
		if (other == null)
			return this.copy();

		return ProtocolProfileBehavior.builder()
				.disableBodyPruning(
						other.disableBodyPruning != null ? other.disableBodyPruning : this.disableBodyPruning)
				.disableCookies(other.disableCookies != null ? other.disableCookies : this.disableCookies)
				.disableUrlEncoding(
						other.disableUrlEncoding != null ? other.disableUrlEncoding : this.disableUrlEncoding)
				.followRedirects(other.followRedirects != null ? other.followRedirects : this.followRedirects)
				.followOriginalHttpMethod(other.followOriginalHttpMethod != null ? other.followOriginalHttpMethod
						: this.followOriginalHttpMethod)
				.followAuthorizationHeader(other.followAuthorizationHeader != null ? other.followAuthorizationHeader
						: this.followAuthorizationHeader)
				.removeRefererHeaderOnRedirect(
						other.removeRefererHeaderOnRedirect != null ? other.removeRefererHeaderOnRedirect
								: this.removeRefererHeaderOnRedirect)
				.strictSSL(other.strictSSL != null ? other.strictSSL : this.strictSSL)
				.maxRedirects(other.maxRedirects != null ? other.maxRedirects : this.maxRedirects)
				.requestTimeout(other.requestTimeout != null ? other.requestTimeout : this.requestTimeout)
				.responseTimeout(other.responseTimeout != null ? other.responseTimeout : this.responseTimeout)
				.multipleValueHeaders(
						other.multipleValueHeaders != null ? other.multipleValueHeaders : this.multipleValueHeaders)
				.automaticContentLength(other.automaticContentLength != null ? other.automaticContentLength
						: this.automaticContentLength)
				.disableSystemProxy(
						other.disableSystemProxy != null ? other.disableSystemProxy : this.disableSystemProxy)
				.build();
	}
}