package es.alesqui.postmangpt.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.mongodb.core.mapping.Field;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * A Cookie, that follows the Google Chrome format as defined in Postman
 * Collection Schema v2.1.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Cookie {

	/**
	 * Common cookie names
	 */
	public static final String SESSION_ID = "JSESSIONID";
	public static final String CSRF_TOKEN = "XSRF-TOKEN";
	public static final String AUTH_TOKEN = "auth-token";

	/**
	 * SameSite values (stored as extensions since not in Postman schema)
	 */
	public static final String SAMESITE_STRICT = "SameSite=Strict";
	public static final String SAMESITE_LAX = "SameSite=Lax";
	public static final String SAMESITE_NONE = "SameSite=None";

	/**
	 * The domain for which this cookie is valid.
	 */
	@Field("domain")
	@JsonProperty("domain")
	private String domain;

	/**
	 * When the cookie expires.
	 */
	@Field("expires")
	@JsonProperty("expires")
	private String expires;

	/**
	 * Max age of the cookie
	 */
	@Field("maxAge")
	@JsonProperty("maxAge")
	private String maxAge;

	/**
	 * True if the cookie is a host-only cookie. (i.e. a request's URL domain must
	 * exactly match the domain of the cookie).
	 */
	@Field("hostOnly")
	@JsonProperty("hostOnly")
	private Boolean hostOnly;

	/**
	 * Indicates if this cookie is HTTP Only. (if True, the cookie is inaccessible
	 * to client-side scripts)
	 */
	@Field("httpOnly")
	@JsonProperty("httpOnly")
	private Boolean httpOnly;

	/**
	 * This is the name of the Cookie.
	 */
	@Field("name")
	@JsonProperty("name")
	private String name;

	/**
	 * The path associated with the Cookie. REQUIRED by Postman schema
	 */
	@Field("path")
	@JsonProperty("path")
	private String path;

	/**
	 * Indicates if the 'secure' flag is set on the Cookie, meaning that it is
	 * transmitted over secure connections only. (typically HTTPS)
	 */
	@Field("secure")
	@JsonProperty("secure")
	private Boolean secure;

	/**
	 * True if the cookie is a session cookie.
	 */
	@Field("session")
	@JsonProperty("session")
	private Boolean session;

	/**
	 * The value of the Cookie.
	 */
	@Field("value")
	@JsonProperty("value")
	private String value;

	/**
	 * Custom attributes for a cookie go here, such as the Priority Field This is
	 * where we store SameSite and other modern attributes
	 */
	@Field("extensions")
	@JsonProperty("extensions")
	@Builder.Default
	private List<String> extensions = new ArrayList<>();

	/**
	 * Constructor for cookie with required fields (domain and path)
	 * 
	 * @param domain Cookie domain (required by Postman schema)
	 * @param path   Cookie path (required by Postman schema)
	 */
	public Cookie(String domain, String path) {
		this.domain = domain;
		this.path = path;
		this.extensions = new ArrayList<>();
	}

	/**
	 * Constructor for basic cookie with name, value, domain and path
	 * 
	 * @param name   Cookie name
	 * @param value  Cookie value
	 * @param domain Cookie domain (required)
	 * @param path   Cookie path (required)
	 */
	public Cookie(String name, String value, String domain, String path) {
		this.name = name;
		this.value = value;
		this.domain = domain;
		this.path = path;
		this.extensions = new ArrayList<>();
	}

	/**
	 * Creates a cookie with required fields
	 * 
	 * @param domain Cookie domain
	 * @param path   Cookie path
	 * @return Cookie instance
	 */
	public static Cookie create(String domain, String path) {
		return new Cookie(domain, path);
	}

	/**
	 * Creates a cookie with name, value, domain and path
	 * 
	 * @param name   Cookie name
	 * @param value  Cookie value
	 * @param domain Cookie domain
	 * @param path   Cookie path
	 * @return Cookie instance
	 */
	public static Cookie of(String name, String value, String domain, String path) {
		return new Cookie(name, value, domain, path);
	}

	/**
	 * Creates a session cookie
	 * 
	 * @param name   Cookie name
	 * @param value  Cookie value
	 * @param domain Cookie domain
	 * @param path   Cookie path
	 * @return Session cookie instance
	 */
	public static Cookie session(String name, String value, String domain, String path) {
		return Cookie.builder().name(name).value(value).domain(domain).path(path).session(true)
				.extensions(new ArrayList<>()).build();
	}

	/**
	 * Creates a secure cookie
	 * 
	 * @param name   Cookie name
	 * @param value  Cookie value
	 * @param domain Cookie domain
	 * @param path   Cookie path
	 * @return Secure cookie instance
	 */
	public static Cookie secure(String name, String value, String domain, String path) {
		return Cookie.builder().name(name).value(value).domain(domain).path(path).secure(true)
				.extensions(new ArrayList<>()).build();
	}

	/**
	 * Creates an HTTP-only cookie
	 * 
	 * @param name   Cookie name
	 * @param value  Cookie value
	 * @param domain Cookie domain
	 * @param path   Cookie path
	 * @return HTTP-only cookie instance
	 */
	public static Cookie httpOnly(String name, String value, String domain, String path) {
		return Cookie.builder().name(name).value(value).domain(domain).path(path).httpOnly(true)
				.extensions(new ArrayList<>()).build();
	}

	/**
	 * Creates a fully secure cookie (secure + httpOnly + SameSite=Strict)
	 * 
	 * @param name   Cookie name
	 * @param value  Cookie value
	 * @param domain Cookie domain
	 * @param path   Cookie path
	 * @return Fully secure cookie instance
	 */
	public static Cookie fullySecure(String name, String value, String domain, String path) {
		return Cookie.builder().name(name).value(value).domain(domain).path(path).secure(true).httpOnly(true)
				.extensions(Arrays.asList(SAMESITE_STRICT)).build();
	}

	/**
	 * Creates a CSRF token cookie
	 * 
	 * @param value  Token value
	 * @param domain Cookie domain
	 * @param path   Cookie path
	 * @return CSRF token cookie
	 */
	public static Cookie csrfToken(String value, String domain, String path) {
		return Cookie.builder().name(CSRF_TOKEN).value(value).domain(domain).path(path).secure(true).httpOnly(false) // CSRF
																														// tokens
																														// need
																														// to
																														// be
																														// accessible
																														// to
																														// JS
				.extensions(Arrays.asList(SAMESITE_STRICT)).build();
	}

	/**
	 * Creates a session ID cookie
	 * 
	 * @param value  Session ID value
	 * @param domain Cookie domain
	 * @param path   Cookie path
	 * @return Session ID cookie
	 */
	public static Cookie sessionId(String value, String domain, String path) {
		return Cookie.builder().name(SESSION_ID).value(value).domain(domain).path(path).secure(true).httpOnly(true)
				.session(true).extensions(Arrays.asList(SAMESITE_LAX)).build();
	}

	/**
	 * Sets the cookie name
	 * 
	 * @param name Cookie name
	 * @return this instance for method chaining
	 */
	public Cookie withName(String name) {
		this.name = name;
		return this;
	}

	/**
	 * Sets the cookie value
	 * 
	 * @param value Cookie value
	 * @return this instance for method chaining
	 */
	public Cookie withValue(String value) {
		this.value = value;
		return this;
	}

	/**
	 * Sets the cookie domain
	 * 
	 * @param domain Cookie domain
	 * @return this instance for method chaining
	 */
	public Cookie withDomain(String domain) {
		this.domain = domain;
		return this;
	}

	/**
	 * Sets the cookie path
	 * 
	 * @param path Cookie path
	 * @return this instance for method chaining
	 */
	public Cookie withPath(String path) {
		this.path = path;
		return this;
	}

	/**
	 * Sets the cookie expiration
	 * 
	 * @param expires Expiration date/time
	 * @return this instance for method chaining
	 */
	public Cookie withExpires(String expires) {
		this.expires = expires;
		return this;
	}

	/**
	 * Sets the cookie max age
	 * 
	 * @param maxAge Max age value
	 * @return this instance for method chaining
	 */
	public Cookie withMaxAge(String maxAge) {
		this.maxAge = maxAge;
		return this;
	}

	/**
	 * Adds an extension to the cookie
	 * 
	 * @param extension Extension to add
	 * @return this instance for method chaining
	 */
	public Cookie withExtension(String extension) {
		if (this.extensions == null) {
			this.extensions = new ArrayList<>();
		}
		this.extensions.add(extension);
		return this;
	}

	/**
	 * Sets the cookie extensions list
	 * 
	 * @param extensions Extensions list
	 * @return this instance for method chaining
	 */
	public Cookie withExtensions(List<String> extensions) {
		this.extensions = extensions != null ? new ArrayList<>(extensions) : new ArrayList<>();
		return this;
	}

	/**
	 * Sets the SameSite attribute (stored as extension)
	 * 
	 * @param sameSite SameSite value (Strict, Lax, None)
	 * @return this instance for method chaining
	 */
	public Cookie withSameSite(String sameSite) {
		return withExtension("SameSite=" + sameSite);
	}

	/**
	 * Sets the cookie as secure
	 * 
	 * @return this instance for method chaining
	 */
	public Cookie asSecure() {
		this.secure = true;
		return this;
	}

	/**
	 * Sets the cookie as HTTP-only
	 * 
	 * @return this instance for method chaining
	 */
	public Cookie asHttpOnly() {
		this.httpOnly = true;
		return this;
	}

	/**
	 * Sets the cookie as host-only
	 * 
	 * @return this instance for method chaining
	 */
	public Cookie asHostOnly() {
		this.hostOnly = true;
		return this;
	}

	/**
	 * Sets the cookie as session cookie
	 * 
	 * @return this instance for method chaining
	 */
	public Cookie asSession() {
		this.session = true;
		return this;
	}

	/**
	 * Sets the SameSite attribute to Strict (stored as extension)
	 * 
	 * @return this instance for method chaining
	 */
	public Cookie withSameSiteStrict() {
		return withExtension(SAMESITE_STRICT);
	}

	/**
	 * Sets the SameSite attribute to Lax (stored as extension)
	 * 
	 * @return this instance for method chaining
	 */
	public Cookie withSameSiteLax() {
		return withExtension(SAMESITE_LAX);
	}

	/**
	 * Sets the SameSite attribute to None (stored as extension)
	 * 
	 * @return this instance for method chaining
	 */
	public Cookie withSameSiteNone() {
		return withExtension(SAMESITE_NONE);
	}

	/**
	 * Checks if the cookie has a name
	 * 
	 * @return true if has name
	 */
	public boolean hasName() {
		return name != null && !name.trim().isEmpty();
	}

	/**
	 * Checks if the cookie has a value
	 * 
	 * @return true if has value
	 */
	public boolean hasValue() {
		return value != null && !value.trim().isEmpty();
	}

	/**
	 * Checks if the cookie has a domain
	 * 
	 * @return true if has domain
	 */
	public boolean hasDomain() {
		return domain != null && !domain.trim().isEmpty();
	}

	/**
	 * Checks if the cookie has a path
	 * 
	 * @return true if has path
	 */
	public boolean hasPath() {
		return path != null && !path.trim().isEmpty();
	}

	/**
	 * Checks if the cookie has an expiration
	 * 
	 * @return true if has expiration
	 */
	public boolean hasExpires() {
		return expires != null && !expires.trim().isEmpty();
	}

	/**
	 * Checks if the cookie has max age
	 * 
	 * @return true if has max age
	 */
	public boolean hasMaxAge() {
		return maxAge != null && !maxAge.trim().isEmpty();
	}

	/**
	 * Checks if the cookie has extensions
	 * 
	 * @return true if has extensions
	 */
	public boolean hasExtensions() {
		return extensions != null && !extensions.isEmpty();
	}

	/**
	 * Checks if the cookie has SameSite attribute (in extensions)
	 * 
	 * @return true if has SameSite
	 */
	public boolean hasSameSite() {
		if (!hasExtensions()) {
			return false;
		}
		return extensions.stream().anyMatch(ext -> ext != null && ext.toLowerCase().startsWith("samesite="));
	}

	/**
	 * Gets the SameSite value from extensions
	 * 
	 * @return SameSite value or null if not found
	 */
	public String getSameSite() {
		if (!hasExtensions()) {
			return null;
		}
		return extensions.stream().filter(ext -> ext != null && ext.toLowerCase().startsWith("samesite="))
				.map(ext -> ext.substring(9)) // Remove "SameSite="
				.findFirst().orElse(null);
	}

	/**
	 * Checks if the cookie is valid according to Postman schema (requires domain
	 * and path)
	 * 
	 * @return true if valid
	 */
	public boolean isValid() {
		return hasDomain() && hasPath();
	}

	/**
	 * Checks if the cookie is secure
	 * 
	 * @return true if secure flag is set
	 */
	public boolean isSecure() {
		return Boolean.TRUE.equals(secure);
	}

	/**
	 * Checks if the cookie is HTTP-only
	 * 
	 * @return true if HTTP-only flag is set
	 */
	public boolean isHttpOnly() {
		return Boolean.TRUE.equals(httpOnly);
	}

	/**
	 * Checks if the cookie is host-only
	 * 
	 * @return true if host-only flag is set
	 */
	public boolean isHostOnly() {
		return Boolean.TRUE.equals(hostOnly);
	}

	/**
	 * Checks if the cookie is a session cookie
	 * 
	 * @return true if session flag is set
	 */
	public boolean isSession() {
		return Boolean.TRUE.equals(session);
	}

	/**
	 * Checks if SameSite is Strict
	 * 
	 * @return true if SameSite is Strict
	 */
	public boolean isSameSiteStrict() {
		String sameSite = getSameSite();
		return "Strict".equalsIgnoreCase(sameSite);
	}

	/**
	 * Checks if SameSite is Lax
	 * 
	 * @return true if SameSite is Lax
	 */
	public boolean isSameSiteLax() {
		String sameSite = getSameSite();
		return "Lax".equalsIgnoreCase(sameSite);
	}

	/**
	 * Checks if SameSite is None
	 * 
	 * @return true if SameSite is None
	 */
	public boolean isSameSiteNone() {
		String sameSite = getSameSite();
		return "None".equalsIgnoreCase(sameSite);
	}

	/**
	 * Checks if the cookie is fully secure
	 * 
	 * @return true if secure, httpOnly and has SameSite
	 */
	public boolean isFullySecure() {
		return isSecure() && isHttpOnly() && hasSameSite();
	}

	/**
	 * Gets the cookie as a Set-Cookie header value
	 * 
	 * @return Set-Cookie header value
	 */
	public String toSetCookieString() {
		if (!hasName()) {
			return "";
		}

		StringBuilder sb = new StringBuilder();
		sb.append(name).append("=").append(value != null ? value : "");

		if (hasDomain()) {
			sb.append("; Domain=").append(domain);
		}

		if (hasPath()) {
			sb.append("; Path=").append(path);
		}

		if (hasExpires()) {
			sb.append("; Expires=").append(expires);
		}

		if (hasMaxAge()) {
			sb.append("; Max-Age=").append(maxAge);
		}

		if (Boolean.TRUE.equals(secure)) {
			sb.append("; Secure");
		}

		if (Boolean.TRUE.equals(httpOnly)) {
			sb.append("; HttpOnly");
		}

		// Add extensions (including SameSite)
		if (hasExtensions()) {
			for (String extension : extensions) {
				if (extension != null && !extension.trim().isEmpty()) {
					sb.append("; ").append(extension);
				}
			}
		}

		return sb.toString();
	}

	/**
	 * Gets the cookie as a Cookie header value
	 * 
	 * @return Cookie header value
	 */
	public String toCookieString() {
		if (!hasName()) {
			return "";
		}
		return name + "=" + (value != null ? value : "");
	}

	/**
	 * Gets cookie summary
	 * 
	 * @return Cookie summary
	 */
	public String getSummary() {
		StringBuilder sb = new StringBuilder();
		sb.append("Cookie: ").append(name != null ? name : "[no name]");
		sb.append("=").append(value != null ? value : "[no value]");

		if (hasDomain()) {
			sb.append(" (Domain: ").append(domain).append(")");
		}

		if (hasPath()) {
			sb.append(" (Path: ").append(path).append(")");
		}

		if (isSecure() || isHttpOnly() || hasSameSite()) {
			sb.append(" [");
			if (isSecure())
				sb.append("Secure ");
			if (isHttpOnly())
				sb.append("HttpOnly ");
			if (hasSameSite())
				sb.append(getSameSite());
			sb.append("]");
		}

		return sb.toString().trim();
	}

	/**
	 * Creates a copy of this cookie
	 * 
	 * @return Copy of this cookie
	 */
	public Cookie copy() {
		return Cookie.builder().domain(this.domain).expires(this.expires).maxAge(this.maxAge)
				.extensions(this.extensions != null ? new ArrayList<>(this.extensions) : new ArrayList<>())
				.hostOnly(this.hostOnly).httpOnly(this.httpOnly).name(this.name).path(this.path).secure(this.secure)
				.session(this.session).value(this.value).build();
	}

	/**
	 * Validates against Postman schema requirements
	 * 
	 * @return true if compliant with Postman schema
	 */
	public boolean isPostmanCompliant() {
		// Postman schema requires domain and path
		return hasDomain() && hasPath();
	}

	/**
	 * Gets validation errors for Postman compliance
	 * 
	 * @return List of validation errors
	 */
	public List<String> getPostmanValidationErrors() {
		List<String> errors = new ArrayList<>();

		if (!hasDomain()) {
			errors.add("Domain is required by Postman schema");
		}

		if (!hasPath()) {
			errors.add("Path is required by Postman schema");
		}

		return errors;
	}

	/**
	 * Parses a Set-Cookie header value into a Cookie object
	 * 
	 * @param setCookieHeader Set-Cookie header value
	 * @param defaultDomain   Default domain if not specified
	 * @param defaultPath     Default path if not specified
	 * @return Parsed Cookie object
	 */
	public static Cookie parseSetCookie(String setCookieHeader, String defaultDomain, String defaultPath) {
		if (setCookieHeader == null || setCookieHeader.trim().isEmpty()) {
			return null;
		}

		String[] parts = setCookieHeader.split(";");
		if (parts.length == 0) {
			return null;
		}

		// Parse name=value
		String[] nameValue = parts[0].trim().split("=", 2);
		if (nameValue.length < 2) {
			return null;
		}

		// Create extensions list to collect custom attributes
		List<String> extensionsList = new ArrayList<>();

		Cookie.CookieBuilder builder = Cookie.builder().name(nameValue[0].trim()).value(nameValue[1].trim())
				.domain(defaultDomain).path(defaultPath).extensions(extensionsList);

		// Parse attributes
		for (int i = 1; i < parts.length; i++) {
			String part = parts[i].trim();
			String[] attrValue = part.split("=", 2);
			String attr = attrValue[0].trim().toLowerCase();

			switch (attr) {
			case "domain":
				if (attrValue.length > 1) {
					builder.domain(attrValue[1].trim());
				}
				break;
			case "path":
				if (attrValue.length > 1) {
					builder.path(attrValue[1].trim());
				}
				break;
			case "expires":
				if (attrValue.length > 1) {
					builder.expires(attrValue[1].trim());
				}
				break;
			case "max-age":
				if (attrValue.length > 1) {
					builder.maxAge(attrValue[1].trim());
				}
				break;
			case "secure":
				builder.secure(true);
				break;
			case "httponly":
				builder.httpOnly(true);
				break;
			case "samesite":
				if (attrValue.length > 1) {
					extensionsList.add("SameSite=" + attrValue[1].trim());
				}
				break;
			default:
				// Add unknown attributes as extensions
				extensionsList.add(part);
				break;
			}
		}

		return builder.build();
	}

	@Override
	public String toString() {
		return getSummary();
	}
}