package es.alesqui.intelligence.model.api_spec.postman;

import org.springframework.data.mongodb.core.mapping.Field;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents an attribute for any authorization method provided by Postman. For
 * example `username` and `password` are set as auth attributes for Basic
 * Authentication method.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthAttribute {

	/**
	 * The key for the auth attribute. Required field.
	 */
	@Field("key")           
	@JsonProperty("key")
	private String key;

	/**
	 * The value for the auth attribute. Can be any type (String, Number, Boolean,
	 * etc.) Optional field.
	 */
	@Field("value")           
	@JsonProperty("value")
	private Object value;

	/**
	 * The type of the auth attribute. Optional field.
	 */
	@Field("type")           
	@JsonProperty("type")
	private String type;

	/**
	 * Constructor for basic auth attribute with key only
	 * 
	 * @param key The attribute key
	 */
	public AuthAttribute(String key) {
		this.key = key;
	}

	/**
	 * Constructor for auth attribute with key and value
	 * 
	 * @param key   The attribute key
	 * @param value The attribute value
	 */
	public AuthAttribute(String key, Object value) {
		this.key = key;
		this.value = value;
	}

	/**
	 * Creates an auth attribute with key and string value
	 * 
	 * @param key   The attribute key
	 * @param value The string value
	 * @return AuthAttribute instance
	 */
	public static AuthAttribute of(String key, String value) {
		return new AuthAttribute(key, value);
	}

	/**
	 * Creates an auth attribute with key and object value
	 * 
	 * @param key   The attribute key
	 * @param value The object value
	 * @return AuthAttribute instance
	 */
	public static AuthAttribute of(String key, Object value) {
		return new AuthAttribute(key, value);
	}

	/**
	 * Creates an auth attribute with key, value and type
	 * 
	 * @param key   The attribute key
	 * @param value The attribute value
	 * @param type  The attribute type
	 * @return AuthAttribute instance
	 */
	public static AuthAttribute of(String key, Object value, String type) {
		return AuthAttribute.builder().key(key).value(value).type(type).build();
	}

	/**
	 * Creates a username attribute
	 * 
	 * @param username The username value
	 * @return AuthAttribute for username
	 */
	public static AuthAttribute username(String username) {
		return AuthAttribute.of("username", username);
	}

	/**
	 * Creates a password attribute
	 * 
	 * @param password The password value
	 * @return AuthAttribute for password
	 */
	public static AuthAttribute password(String password) {
		return AuthAttribute.of("password", password);
	}

	/**
	 * Creates a token attribute
	 * 
	 * @param token The token value
	 * @return AuthAttribute for token
	 */
	public static AuthAttribute token(String token) {
		return AuthAttribute.of("token", token);
	}

	/**
	 * Creates an API key attribute
	 * 
	 * @param apiKey The API key value
	 * @return AuthAttribute for API key
	 */
	public static AuthAttribute apiKey(String apiKey) {
		return AuthAttribute.of("value", apiKey);
	}

	/**
	 * Creates an API key name attribute
	 * 
	 * @param keyName The API key name
	 * @return AuthAttribute for API key name
	 */
	public static AuthAttribute apiKeyName(String keyName) {
		return AuthAttribute.of("key", keyName);
	}

	/**
	 * Creates an API key location attribute (header/query)
	 * 
	 * @param location The location (header or query)
	 * @return AuthAttribute for API key location
	 */
	public static AuthAttribute apiKeyIn(String location) {
		return AuthAttribute.of("in", location);
	}

	/**
	 * Creates a consumer key attribute for OAuth1
	 * 
	 * @param consumerKey The consumer key value
	 * @return AuthAttribute for consumer key
	 */
	public static AuthAttribute consumerKey(String consumerKey) {
		return AuthAttribute.of("consumerKey", consumerKey);
	}

	/**
	 * Creates a consumer secret attribute for OAuth1
	 * 
	 * @param consumerSecret The consumer secret value
	 * @return AuthAttribute for consumer secret
	 */
	public static AuthAttribute consumerSecret(String consumerSecret) {
		return AuthAttribute.of("consumerSecret", consumerSecret);
	}

	/**
	 * Creates an access token attribute for OAuth
	 * 
	 * @param accessToken The access token value
	 * @return AuthAttribute for access token
	 */
	public static AuthAttribute accessToken(String accessToken) {
		return AuthAttribute.of("accessToken", accessToken);
	}

	/**
	 * Creates a token secret attribute for OAuth1
	 * 
	 * @param tokenSecret The token secret value
	 * @return AuthAttribute for token secret
	 */
	public static AuthAttribute tokenSecret(String tokenSecret) {
		return AuthAttribute.of("tokenSecret", tokenSecret);
	}

	/**
	 * Creates an AWS access key attribute
	 * 
	 * @param accessKey The AWS access key
	 * @return AuthAttribute for AWS access key
	 */
	public static AuthAttribute awsAccessKey(String accessKey) {
		return AuthAttribute.of("accessKey", accessKey);
	}

	/**
	 * Creates an AWS secret key attribute
	 * 
	 * @param secretKey The AWS secret key
	 * @return AuthAttribute for AWS secret key
	 */
	public static AuthAttribute awsSecretKey(String secretKey) {
		return AuthAttribute.of("secretKey", secretKey);
	}

	/**
	 * Creates an AWS region attribute
	 * 
	 * @param region The AWS region
	 * @return AuthAttribute for AWS region
	 */
	public static AuthAttribute awsRegion(String region) {
		return AuthAttribute.of("region", region);
	}

	/**
	 * Creates an AWS service attribute
	 * 
	 * @param service The AWS service name
	 * @return AuthAttribute for AWS service
	 */
	public static AuthAttribute awsService(String service) {
		return AuthAttribute.of("service", service);
	}

	/**
	 * Creates a realm attribute for Digest auth
	 * 
	 * @param realm The realm value
	 * @return AuthAttribute for realm
	 */
	public static AuthAttribute realm(String realm) {
		return AuthAttribute.of("realm", realm);
	}

	/**
	 * Creates a domain attribute for NTLM auth
	 * 
	 * @param domain The domain value
	 * @return AuthAttribute for domain
	 */
	public static AuthAttribute domain(String domain) {
		return AuthAttribute.of("domain", domain);
	}

	/**
	 * Gets the value as String
	 * 
	 * @return String representation of value
	 */
	public String getValueAsString() {
		return value != null ? value.toString() : null;
	}

	/**
	 * Gets the value as Integer
	 * 
	 * @return Integer value or null if not convertible
	 */
	public Integer getValueAsInteger() {
		if (value == null) {
			return null;
		}

		try {
			if (value instanceof Integer) {
				return (Integer) value;
			} else if (value instanceof Number) {
				return ((Number) value).intValue();
			} else {
				return Integer.valueOf(value.toString());
			}
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/**
	 * Gets the value as Boolean
	 * 
	 * @return Boolean value or null if not convertible
	 */
	public Boolean getValueAsBoolean() {
		if (value == null) {
			return null;
		}

		if (value instanceof Boolean) {
			return (Boolean) value;
		} else {
			String stringValue = value.toString().toLowerCase();
			return "true".equals(stringValue) || "1".equals(stringValue) || "yes".equals(stringValue);
		}
	}

	/**
	 * Gets the value as Double
	 * 
	 * @return Double value or null if not convertible
	 */
	public Double getValueAsDouble() {
		if (value == null) {
			return null;
		}

		try {
			if (value instanceof Double) {
				return (Double) value;
			} else if (value instanceof Number) {
				return ((Number) value).doubleValue();
			} else {
				return Double.valueOf(value.toString());
			}
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/**
	 * Gets the value with a specific type cast
	 * 
	 * @param <T>   The expected type
	 * @param clazz The class to cast to
	 * @return The value cast to the specified type or null if not possible
	 */
	@SuppressWarnings("unchecked")
	public <T> T getValueAs(Class<T> clazz) {
		if (value == null || clazz == null) {
			return null;
		}

		try {
			if (clazz.isInstance(value)) {
				return (T) value;
			} else if (clazz == String.class) {
				return (T) getValueAsString();
			} else if (clazz == Integer.class) {
				return (T) getValueAsInteger();
			} else if (clazz == Boolean.class) {
				return (T) getValueAsBoolean();
			} else if (clazz == Double.class) {
				return (T) getValueAsDouble();
			}
		} catch (Exception e) {
			// Return null if casting fails
		}

		return null;
	}

	/**
	 * Checks if the attribute has a value
	 * 
	 * @return true if value is not null
	 */
	public boolean hasValue() {
		return value != null;
	}

	/**
	 * Checks if the attribute has a non-empty value
	 * 
	 * @return true if value is not null and not empty string
	 */
	public boolean hasNonEmptyValue() {
		if (value == null) {
			return false;
		}

		String stringValue = value.toString();
		return !stringValue.trim().isEmpty();
	}

	/**
	 * Checks if the key matches (case-sensitive)
	 * 
	 * @param key The key to check
	 * @return true if keys match
	 */
	public boolean hasKey(String key) {
		return this.key != null && this.key.equals(key);
	}

	/**
	 * Checks if the key matches (case-insensitive)
	 * 
	 * @param key The key to check
	 * @return true if keys match ignoring case
	 */
	public boolean hasKeyIgnoreCase(String key) {
		return this.key != null && this.key.equalsIgnoreCase(key);
	}

	/**
	 * Checks if this attribute is valid (has a key)
	 * 
	 * @return true if attribute has a key
	 */
	public boolean isValid() {
		return key != null && !key.trim().isEmpty();
	}

	/**
	 * Sets the value and returns this instance for method chaining
	 * 
	 * @param value The new value
	 * @return this instance
	 */
	public AuthAttribute withValue(Object value) {
		this.value = value;
		return this;
	}

	/**
	 * Sets the type and returns this instance for method chaining
	 * 
	 * @param type The new type
	 * @return this instance
	 */
	public AuthAttribute withType(String type) {
		this.type = type;
		return this;
	}

	/**
	 * Sets the key and returns this instance for method chaining
	 * 
	 * @param key The new key
	 * @return this instance
	 */
	public AuthAttribute withKey(String key) {
		this.key = key;
		return this;
	}

	/**
	 * Creates a copy of this authentication attribute
	 * 
	 * @return Copy of this authentication attribute
	 */
	public AuthAttribute copy() {
		return AuthAttribute.builder().key(this.key).value(copyValue(this.value)).type(this.type).build();
	}

	/**
	 * Helper method to copy the value object For primitive types and strings, we
	 * can use the same reference For complex objects, this might need to be
	 * enhanced
	 * 
	 * @param originalValue The original value to copy
	 * @return Copy of the value
	 */
	private Object copyValue(Object originalValue) {
		if (originalValue == null) {
			return null;
		}

		// For immutable types, we can return the same reference
		if (originalValue instanceof String || originalValue instanceof Number || originalValue instanceof Boolean
				|| originalValue instanceof Character) {
			return originalValue;
		}

		// For other types, we might need specific handling
		// For now, we'll use toString() and return as String
		// This can be enhanced based on specific needs
		return originalValue.toString();
	}

	/**
	 * Creates a deep copy with a new value
	 * 
	 * @param newValue The new value for the copy
	 * @return Copy of this attribute with new value
	 */
	public AuthAttribute copyWithValue(Object newValue) {
		return AuthAttribute.builder().key(this.key).value(newValue).type(this.type).build();
	}

	/**
	 * Creates a deep copy with a new key
	 * 
	 * @param newKey The new key for the copy
	 * @return Copy of this attribute with new key
	 */
	public AuthAttribute copyWithKey(String newKey) {
		return AuthAttribute.builder().key(newKey).value(copyValue(this.value)).type(this.type).build();
	}

	/**
	 * Creates a deep copy with a new type
	 * 
	 * @param newType The new type for the copy
	 * @return Copy of this attribute with new type
	 */
	public AuthAttribute copyWithType(String newType) {
		return AuthAttribute.builder().key(this.key).value(copyValue(this.value)).type(newType).build();
	}

	/**
	 * Gets a summary of this attribute
	 * 
	 * @return Summary string
	 */
	public String getSummary() {
		StringBuilder summary = new StringBuilder();
		summary.append("AuthAttribute[");
		summary.append("key=").append(key != null ? key : "null");
		summary.append(", value=").append(value != null ? value.getClass().getSimpleName() : "null");
		if (type != null) {
			summary.append(", type=").append(type);
		}
		summary.append("]");
		return summary.toString();
	}

	/**
	 * Converts to string representation
	 */
	@Override
	public String toString() {
		return String.format("AuthAttribute[key=%s, value=%s, type=%s]", key, value != null ? value.toString() : "null",
				type != null ? type : "null");
	}

	// Static utility methods

	/**
	 * Creates an empty attribute with just a key
	 * 
	 * @param key The attribute key
	 * @return Empty AuthAttribute with key
	 */
	public static AuthAttribute empty(String key) {
		return new AuthAttribute(key);
	}

	/**
	 * Creates an attribute from key-value pair
	 * 
	 * @param keyValue String in format "key=value"
	 * @return AuthAttribute or null if format is invalid
	 */
	public static AuthAttribute fromKeyValue(String keyValue) {
		if (keyValue == null || !keyValue.contains("=")) {
			return null;
		}

		String[] parts = keyValue.split("=", 2);
		if (parts.length != 2) {
			return null;
		}

		return AuthAttribute.of(parts[0].trim(), parts[1].trim());
	}

	/**
	 * Validates an attribute
	 * 
	 * @param attribute The attribute to validate
	 * @return true if attribute is valid
	 */
	public static boolean isValid(AuthAttribute attribute) {
		return attribute != null && attribute.isValid();
	}
}