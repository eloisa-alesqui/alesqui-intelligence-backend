package es.alesqui.intelligence.model.postman;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.mongodb.core.mapping.Field;

/**
 * A query parameter for URL Represents the query string part of the URL, parsed
 * into separate variables Follows the Postman Collection Format v2.1.0
 * specification
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QueryParam {

	/**
	 * The key of the query parameter
	 */
	@Field("key")
	@JsonProperty("key")
	private String key;

	/**
	 * The value of the query parameter
	 */
	@Field("value")
	@JsonProperty("value")
	private String value;

	/**
	 * If set to true, the current query parameter will not be sent with the
	 * request.
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
	 * Creates a new QueryParam with key and value
	 * 
	 * @param key   Parameter key
	 * @param value Parameter value
	 * @return QueryParam instance
	 */
	public static QueryParam create(String key, String value) {
		return QueryParam.builder().key(key).value(value).build();
	}

	/**
	 * Creates a new QueryParam with key, value and enabled/disabled state
	 * 
	 * @param key      Parameter key
	 * @param value    Parameter value
	 * @param disabled Whether the parameter is disabled
	 * @return QueryParam instance
	 */
	public static QueryParam create(String key, String value, boolean disabled) {
		return QueryParam.builder().key(key).value(value).disabled(disabled).build();
	}

	/**
	 * Creates a new QueryParam with key, value and description
	 * 
	 * @param key         Parameter key
	 * @param value       Parameter value
	 * @param description Parameter description
	 * @return QueryParam instance
	 */
	public static QueryParam create(String key, String value, String description) {
		return QueryParam.builder().key(key).value(value).description(Description.create(description)).build();
	}

	/**
	 * Creates a new enabled QueryParam with key and value
	 * 
	 * @param key   Parameter key
	 * @param value Parameter value
	 * @return QueryParam instance
	 */
	public static QueryParam enabled(String key, String value) {
		return create(key, value, false);
	}

	/**
	 * Creates a new disabled QueryParam with key and value
	 * 
	 * @param key   Parameter key
	 * @param value Parameter value
	 * @return QueryParam instance
	 */
	public static QueryParam disabled(String key, String value) {
		return create(key, value, true);
	}

	/**
	 * Sets the key for this query parameter
	 * 
	 * @param key Parameter key
	 * @return this instance for method chaining
	 */
	public QueryParam withKey(String key) {
		this.key = key;
		return this;
	}

	/**
	 * Sets the value for this query parameter
	 * 
	 * @param value Parameter value
	 * @return this instance for method chaining
	 */
	public QueryParam withValue(String value) {
		this.value = value;
		return this;
	}

	/**
	 * Sets the description for this query parameter
	 * 
	 * @param description Parameter description as string
	 * @return this instance for method chaining
	 */
	public QueryParam withDescription(String description) {
		this.description = Description.create(description);
		return this;
	}

	/**
	 * Sets the description for this query parameter
	 * 
	 * @param description Parameter description object
	 * @return this instance for method chaining
	 */
	public QueryParam withDescription(Description description) {
		this.description = description;
		return this;
	}

	/**
	 * Enables this query parameter (sets disabled to false)
	 * 
	 * @return this instance for method chaining
	 */
	public QueryParam enable() {
		this.disabled = false;
		return this;
	}

	/**
	 * Disables this query parameter (sets disabled to true)
	 * 
	 * @return this instance for method chaining
	 */
	public QueryParam disable() {
		this.disabled = true;
		return this;
	}

	/**
	 * Sets the disabled state of this query parameter
	 * 
	 * @param disabled Whether the parameter should be disabled
	 * @return this instance for method chaining
	 */
	public QueryParam setDisabled(boolean disabled) {
		this.disabled = disabled;
		return this;
	}

	/**
	 * Checks if this query parameter is enabled (not disabled)
	 * 
	 * @return true if enabled, false if disabled
	 */
	public boolean isEnabled() {
		return disabled == null || !disabled;
	}

	/**
	 * Checks if this query parameter is disabled
	 * 
	 * @return true if disabled, false if enabled
	 */
	public boolean isDisabled() {
		return disabled != null && disabled;
	}

	/**
	 * Gets the description as a string
	 * 
	 * @return Description content as string, or null if no description
	 */
	public String getDescriptionAsString() {
		return description != null ? description.getContent() : null;
	}

	/**
	 * Checks if this query parameter has a non-null, non-empty key
	 * 
	 * @return true if key is valid
	 */
	public boolean hasValidKey() {
		return key != null && !key.trim().isEmpty();
	}

	/**
	 * Checks if this query parameter has a non-null value
	 * 
	 * @return true if value is not null
	 */
	public boolean hasValue() {
		return value != null;
	}

	/**
	 * Gets the query parameter as a URL encoded string (key=value format)
	 * 
	 * @return Query parameter string, or empty string if disabled or invalid
	 */
	public String toQueryString() {
		if (isDisabled() || !hasValidKey()) {
			return "";
		}

		StringBuilder sb = new StringBuilder();
		sb.append(key);

		if (hasValue()) {
			sb.append("=").append(value);
		}

		return sb.toString();
	}

	/**
	 * Creates a copy of this QueryParam
	 * 
	 * @return A new QueryParam instance with the same values
	 */
	public QueryParam copy() {
		return QueryParam.builder().key(this.key).value(this.value).disabled(this.disabled)
				.description(this.description != null ? this.description.copy() : null).build();
	}

	/**
	 * Utility method to create a list of QueryParams from key-value pairs
	 * 
	 * @param keyValuePairs Alternating keys and values (key1, value1, key2, value2,
	 *                      ...)
	 * @return List of QueryParam objects
	 */
	public static List<QueryParam> fromKeyValuePairs(String... keyValuePairs) {
		List<QueryParam> params = new ArrayList<>();

		for (int i = 0; i < keyValuePairs.length - 1; i += 2) {
			params.add(create(keyValuePairs[i], keyValuePairs[i + 1]));
		}

		return params;
	}

	/**
	 * Utility method to convert a list of QueryParams to a query string
	 * 
	 * @param queryParams List of query parameters
	 * @return Query string (without leading '?')
	 */
	public static String toQueryString(List<QueryParam> queryParams) {
		if (queryParams == null || queryParams.isEmpty()) {
			return "";
		}

		StringBuilder sb = new StringBuilder();
		boolean first = true;

		for (QueryParam param : queryParams) {
			String paramString = param.toQueryString();
			if (!paramString.isEmpty()) {
				if (!first) {
					sb.append("&");
				}
				sb.append(paramString);
				first = false;
			}
		}

		return sb.toString();
	}

	@Override
	public String toString() {
		return String.format("QueryParam{key='%s', value='%s', disabled=%s}", key, value, disabled);
	}
}