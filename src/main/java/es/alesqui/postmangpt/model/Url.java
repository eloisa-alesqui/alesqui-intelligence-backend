package es.alesqui.postmangpt.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

import org.springframework.data.mongodb.core.mapping.Field;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * A URL definition for Postman Collection If object, contains the complete
 * broken-down URL for this request. If string, contains the literal request
 * URL. Follows the Postman Collection Format v2.1.0 specification
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Url {

	/**
	 * The string representation of the request URL, including the protocol, host,
	 * path, hash, query parameter(s) and path variable(s).
	 */
	@Field("raw")
	@JsonProperty("raw")
	private String raw;

	/**
	 * The protocol associated with the request, E.g: 'http'
	 */
	@Field("protocol") 
	@JsonProperty("protocol")
	private String protocol;

	/**
	 * The host for the URL, E.g: api.yourdomain.com. Can be stored as a string or
	 * as an array of strings.
	 */
	@Field("host") 
	@JsonProperty("host")
	private List<String> host;

	/**
	 * The port number present in this URL. An empty value implies 80/443 depending
	 * on whether the protocol field contains http/https.
	 */
	@Field("port") 
	@JsonProperty("port")
	private String port;

	/**
	 * The complete path of the current url, broken down into segments. A segment
	 * could be a string, or a path variable.
	 */
	@Field("path") 
	@JsonProperty("path")
	private List<String> path;

	/**
	 * An array of QueryParams, which is basically the query string part of the URL,
	 * parsed into separate variables
	 */
	@Field("query") 
	@JsonProperty("query")
	private List<QueryParam> query;

	/**
	 * Contains the URL fragment (if any). Usually this is not transmitted over the
	 * network, but it could be useful to store this in some cases.
	 */
	@Field("hash") 
	@JsonProperty("hash")
	private String hash;

	/**
	 * Postman allows you to configure path variables with dynamic values that are
	 * populated during the request processing.
	 */
	@Field("variable") 
	@JsonProperty("variable")
	private List<Variable> variable;

	/**
	 * Creates a new Url from a raw URL string
	 * 
	 * @param rawUrl The complete URL string
	 * @return Url instance
	 */
	public static Url create(String rawUrl) {
		return Url.builder().raw(rawUrl).build();
	}

	/**
	 * Creates a new Url with protocol and host
	 * 
	 * @param protocol The protocol (http, https, etc.)
	 * @param host     The host as a string
	 * @return Url instance
	 */
	public static Url create(String protocol, String host) {
		return Url.builder().protocol(protocol).host(Arrays.asList(host.split("\\."))).build();
	}

	/**
	 * Creates a new Url with protocol, host and path
	 * 
	 * @param protocol The protocol
	 * @param host     The host as a string
	 * @param path     The path as a string
	 * @return Url instance
	 */
	public static Url create(String protocol, String host, String path) {
		return create(protocol, host).withPath(path);
	}

	/**
	 * Sets the protocol for this URL
	 * 
	 * @param protocol Protocol string (http, https, etc.)
	 * @return this instance for method chaining
	 */
	public Url withProtocol(String protocol) {
		this.protocol = protocol;
		return this;
	}

	/**
	 * Sets the host for this URL
	 * 
	 * @param host Host as a string (will be split by dots)
	 * @return this instance for method chaining
	 */
	public Url withHost(String host) {
		this.host = Arrays.asList(host.split("\\."));
		return this;
	}

	/**
	 * Sets the host for this URL
	 * 
	 * @param hostParts Host as a list of strings
	 * @return this instance for method chaining
	 */
	public Url withHost(List<String> hostParts) {
		this.host = new ArrayList<>(hostParts);
		return this;
	}

	/**
	 * Sets the port for this URL
	 * 
	 * @param port Port number as string
	 * @return this instance for method chaining
	 */
	public Url withPort(String port) {
		this.port = port;
		return this;
	}

	/**
	 * Sets the port for this URL
	 * 
	 * @param port Port number as integer
	 * @return this instance for method chaining
	 */
	public Url withPort(int port) {
		this.port = String.valueOf(port);
		return this;
	}

	/**
	 * Sets the path for this URL
	 * 
	 * @param path Path as a string (will be split by slashes)
	 * @return this instance for method chaining
	 */
	public Url withPath(String path) {
		if (path != null && !path.isEmpty()) {
			// Remove leading slash and split by slash
			String cleanPath = path.startsWith("/") ? path.substring(1) : path;
			this.path = Arrays.asList(cleanPath.split("/"));
		}
		return this;
	}

	/**
	 * Sets the path for this URL
	 * 
	 * @param pathSegments Path as a list of strings
	 * @return this instance for method chaining
	 */
	public Url withPath(List<String> pathSegments) {
		this.path = new ArrayList<>(pathSegments);
		return this;
	}

	/**
	 * Adds a path segment to this URL
	 * 
	 * @param segment Path segment to add
	 * @return this instance for method chaining
	 */
	public Url addPathSegment(String segment) {
		if (this.path == null) {
			this.path = new ArrayList<>();
		}
		this.path.add(segment);
		return this;
	}

	/**
	 * Sets the hash fragment for this URL
	 * 
	 * @param hash Hash fragment
	 * @return this instance for method chaining
	 */
	public Url withHash(String hash) {
		this.hash = hash;
		return this;
	}

	/**
	 * Adds a query parameter to this URL
	 * 
	 * @param key   Parameter key
	 * @param value Parameter value
	 * @return this instance for method chaining
	 */
	public Url addQueryParam(String key, String value) {
		if (this.query == null) {
			this.query = new ArrayList<>();
		}
		this.query.add(QueryParam.builder().key(key).value(value).build());
		return this;
	}

	/**
	 * Adds a query parameter to this URL
	 * 
	 * @param queryParam QueryParam object
	 * @return this instance for method chaining
	 */
	public Url addQueryParam(QueryParam queryParam) {
		if (this.query == null) {
			this.query = new ArrayList<>();
		}
		this.query.add(queryParam);
		return this;
	}

	/**
	 * Sets the query parameters for this URL
	 * 
	 * @param queryParams List of query parameters
	 * @return this instance for method chaining
	 */
	public Url withQuery(List<QueryParam> queryParams) {
		this.query = new ArrayList<>(queryParams);
		return this;
	}

	/**
	 * Adds a path variable to this URL
	 * 
	 * @param variable Variable to add
	 * @return this instance for method chaining
	 */
	public Url addVariable(Variable variable) {
		if (this.variable == null) {
			this.variable = new ArrayList<>();
		}
		this.variable.add(variable);
		return this;
	}

	/**
	 * Adds a path variable to this URL
	 * 
	 * @param key   Variable key
	 * @param value Variable value
	 * @return this instance for method chaining
	 */
	public Url addVariable(String key, String value) {
		return addVariable(Variable.builder().key(key).value(value).build());
	}

	/**
	 * Sets the path variables for this URL
	 * 
	 * @param variables List of variables
	 * @return this instance for method chaining
	 */
	public Url withVariables(List<Variable> variables) {
		this.variable = new ArrayList<>(variables);
		return this;
	}

	/**
	 * Builds the raw URL string from the components
	 * 
	 * @return The constructed URL string
	 */
	public String buildRawUrl() {
		if (this.raw != null && !this.raw.isEmpty()) {
			return this.raw;
		}

		StringBuilder url = new StringBuilder();

		// Protocol
		if (protocol != null) {
			url.append(protocol).append("://");
		}

		// Host
		if (host != null && !host.isEmpty()) {
			url.append(String.join(".", host));
		}

		// Port
		if (port != null && !port.isEmpty()) {
			url.append(":").append(port);
		}

		// Path
		if (path != null && !path.isEmpty()) {
			url.append("/").append(String.join("/", path));
		}

		// Query parameters
		if (query != null && !query.isEmpty()) {
			url.append("?");
			for (int i = 0; i < query.size(); i++) {
				QueryParam param = query.get(i);
				if (i > 0) {
					url.append("&");
				}
				url.append(param.getKey());
				if (param.getValue() != null) {
					url.append("=").append(param.getValue());
				}
			}
		}

		// Hash
		if (hash != null && !hash.isEmpty()) {
			url.append("#").append(hash);
		}

		return url.toString();
	}

	/**
	 * Gets the host as a single string
	 * 
	 * @return Host as string (joined by dots)
	 */
	public String getHostAsString() {
		return host != null ? String.join(".", host) : null;
	}

	/**
	 * Gets the path as a single string
	 * 
	 * @return Path as string (joined by slashes)
	 */
	public String getPathAsString() {
		return path != null ? "/" + String.join("/", path) : null;
	}
}