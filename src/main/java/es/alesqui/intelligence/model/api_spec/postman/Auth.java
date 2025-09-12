package es.alesqui.intelligence.model.api_spec.postman;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

import org.springframework.data.mongodb.core.mapping.Field;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import es.alesqui.intelligence.model.api_spec.postman.enums.AuthType;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Represents authentication helpers provided by Postman
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL) 
public class Auth {

	/**
	 * The authentication type. Required field.
	 */
	@Field("type")
	@JsonProperty("type")
	private String type;

	/**
	 * No authentication placeholder
	 */
	@Field("noauth")
	@JsonProperty("noauth")
	private Object noauth;

	/**
	 * The attributes for API Key Authentication.
	 */
	@Field("apikey")
	@JsonProperty("apikey")
	@Builder.Default
	private List<AuthAttribute> apikey = new ArrayList<>();

	/**
	 * The attributes for AWS Auth.
	 */
	@Field("awsv4")
	@JsonProperty("awsv4")
	@Builder.Default
	private List<AuthAttribute> awsv4 = new ArrayList<>();

	/**
	 * The attributes for Basic Authentication.
	 */
	@Field("basic")
	@JsonProperty("basic")
	@Builder.Default
	private List<AuthAttribute> basic = new ArrayList<>();

	/**
	 * The helper attributes for Bearer Token Authentication.
	 */
	@Field("bearer")
	@JsonProperty("bearer")
	@Builder.Default
	private List<AuthAttribute> bearer = new ArrayList<>();

	/**
	 * The attributes for Digest Authentication.
	 */
	@Field("digest")
	@JsonProperty("digest")
	@Builder.Default
	private List<AuthAttribute> digest = new ArrayList<>();

	/**
	 * The attributes for Akamai EdgeGrid Authentication.
	 */
	@Field("edgegrid")
	@JsonProperty("edgegrid")
	@Builder.Default
	private List<AuthAttribute> edgegrid = new ArrayList<>();

	/**
	 * The attributes for Hawk Authentication.
	 */
	@Field("hawk")
	@JsonProperty("hawk")
	@Builder.Default
	private List<AuthAttribute> hawk = new ArrayList<>();

	/**
	 * The attributes for NTLM Authentication.
	 */
	@Field("ntlm")
	@JsonProperty("ntlm")
	@Builder.Default
	private List<AuthAttribute> ntlm = new ArrayList<>();

	/**
	 * The attributes for OAuth1.
	 */
	@Field("oauth1")
	@JsonProperty("oauth1")
	@Builder.Default
	private List<AuthAttribute> oauth1 = new ArrayList<>();

	/**
	 * Helper attributes for OAuth2.
	 */
	@Field("oauth2")
	@JsonProperty("oauth2")
	@Builder.Default
	private List<AuthAttribute> oauth2 = new ArrayList<>();

	/**
	 * Constructor with type only
	 * 
	 * @param type Authentication type
	 */
	public Auth(String type) {
		this.type = type;
		initializeLists();
	}

	/**
	 * Constructor with type enum
	 * 
	 * @param type Authentication type enum
	 */
	public Auth(AuthType type) {
		this.type = type.getValue();
		initializeLists();
	}

	private void initializeLists() {
		if (apikey == null)
			apikey = new ArrayList<>();
		if (awsv4 == null)
			awsv4 = new ArrayList<>();
		if (basic == null)
			basic = new ArrayList<>();
		if (bearer == null)
			bearer = new ArrayList<>();
		if (digest == null)
			digest = new ArrayList<>();
		if (edgegrid == null)
			edgegrid = new ArrayList<>();
		if (hawk == null)
			hawk = new ArrayList<>();
		if (ntlm == null)
			ntlm = new ArrayList<>();
		if (oauth1 == null)
			oauth1 = new ArrayList<>();
		if (oauth2 == null)
			oauth2 = new ArrayList<>();
	}

	// Factory methods for different auth types

	/**
	 * Creates no authentication
	 * 
	 * @return Auth instance with no authentication
	 */
	public static Auth noAuth() {
		return new Auth(AuthType.NOAUTH);
	}

	/**
	 * Creates basic authentication
	 * 
	 * @param username Username
	 * @param password Password
	 * @return Auth instance with basic authentication
	 */
	public static Auth basicAuth(String username, String password) {
		Auth auth = new Auth(AuthType.BASIC);
		auth.basic.add(AuthAttribute.username(username));
		auth.basic.add(AuthAttribute.password(password));
		return auth;
	}

	/**
	 * Creates basic authentication (alternative method name)
	 * 
	 * @param username Username
	 * @param password Password
	 * @return Auth instance with basic authentication
	 */
	public static Auth basic(String username, String password) {
		return basicAuth(username, password);
	}

	/**
	 * Creates bearer token authentication
	 * 
	 * @param token Bearer token
	 * @return Auth instance with bearer authentication
	 */
	public static Auth bearerToken(String token) {
		Auth auth = new Auth(AuthType.BEARER);
		auth.bearer.add(AuthAttribute.token(token));
		return auth;
	}

	/**
	 * Creates bearer token authentication (alternative method name)
	 * 
	 * @param token Bearer token
	 * @return Auth instance with bearer authentication
	 */
	public static Auth bearer(String token) {
		return bearerToken(token);
	}

	/**
	 * Creates API key authentication
	 * 
	 * @param key      API key value
	 * @param keyName  API key name
	 * @param location Location (header or query)
	 * @return Auth instance with API key authentication
	 */
	public static Auth apiKey(String key, String keyName, String location) {
		Auth auth = new Auth(AuthType.APIKEY);
		auth.apikey.add(AuthAttribute.apiKey(key));
		auth.apikey.add(AuthAttribute.of("key", keyName));
		auth.apikey.add(AuthAttribute.apiKeyIn(location));
		return auth;
	}

	/**
	 * Creates API key authentication in header
	 * 
	 * @param key     API key value
	 * @param keyName API key name
	 * @return Auth instance with API key authentication in header
	 */
	public static Auth apiKeyHeader(String key, String keyName) {
		return apiKey(key, keyName, "header");
	}

	/**
	 * Creates API key authentication in query
	 * 
	 * @param key     API key value
	 * @param keyName API key name
	 * @return Auth instance with API key authentication in query
	 */
	public static Auth apiKeyQuery(String key, String keyName) {
		return apiKey(key, keyName, "query");
	}

	/**
	 * Creates OAuth1 authentication
	 * 
	 * @param consumerKey    Consumer key
	 * @param consumerSecret Consumer secret
	 * @param token          Access token
	 * @param tokenSecret    Token secret
	 * @return Auth instance with OAuth1 authentication
	 */
	public static Auth oauth1(String consumerKey, String consumerSecret, String token, String tokenSecret) {
		Auth auth = new Auth(AuthType.OAUTH1);
		auth.oauth1.add(AuthAttribute.of("consumerKey", consumerKey));
		auth.oauth1.add(AuthAttribute.of("consumerSecret", consumerSecret));
		auth.oauth1.add(AuthAttribute.of("token", token));
		auth.oauth1.add(AuthAttribute.of("tokenSecret", tokenSecret));
		return auth;
	}

	/**
	 * Creates OAuth2 authentication
	 * 
	 * @param accessToken Access token
	 * @return Auth instance with OAuth2 authentication
	 */
	public static Auth oauth2(String accessToken) {
		Auth auth = new Auth(AuthType.OAUTH2);
		auth.oauth2.add(AuthAttribute.of("accessToken", accessToken));
		return auth;
	}

	/**
	 * Creates AWS v4 authentication
	 * 
	 * @param accessKey   AWS access key
	 * @param secretKey   AWS secret key
	 * @param awsRegion   AWS region
	 * @param serviceName AWS service name
	 * @return Auth instance with AWS v4 authentication
	 */
	public static Auth awsv4(String accessKey, String secretKey, String awsRegion, String serviceName) {
		Auth auth = new Auth(AuthType.AWSV4);
		auth.awsv4.add(AuthAttribute.of("accessKey", accessKey));
		auth.awsv4.add(AuthAttribute.of("secretKey", secretKey));
		auth.awsv4.add(AuthAttribute.of("region", awsRegion));
		auth.awsv4.add(AuthAttribute.of("service", serviceName));
		return auth;
	}

	/**
	 * Creates digest authentication
	 * 
	 * @param username Username
	 * @param password Password
	 * @param realm    Realm
	 * @return Auth instance with digest authentication
	 */
	public static Auth digest(String username, String password, String realm) {
		Auth auth = new Auth(AuthType.DIGEST);
		auth.digest.add(AuthAttribute.username(username));
		auth.digest.add(AuthAttribute.password(password));
		auth.digest.add(AuthAttribute.of("realm", realm));
		return auth;
	}

	/**
	 * Creates NTLM authentication
	 * 
	 * @param username Username
	 * @param password Password
	 * @param domain   Domain
	 * @return Auth instance with NTLM authentication
	 */
	public static Auth ntlm(String username, String password, String domain) {
		Auth auth = new Auth(AuthType.NTLM);
		auth.ntlm.add(AuthAttribute.username(username));
		auth.ntlm.add(AuthAttribute.password(password));
		auth.ntlm.add(AuthAttribute.of("domain", domain));
		return auth;
	}

	// Utility methods

	/**
	 * Gets the current authentication attributes based on type
	 * 
	 * @return List of current auth attributes
	 */
	public List<AuthAttribute> getCurrentAttributes() {
		if (type == null)
			return new ArrayList<>();

		switch (type.toLowerCase()) {
		case "apikey":
			return apikey;
		case "awsv4":
			return awsv4;
		case "basic":
			return basic;
		case "bearer":
			return bearer;
		case "digest":
			return digest;
		case "edgegrid":
			return edgegrid;
		case "hawk":
			return hawk;
		case "ntlm":
			return ntlm;
		case "oauth1":
			return oauth1;
		case "oauth2":
			return oauth2;
		default:
			return new ArrayList<>();
		}
	}

	/**
	 * Adds an attribute to the current authentication type
	 * 
	 * @param attribute The attribute to add
	 * @return this instance for method chaining
	 */
	public Auth addAttribute(AuthAttribute attribute) {
		getCurrentAttributes().add(attribute);
		return this;
	}

	/**
	 * Adds multiple attributes to the current authentication type
	 * 
	 * @param attributes The attributes to add
	 * @return this instance for method chaining
	 */
	public Auth addAttributes(AuthAttribute... attributes) {
		getCurrentAttributes().addAll(Arrays.asList(attributes));
		return this;
	}

	/**
	 * Adds multiple attributes from list to the current authentication type
	 * 
	 * @param attributes The attributes to add
	 * @return this instance for method chaining
	 */
	public Auth addAttributes(List<AuthAttribute> attributes) {
		if (attributes != null) {
			getCurrentAttributes().addAll(attributes);
		}
		return this;
	}

	/**
	 * Checks if this is no authentication
	 * 
	 * @return true if no authentication
	 */
	public boolean isNoAuth() {
		return AuthType.NOAUTH.getValue().equals(type);
	}

	/**
	 * Checks if this is basic authentication
	 * 
	 * @return true if basic authentication
	 */
	public boolean isBasic() {
		return AuthType.BASIC.getValue().equals(type);
	}

	/**
	 * Checks if this is bearer token authentication
	 * 
	 * @return true if bearer token authentication
	 */
	public boolean isBearer() {
		return AuthType.BEARER.getValue().equals(type);
	}

	/**
	 * Checks if this is API key authentication
	 * 
	 * @return true if API key authentication
	 */
	public boolean isApiKey() {
		return AuthType.APIKEY.getValue().equals(type);
	}

	/**
	 * Checks if this is OAuth1 authentication
	 * 
	 * @return true if OAuth1 authentication
	 */
	public boolean isOAuth1() {
		return AuthType.OAUTH1.getValue().equals(type);
	}

	/**
	 * Checks if this is OAuth2 authentication
	 * 
	 * @return true if OAuth2 authentication
	 */
	public boolean isOAuth2() {
		return AuthType.OAUTH2.getValue().equals(type);
	}

	/**
	 * Gets the authentication type as enum
	 * 
	 * @return AuthType enum or null if not found
	 */
	public AuthType getTypeEnum() {
		if (type == null)
			return null;

		for (AuthType t : AuthType.values()) {
			if (t.getValue().equals(type)) {
				return t;
			}
		}
		return null;
	}

	/**
	 * Gets an attribute value by key
	 * 
	 * @param key Attribute key
	 * @return Attribute value or null if not found
	 */
	public String getAttributeValue(String key) {
		List<AuthAttribute> attributes = getCurrentAttributes();
		if (attributes == null) {
			return null;
		}

		return attributes.stream().filter(attr -> key.equals(attr.getKey())).map(AuthAttribute::getValue)
				.filter(value -> value != null).map(Object::toString).findFirst().orElse(null);
	}

	/**
	 * Gets an attribute by key
	 * 
	 * @param key Attribute key
	 * @return AuthAttribute or null if not found
	 */
	public AuthAttribute getAttribute(String key) {
		List<AuthAttribute> attributes = getCurrentAttributes();
		if (attributes == null) {
			return null;
		}

		return attributes.stream().filter(attr -> key.equals(attr.getKey())).findFirst().orElse(null);
	}

	/**
	 * Sets an attribute value (replaces if exists, adds if not)
	 * 
	 * @param key   Attribute key
	 * @param value Attribute value
	 * @return this instance for method chaining
	 */
	public Auth setAttribute(String key, String value) {
		List<AuthAttribute> attributes = getCurrentAttributes();

		// Remove existing attribute with same key
		attributes.removeIf(attr -> key.equals(attr.getKey()));

		// Add new attribute
		attributes.add(AuthAttribute.of(key, value));

		return this;
	}

	/**
	 * Removes an attribute by key
	 * 
	 * @param key Attribute key to remove
	 * @return this instance for method chaining
	 */
	public Auth removeAttribute(String key) {
		List<AuthAttribute> attributes = getCurrentAttributes();
		attributes.removeIf(attr -> key.equals(attr.getKey()));
		return this;
	}

	/**
	 * Checks if an attribute exists
	 * 
	 * @param key Attribute key
	 * @return true if attribute exists
	 */
	public boolean hasAttribute(String key) {
		return getAttribute(key) != null;
	}

	/**
	 * Gets the number of attributes for current auth type
	 * 
	 * @return Number of attributes
	 */
	public int getAttributeCount() {
		List<AuthAttribute> attributes = getCurrentAttributes();
		return attributes != null ? attributes.size() : 0;
	}

	/**
	 * Validates the authentication configuration
	 * 
	 * @return true if authentication is valid
	 */
	public boolean isValid() {
		if (type == null || type.trim().isEmpty()) {
			return false;
		}

		// Check if type is valid
		if (getTypeEnum() == null) {
			return false;
		}

		// Validate based on type
		switch (type.toLowerCase()) {
		case "basic":
			return hasAttribute("username") && hasAttribute("password");
		case "bearer":
			return hasAttribute("token");
		case "apikey":
			return hasAttribute("value") && hasAttribute("key");
		case "oauth1":
			return hasAttribute("consumerKey") && hasAttribute("consumerSecret");
		case "oauth2":
			return hasAttribute("accessToken");
		case "noauth":
			return true;
		default:
			return getAttributeCount() > 0;
		}
	}

	/**
	 * Creates a copy of this authentication
	 * 
	 * @return Copy of this authentication
	 */
	public Auth copy() {
		Auth.AuthBuilder builder = Auth.builder().type(this.type).noauth(this.noauth);

		// Copy all attribute lists
		if (this.apikey != null) {
			List<AuthAttribute> copiedApikey = new ArrayList<>();
			for (AuthAttribute attr : this.apikey) {
				copiedApikey.add(attr != null ? attr.copy() : null);
			}
			builder.apikey(copiedApikey);
		}

		if (this.awsv4 != null) {
			List<AuthAttribute> copiedAwsv4 = new ArrayList<>();
			for (AuthAttribute attr : this.awsv4) {
				copiedAwsv4.add(attr != null ? attr.copy() : null);
			}
			builder.awsv4(copiedAwsv4);
		}

		if (this.basic != null) {
			List<AuthAttribute> copiedBasic = new ArrayList<>();
			for (AuthAttribute attr : this.basic) {
				copiedBasic.add(attr != null ? attr.copy() : null);
			}
			builder.basic(copiedBasic);
		}

		if (this.bearer != null) {
			List<AuthAttribute> copiedBearer = new ArrayList<>();
			for (AuthAttribute attr : this.bearer) {
				copiedBearer.add(attr != null ? attr.copy() : null);
			}
			builder.bearer(copiedBearer);
		}

		if (this.digest != null) {
			List<AuthAttribute> copiedDigest = new ArrayList<>();
			for (AuthAttribute attr : this.digest) {
				copiedDigest.add(attr != null ? attr.copy() : null);
			}
			builder.digest(copiedDigest);
		}

		if (this.edgegrid != null) {
			List<AuthAttribute> copiedEdgegrid = new ArrayList<>();
			for (AuthAttribute attr : this.edgegrid) {
				copiedEdgegrid.add(attr != null ? attr.copy() : null);
			}
			builder.edgegrid(copiedEdgegrid);
		}

		if (this.hawk != null) {
			List<AuthAttribute> copiedHawk = new ArrayList<>();
			for (AuthAttribute attr : this.hawk) {
				copiedHawk.add(attr != null ? attr.copy() : null);
			}
			builder.hawk(copiedHawk);
		}

		if (this.ntlm != null) {
			List<AuthAttribute> copiedNtlm = new ArrayList<>();
			for (AuthAttribute attr : this.ntlm) {
				copiedNtlm.add(attr != null ? attr.copy() : null);
			}
			builder.ntlm(copiedNtlm);
		}

		if (this.oauth1 != null) {
			List<AuthAttribute> copiedOauth1 = new ArrayList<>();
			for (AuthAttribute attr : this.oauth1) {
				copiedOauth1.add(attr != null ? attr.copy() : null);
			}
			builder.oauth1(copiedOauth1);
		}

		if (this.oauth2 != null) {
			List<AuthAttribute> copiedOauth2 = new ArrayList<>();
			for (AuthAttribute attr : this.oauth2) {
				copiedOauth2.add(attr != null ? attr.copy() : null);
			}
			builder.oauth2(copiedOauth2);
		}

		return builder.build();
	}

	/**
	 * Gets authentication summary
	 * 
	 * @return Authentication summary as string
	 */
	public String getSummary() {
		if (type == null) {
			return "Auth: No type specified";
		}

		StringBuilder summary = new StringBuilder();
		summary.append("Auth Type: ").append(type.toUpperCase()).append("\n");
		summary.append("Attributes: ").append(getAttributeCount()).append("\n");

		List<AuthAttribute> attributes = getCurrentAttributes();
		if (attributes != null && !attributes.isEmpty()) {
			summary.append("Keys: ");
			attributes.stream().map(AuthAttribute::getKey).filter(key -> key != null)
					.forEach(key -> summary.append(key).append(" "));
		}

		return summary.toString().trim();
	}

	/**
	 * Clears all attributes for current auth type
	 * 
	 * @return this instance for method chaining
	 */
	public Auth clearAttributes() {
		List<AuthAttribute> attributes = getCurrentAttributes();
		if (attributes != null) {
			attributes.clear();
		}
		return this;
	}

	/**
	 * Converts to string representation
	 */
	@Override
	public String toString() {
		return String.format("Auth[type=%s, attributes=%d]", type != null ? type : "null", getAttributeCount());
	}

	// Static utility methods

	/**
	 * Creates authentication from type string
	 * 
	 * @param type Authentication type
	 * @return Auth instance
	 */
	public static Auth of(String type) {
		return new Auth(type);
	}

	/**
	 * Creates authentication from type enum
	 * 
	 * @param type Authentication type enum
	 * @return Auth instance
	 */
	public static Auth of(AuthType type) {
		return new Auth(type);
	}

	/**
	 * Validates a list of authentications
	 * 
	 * @param auths List of authentications to validate
	 * @return List of validation errors
	 */
	public static List<String> validateAuths(List<Auth> auths) {
		List<String> errors = new ArrayList<>();

		if (auths == null) {
			errors.add("Auth list is null");
			return errors;
		}

		for (int i = 0; i < auths.size(); i++) {
			Auth auth = auths.get(i);
			if (auth == null) {
				errors.add("Auth at index " + i + " is null");
				continue;
			}

			if (!auth.isValid()) {
				errors.add("Auth at index " + i + " is invalid: " + auth.toString());
			}
		}

		return errors;
	}

	/**
	 * Gets all supported authentication types
	 * 
	 * @return List of supported auth types
	 */
	public static List<String> getSupportedTypes() {
		List<String> types = new ArrayList<>();
		for (AuthType type : AuthType.values()) {
			types.add(type.getValue());
		}
		return types;
	}

	/**
	 * Checks if a type is supported
	 * 
	 * @param type AuthType to check
	 * @return true if supported
	 */
	public static boolean isTypeSupported(String type) {
		if (type == null) {
			return false;
		}

		for (AuthType t : AuthType.values()) {
			if (t.getValue().equalsIgnoreCase(type)) {
				return true;
			}
		}
		return false;
	}
}