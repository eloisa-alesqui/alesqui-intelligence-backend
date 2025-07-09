package es.alesqui.postmangpt.model.postman;

import org.springframework.data.mongodb.core.mapping.Field;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single HTTP Header
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Header {

	/**
	 * This holds the name of the HTTP Header, for example `Content-Type` or
	 * `X-Custom-Header`.
	 */
	@Field("key")
	@JsonProperty("key")
	private String key;

	/**
	 * The value (or the placeholder) of the HTTP Header, for example
	 * `application/json` or `{{variable}}`.
	 */
	@Field("value")
	@JsonProperty("value")
	private String value;

	/**
	 * If set to true, the current header will not be sent with requests.
	 * Defaults to false (enabled).
	 */
	@Field("disabled")
	@JsonProperty("disabled")
	@Builder.Default
	private Boolean disabled = false;

	/**
	 * A Description can be a raw text, or be an object, which holds the description
	 * along with its format.
	 */
	@Field("description")
	@JsonProperty("description")
	private Description description;

	/**
	 * Constructor for basic header with key and value
	 * 
	 * @param key   Header key
	 * @param value Header value
	 */
	public Header(String key, String value) {
		this.key = key;
		this.value = value;
		this.disabled = false;
	}

	/**
	 * Creates a header with key and value
	 * 
	 * @param key   Header key
	 * @param value Header value
	 * @return Header instance
	 */
	public static Header of(String key, String value) {
		return new Header(key, value);
	}

	/**
	 * Creates a disabled header
	 * 
	 * @param key   Header key
	 * @param value Header value
	 * @return Disabled header instance
	 */
	public static Header disabled(String key, String value) {
		return Header.builder().key(key).value(value).disabled(true).build();
	}

	// Common HTTP headers factory methods

	/**
	 * Creates Content-Type header
	 * 
	 * @param contentType Content type value
	 * @return Header instance
	 */
	public static Header contentType(String contentType) {
		return Header.of("Content-Type", contentType);
	}

	/**
	 * Creates Accept header
	 * 
	 * @param accept Accept value
	 * @return Header instance
	 */
	public static Header accept(String accept) {
		return Header.of("Accept", accept);
	}

	/**
	 * Creates Authorization header
	 * 
	 * @param authorization Authorization value
	 * @return Header instance
	 */
	public static Header authorization(String authorization) {
		return Header.of("Authorization", authorization);
	}

	/**
	 * Creates User-Agent header
	 * 
	 * @param userAgent User agent value
	 * @return Header instance
	 */
	public static Header userAgent(String userAgent) {
		return Header.of("User-Agent", userAgent);
	}

	/**
	 * Creates X-API-Key header
	 * 
	 * @param apiKey API key value
	 * @return Header instance
	 */
	public static Header apiKey(String apiKey) {
		return Header.of("X-API-Key", apiKey);
	}

	/**
	 * Creates custom API key header
	 * 
	 * @param headerName Header name (e.g., "X-RapidAPI-Key")
	 * @param apiKey     API key value
	 * @return Header instance
	 */
	public static Header customApiKey(String headerName, String apiKey) {
		return Header.of(headerName, apiKey);
	}

	/**
	 * Creates Bearer token Authorization header
	 * 
	 * @param token Bearer token
	 * @return Header instance
	 */
	public static Header bearerToken(String token) {
		return Header.of("Authorization", "Bearer " + token);
	}

	/**
	 * Creates Basic auth Authorization header
	 * 
	 * @param credentials Base64 encoded credentials
	 * @return Header instance
	 */
	public static Header basicAuth(String credentials) {
		return Header.of("Authorization", "Basic " + credentials);
	}

	/**
	 * Creates Basic auth Authorization header from username and password
	 * 
	 * @param username Username
	 * @param password Password
	 * @return Header instance
	 */
	public static Header basicAuth(String username, String password) {
		String credentials = java.util.Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
		return basicAuth(credentials);
	}

	// Content-Type shortcuts

	/**
	 * Creates Content-Type: application/json header
	 * 
	 * @return Header instance
	 */
	public static Header json() {
		return contentType("application/json");
	}

	/**
	 * Creates Content-Type: application/xml header
	 * 
	 * @return Header instance
	 */
	public static Header xml() {
		return contentType("application/xml");
	}

	/**
	 * Creates Content-Type: text/plain header
	 * 
	 * @return Header instance
	 */
	public static Header textPlain() {
		return contentType("text/plain");
	}

	/**
	 * Creates Content-Type: text/html header
	 * 
	 * @return Header instance
	 */
	public static Header html() {
		return contentType("text/html");
	}

	/**
	 * Creates Content-Type: application/x-www-form-urlencoded header
	 * 
	 * @return Header instance
	 */
	public static Header formUrlEncoded() {
		return contentType("application/x-www-form-urlencoded");
	}

	/**
	 * Creates Content-Type: multipart/form-data header
	 * 
	 * @return Header instance
	 */
	public static Header multipartFormData() {
		return contentType("multipart/form-data");
	}

	/**
	 * Creates Content-Type: application/octet-stream header
	 * 
	 * @return Header instance
	 */
	public static Header octetStream() {
		return contentType("application/octet-stream");
	}

	// Accept shortcuts

	/**
	 * Creates Accept: application/json header
	 * 
	 * @return Header instance
	 */
	public static Header acceptJson() {
		return accept("application/json");
	}

	/**
	 * Creates Accept: application/xml header
	 * 
	 * @return Header instance
	 */
	public static Header acceptXml() {
		return accept("application/xml");
	}

	/**
	 * Creates Accept: text/plain header
	 * 
	 * @return Header instance
	 */
	public static Header acceptText() {
		return accept("text/plain");
	}

	/**
	 * Creates Accept: text/html header
	 * 
	 * @return Header instance
	 */
	public static Header acceptHtml() {
		return accept("text/html");
	}

	/**
	 * Creates Accept header for all content types
	 * 
	 * @return Header instance with Accept: *&#47;* value
	 */
	public static Header acceptAll() {
		return accept("*/*");
	}

	// Cache control headers

	/**
	 * Creates Cache-Control header
	 * 
	 * @param cacheControl Cache control value
	 * @return Header instance
	 */
	public static Header cacheControl(String cacheControl) {
		return Header.of("Cache-Control", cacheControl);
	}

	/**
	 * Creates Cache-Control: no-cache header
	 * 
	 * @return Header instance
	 */
	public static Header noCache() {
		return cacheControl("no-cache");
	}

	/**
	 * Creates Cache-Control: no-store header
	 * 
	 * @return Header instance
	 */
	public static Header noStore() {
		return cacheControl("no-store");
	}

	// CORS headers

	/**
	 * Creates Access-Control-Allow-Origin header
	 * 
	 * @param origin Origin value
	 * @return Header instance
	 */
	public static Header accessControlAllowOrigin(String origin) {
		return Header.of("Access-Control-Allow-Origin", origin);
	}

	/**
	 * Creates Access-Control-Allow-Methods header
	 * 
	 * @param methods Methods value
	 * @return Header instance
	 */
	public static Header accessControlAllowMethods(String methods) {
		return Header.of("Access-Control-Allow-Methods", methods);
	}

	/**
	 * Creates Access-Control-Allow-Headers header
	 * 
	 * @param headers Headers value
	 * @return Header instance
	 */
	public static Header accessControlAllowHeaders(String headers) {
		return Header.of("Access-Control-Allow-Headers", headers);
	}

	// Custom headers

	/**
	 * Creates X-Requested-With header
	 * 
	 * @param value Requested with value (e.g., "XMLHttpRequest")
	 * @return Header instance
	 */
	public static Header xRequestedWith(String value) {
		return Header.of("X-Requested-With", value);
	}

	/**
	 * Creates X-Forwarded-For header
	 * 
	 * @param ip IP address
	 * @return Header instance
	 */
	public static Header xForwardedFor(String ip) {
		return Header.of("X-Forwarded-For", ip);
	}

	/**
	 * Creates X-Real-IP header
	 * 
	 * @param ip IP address
	 * @return Header instance
	 */
	public static Header xRealIp(String ip) {
		return Header.of("X-Real-IP", ip);
	}

	// Fluent API methods

	/**
	 * Sets the header as disabled
	 * 
	 * @return this instance for method chaining
	 */
	public Header disable() {
		this.disabled = true;
		return this;
	}

	/**
	 * Sets the header as enabled
	 * 
	 * @return this instance for method chaining
	 */
	public Header enable() {
		this.disabled = false;
		return this;
	}

	/**
	 * Sets the disabled state
	 * 
	 * @param disabled Whether the header is disabled
	 * @return this instance for method chaining
	 */
	public Header setDisabled(boolean disabled) {
		this.disabled = disabled;
		return this;
	}

	/**
	 * Sets the value
	 * 
	 * @param value New header value
	 * @return this instance for method chaining
	 */
	public Header withValue(String value) {
		this.value = value;
		return this;
	}

	/**
	 * Sets the key
	 * 
	 * @param key New header key
	 * @return this instance for method chaining
	 */
	public Header withKey(String key) {
		this.key = key;
		return this;
	}

	/**
	 * Sets the description
	 * 
	 * @param description Description text
	 * @return this instance for method chaining
	 */
	public Header withDescription(String description) {
		this.description = new Description(description);
		return this;
	}

	/**
	 * Sets the description object
	 * 
	 * @param description Description object
	 * @return this instance for method chaining
	 */
	public Header withDescription(Description description) {
		this.description = description;
		return this;
	}

	// Utility methods

	/**
	 * Checks if the header is enabled
	 * 
	 * @return true if enabled (not disabled)
	 */
	public boolean isEnabled() {
		return disabled == null || !disabled;
	}

	/**
	 * Checks if the header is disabled
	 * 
	 * @return true if disabled
	 */
	public boolean isDisabled() {
		return disabled != null && disabled;
	}

	/**
	 * Checks if this is a Content-Type header
	 * 
	 * @return true if Content-Type header
	 */
	public boolean isContentType() {
		return "Content-Type".equalsIgnoreCase(key);
	}

	/**
	 * Checks if this is an Authorization header
	 * 
	 * @return true if Authorization header
	 */
	public boolean isAuthorization() {
		return "Authorization".equalsIgnoreCase(key);
	}

	/**
	 * Checks if this is an Accept header
	 * 
	 * @return true if Accept header
	 */
	public boolean isAccept() {
		return "Accept".equalsIgnoreCase(key);
	}

	/**
	 * Checks if this is a User-Agent header
	 * 
	 * @return true if User-Agent header
	 */
	public boolean isUserAgent() {
		return "User-Agent".equalsIgnoreCase(key);
	}

	/**
	 * Checks if this is a Cache-Control header
	 * 
	 * @return true if Cache-Control header
	 */
	public boolean isCacheControl() {
		return "Cache-Control".equalsIgnoreCase(key);
	}

	/**
	 * Checks if this is a custom header (starts with X-)
	 * 
	 * @return true if custom header
	 */
	public boolean isCustomHeader() {
		return key != null && key.toLowerCase().startsWith("x-");
	}

	/**
	 * Checks if this header has a value
	 * 
	 * @return true if value is not null and not empty
	 */
	public boolean hasValue() {
		return value != null && !value.trim().isEmpty();
	}

	/**
	 * Checks if this header has a key
	 * 
	 * @return true if key is not null and not empty
	 */
	public boolean hasKey() {
		return key != null && !key.trim().isEmpty();
	}

	/**
	 * Checks if this header is valid (has both key and value)
	 * 
	 * @return true if header is valid
	 */
	public boolean isValid() {
		return hasKey() && hasValue();
	}

	/**
	 * Checks if the key matches (case-sensitive)
	 * 
	 * @param headerKey The key to check
	 * @return true if keys match
	 */
	public boolean hasKey(String headerKey) {
		return key != null && key.equals(headerKey);
	}

	/**
	 * Checks if the key matches (case-insensitive)
	 * 
	 * @param headerKey The key to check
	 * @return true if keys match ignoring case
	 */
	public boolean hasKeyIgnoreCase(String headerKey) {
		return key != null && key.equalsIgnoreCase(headerKey);
	}

	/**
	 * Gets the header as a string in HTTP format
	 * 
	 * @return Header string (e.g., "Content-Type: application/json")
	 */
	public String toHttpString() {
		return key + ": " + (value != null ? value : "");
	}

	/**
	 * Gets a summary of this header
	 * 
	 * @return Summary string
	 */
	public String getSummary() {
		StringBuilder summary = new StringBuilder();
		summary.append("Header[");
		summary.append("key=").append(key != null ? key : "null");
		summary.append(", value=").append(value != null ? value : "null");
		if (disabled != null && disabled) {
			summary.append(", disabled");
		}
		if (description != null) {
			summary.append(", hasDescription");
		}
		summary.append("]");
		return summary.toString();
	}

	/**
	 * Creates a copy of this header
	 * 
	 * @return Copy of this header
	 */
	public Header copy() {
		return Header.builder().key(this.key).value(this.value).disabled(this.disabled)
				.description(this.description != null ? this.description.copy() : null).build();
	}

	/**
	 * Creates a copy with a new value
	 * 
	 * @param newValue The new value for the copy
	 * @return Copy of this header with new value
	 */
	public Header copyWithValue(String newValue) {
		return Header.builder().key(this.key).value(newValue).disabled(this.disabled)
				.description(this.description != null ? this.description.copy() : null).build();
	}

	/**
	 * Creates a copy with a new key
	 * 
	 * @param newKey The new key for the copy
	 * @return Copy of this header with new key
	 */
	public Header copyWithKey(String newKey) {
		return Header.builder().key(newKey).value(this.value).disabled(this.disabled)
				.description(this.description != null ? this.description.copy() : null).build();
	}

	/**
	 * Creates a copy with new enabled/disabled state
	 * 
	 * @param newDisabled The new disabled state
	 * @return Copy of this header with new disabled state
	 */
	public Header copyWithDisabled(boolean newDisabled) {
		return Header.builder().key(this.key).value(this.value).disabled(newDisabled)
				.description(this.description != null ? this.description.copy() : null).build();
	}

	/**
	 * Creates an enabled copy of this header
	 * 
	 * @return Enabled copy of this header
	 */
	public Header copyEnabled() {
		return copyWithDisabled(false);
	}

	/**
	 * Creates a disabled copy of this header
	 * 
	 * @return Disabled copy of this header
	 */
	public Header copyDisabled() {
		return copyWithDisabled(true);
	}

	/**
	 * Creates a copy with a new description
	 * 
	 * @param newDescription The new description
	 * @return Copy of this header with new description
	 */
	public Header copyWithDescription(String newDescription) {
		return Header.builder().key(this.key).value(this.value).disabled(this.disabled)
				.description(newDescription != null ? new Description(newDescription) : null).build();
	}

	/**
	 * Creates a copy with a new description object
	 * 
	 * @param newDescription The new description object
	 * @return Copy of this header with new description
	 */
	public Header copyWithDescription(Description newDescription) {
		return Header.builder().key(this.key).value(this.value).disabled(this.disabled)
				.description(newDescription != null ? newDescription.copy() : null).build();
	}

	// Static utility methods

	/**
	 * Creates an empty header with just a key
	 * 
	 * @param key The header key
	 * @return Empty Header with key
	 */
	public static Header empty(String key) {
		return new Header(key, null);
	}

	/**
	 * Creates a header from HTTP header string
	 * 
	 * @param headerString String in format "key: value"
	 * @return Header or null if format is invalid
	 */
	public static Header fromHttpString(String headerString) {
		if (headerString == null || !headerString.contains(":")) {
			return null;
		}

		String[] parts = headerString.split(":", 2);
		if (parts.length != 2) {
			return null;
		}

		return Header.of(parts[0].trim(), parts[1].trim());
	}

	/**
	 * Validates a header
	 * 
	 * @param header The header to validate
	 * @return true if header is valid
	 */
	public static boolean isValid(Header header) {
		return header != null && header.isValid();
	}

	/**
	 * Converts to string representation
	 */
	@Override
	public String toString() {
		return String.format("Header[key=%s, value=%s, disabled=%s]", key, value,
				disabled != null ? disabled.toString() : "false");
	}
	
}