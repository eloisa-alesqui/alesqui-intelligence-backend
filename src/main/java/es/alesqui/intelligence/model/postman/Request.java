package es.alesqui.intelligence.model.postman;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import es.alesqui.intelligence.model.postman.enums.RequestMethod;
import es.alesqui.intelligence.util.serialization.UrlDeserializer;

import java.util.List;

import org.springframework.data.mongodb.core.mapping.Field;

import java.util.ArrayList;

/**
 * A request represents an HTTP request. If a string, the string is assumed to
 * be the request URL and the method is assumed to be 'GET'. Follows the Postman
 * Collection Format v2.1.0 specification
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Request {

	/**
	 * The object represents an HTTP request URL. Can be a string or a Url object.
	 */
	@Field("url")
    @JsonProperty("url")
    @JsonDeserialize(using = UrlDeserializer.class)
	private Object url; // Can be String or Url object

	/**
	 * Represents authentication helpers provided by Postman
	 */
	@Field("auth") 
	@JsonProperty("auth")
	private Auth auth;

	/**
	 * The HTTP method associated with this request.
	 */
	@Field("method") 
	@JsonProperty("method")
	@Builder.Default
	private RequestMethod method = RequestMethod.GET;

	/**
	 * A list of headers that belong to the request
	 */
	@Field("header")
	@JsonProperty("header")
	@Builder.Default
	private List<Header> header = new ArrayList<>();

	/**
	 * This field contains the data usually contained in the request body.
	 */
	@Field("body")  
	@JsonProperty("body")
	private Body body;

	/**
	 * Using the Proxy, you can configure your custom proxy into the postman for
	 * particular url match
	 */
	@Field("proxy") 
	@JsonProperty("proxy")
	private Proxy proxy;

	/**
	 * Represents the certificate used for client authentication
	 */
	@Field("certificate") 
	@JsonProperty("certificate")
	private Certificate certificate;

	/**
	 * A Description can be a raw text, or be an object, which holds the description
	 * along with its format.
	 */
	@Field("description") 
	@JsonProperty("description")
	private Description description;

	/**
	 * Constructor for simple GET request
	 * 
	 * @param url Request URL
	 */
	public Request(String url) {
		this.url = url;
		this.method = RequestMethod.GET;
		this.header = new ArrayList<>();
	}

	/**
	 * Constructor for request with method
	 * 
	 * @param method HTTP method string
	 * @param url    Request URL
	 */
	public Request(String method, String url) {
		this.method = RequestMethod.fromString(method);
		this.url = url;
		this.header = new ArrayList<>();
	}

	/**
	 * Constructor with method enum
	 * 
	 * @param method HTTP method enum
	 * @param url    Request URL
	 */
	public Request(RequestMethod method, String url) {
		this.method = method != null ? method : RequestMethod.GET;
		this.url = url;
		this.header = new ArrayList<>();
	}

	// Factory methods for different HTTP methods

	/**
	 * Creates a GET request
	 * 
	 * @param url Request URL
	 * @return Request instance
	 */
	public static Request get(String url) {
		return new Request(RequestMethod.GET, url);
	}

	/**
	 * Creates a POST request
	 * 
	 * @param url Request URL
	 * @return Request instance
	 */
	public static Request post(String url) {
		return new Request(RequestMethod.POST, url);
	}

	/**
	 * Creates a PUT request
	 * 
	 * @param url Request URL
	 * @return Request instance
	 */
	public static Request put(String url) {
		return new Request(RequestMethod.PUT, url);
	}

	/**
	 * Creates a PATCH request
	 * 
	 * @param url Request URL
	 * @return Request instance
	 */
	public static Request patch(String url) {
		return new Request(RequestMethod.PATCH, url);
	}

	/**
	 * Creates a DELETE request
	 * 
	 * @param url Request URL
	 * @return Request instance
	 */
	public static Request delete(String url) {
		return new Request(RequestMethod.DELETE, url);
	}

	/**
	 * Creates a HEAD request
	 * 
	 * @param url Request URL
	 * @return Request instance
	 */
	public static Request head(String url) {
		return new Request(RequestMethod.HEAD, url);
	}

	/**
	 * Creates an OPTIONS request
	 * 
	 * @param url Request URL
	 * @return Request instance
	 */
	public static Request options(String url) {
		return new Request(RequestMethod.OPTIONS, url);
	}

	/**
	 * Creates a request with custom method
	 * 
	 * @param method HTTP method string
	 * @param url    Request URL
	 * @return Request instance
	 */
	public static Request of(String method, String url) {
		return new Request(method, url);
	}

	/**
	 * Creates a request with method enum
	 * 
	 * @param method HTTP method enum
	 * @param url    Request URL
	 * @return Request instance
	 */
	public static Request of(RequestMethod method, String url) {
		return new Request(method, url);
	}

	// Fluent API methods

	/**
	 * Sets the URL as string
	 * 
	 * @param url Request URL
	 * @return this instance for method chaining
	 */
	public Request withUrl(String url) {
		this.url = url;
		return this;
	}

	/**
	 * Sets the URL as Url object
	 * 
	 * @param url Request URL object
	 * @return this instance for method chaining
	 */
	public Request withUrl(Url url) {
		this.url = url;
		return this;
	}

	/**
	 * Sets the HTTP method
	 * 
	 * @param method HTTP method enum
	 * @return this instance for method chaining
	 */
	public Request withMethod(RequestMethod method) {
		this.method = method != null ? method : RequestMethod.GET;
		return this;
	}

	/**
	 * Sets the HTTP method from string
	 * 
	 * @param method HTTP method string
	 * @return this instance for method chaining
	 */
	public Request withMethod(String method) {
		this.method = RequestMethod.fromString(method);
		return this;
	}

	/**
	 * Sets the authentication
	 * 
	 * @param auth Authentication configuration
	 * @return this instance for method chaining
	 */
	public Request withAuth(Auth auth) {
		this.auth = auth;
		return this;
	}

	/**
	 * Sets basic authentication
	 * 
	 * @param username Username
	 * @param password Password
	 * @return this instance for method chaining
	 */
	public Request withBasicAuth(String username, String password) {
		this.auth = Auth.basicAuth(username, password);
		return this;
	}

	/**
	 * Sets bearer token authentication
	 * 
	 * @param token Bearer token
	 * @return this instance for method chaining
	 */
	public Request withBearerToken(String token) {
		this.auth = Auth.bearerToken(token);
		return this;
	}

	/**
	 * Sets API key authentication
	 * 
	 * @param key      API key value
	 * @param keyName  API key name
	 * @param location Location (header/query)
	 * @return this instance for method chaining
	 */
	public Request withApiKey(String key, String keyName, String location) {
		this.auth = Auth.apiKey(key, keyName, location);
		return this;
	}

	/**
	 * Sets the proxy configuration
	 * 
	 * @param proxy Proxy configuration
	 * @return this instance for method chaining
	 */
	public Request withProxy(Proxy proxy) {
		this.proxy = proxy;
		return this;
	}

	/**
	 * Sets the proxy with host and port
	 * 
	 * @param host Proxy host
	 * @param port Proxy port
	 * @return this instance for method chaining
	 */
	public Request withProxy(String host, int port) {
		this.proxy = Proxy.create(host, port);
		return this;
	}

	/**
	 * Sets the proxy with host, port and tunnel
	 * 
	 * @param host   Proxy host
	 * @param port   Proxy port
	 * @param tunnel Whether to use tunneling
	 * @return this instance for method chaining
	 */
	public Request withProxy(String host, int port, boolean tunnel) {
		this.proxy = Proxy.create(host, port, tunnel);
		return this;
	}

	/**
	 * Removes proxy configuration
	 * 
	 * @return this instance for method chaining
	 */
	public Request withoutProxy() {
		this.proxy = null;
		return this;
	}

	/**
	 * Sets the certificate for client authentication
	 * 
	 * @param certificate Certificate configuration
	 * @return this instance for method chaining
	 */
	public Request withCertificate(Certificate certificate) {
		this.certificate = certificate;
		return this;
	}

	/**
	 * Sets the certificate with name and matches
	 * 
	 * @param name    Certificate name
	 * @param matches Certificate matches
	 * @return this instance for method chaining
	 */
	public Request withCertificate(String name, List<String> matches) {
		this.certificate = Certificate.create(name, matches);
		return this;
	}

	/**
	 * Removes certificate configuration
	 * 
	 * @return this instance for method chaining
	 */
	public Request withoutCertificate() {
		this.certificate = null;
		return this;
	}

	/**
	 * Adds a header
	 * 
	 * @param header Header to add
	 * @return this instance for method chaining
	 */
	public Request addHeader(Header header) {
		if (this.header == null) {
			this.header = new ArrayList<>();
		}
		this.header.add(header);
		return this;
	}

	/**
	 * Adds a header with key and value
	 * 
	 * @param key   Header key
	 * @param value Header value
	 * @return this instance for method chaining
	 */
	public Request addHeader(String key, String value) {
		return addHeader(Header.of(key, value));
	}

	/**
	 * Adds multiple headers
	 * 
	 * @param headers Headers to add
	 * @return this instance for method chaining
	 */
	public Request addHeaders(Header... headers) {
		if (this.header == null) {
			this.header = new ArrayList<>();
		}
		for (Header h : headers) {
			this.header.add(h);
		}
		return this;
	}

	/**
	 * Adds multiple headers from list
	 * 
	 * @param headers Headers to add
	 * @return this instance for method chaining
	 */
	public Request addHeaders(List<Header> headers) {
		if (headers != null) {
			if (this.header == null) {
				this.header = new ArrayList<>();
			}
			this.header.addAll(headers);
		}
		return this;
	}

	/**
	 * Sets Content-Type header
	 * 
	 * @param contentType Content type value
	 * @return this instance for method chaining
	 */
	public Request withContentType(String contentType) {
		return addHeader("Content-Type", contentType);
	}

	/**
	 * Sets Accept header
	 * 
	 * @param accept Accept value
	 * @return this instance for method chaining
	 */
	public Request withAccept(String accept) {
		return addHeader("Accept", accept);
	}

	/**
	 * Sets User-Agent header
	 * 
	 * @param userAgent User agent value
	 * @return this instance for method chaining
	 */
	public Request withUserAgent(String userAgent) {
		return addHeader("User-Agent", userAgent);
	}

	/**
	 * Sets Authorization header
	 * 
	 * @param authorization Authorization value
	 * @return this instance for method chaining
	 */
	public Request withAuthorization(String authorization) {
		return addHeader("Authorization", authorization);
	}

	/**
	 * Sets the request body
	 * 
	 * @param body Body object
	 * @return this instance for method chaining
	 */
	public Request withBody(Body body) {
		this.body = body;
		return this;
	}

	/**
	 * Sets raw text body
	 * 
	 * @param rawBody Raw body content
	 * @return this instance for method chaining
	 */
	public Request withRawBody(String rawBody) {
		this.body = Body.raw(rawBody);
		return this;
	}

	/**
	 * Sets JSON body
	 * 
	 * @param jsonBody JSON body content
	 * @return this instance for method chaining
	 */
	public Request withJsonBody(String jsonBody) {
		this.body = Body.json(jsonBody);
		return withContentType("application/json");
	}

	/**
	 * Sets XML body
	 * 
	 * @param xmlBody XML body content
	 * @return this instance for method chaining
	 */
	public Request withXmlBody(String xmlBody) {
		this.body = Body.xml(xmlBody);
		return withContentType("application/xml");
	}

	/**
	 * Sets form data body
	 * 
	 * @param formData Form data
	 * @return this instance for method chaining
	 */
	public Request withFormData(List<FormParameter> formData) {
		this.body = Body.formData(formData);
		return this;
	}

	/**
	 * Sets URL encoded body
	 * 
	 * @param urlencoded URL encoded data
	 * @return this instance for method chaining
	 */
	public Request withUrlEncoded(List<UrlEncodedParameter> urlencoded) {
		this.body = Body.urlencoded(urlencoded);
		return withContentType("application/x-www-form-urlencoded");
	}

	/**
	 * Sets the description
	 * 
	 * @param description Description text
	 * @return this instance for method chaining
	 */
	public Request withDescription(String description) {
		this.description = new Description(description);
		return this;
	}

	/**
	 * Sets the description object
	 * 
	 * @param description Description object
	 * @return this instance for method chaining
	 */
	public Request withDescription(Description description) {
		this.description = description;
		return this;
	}

	// Utility methods

	/**
	 * Gets the URL as string
	 * 
	 * @return URL as string
	 */
	public String getUrlAsString() {
		if (url == null) {
			return null;
		}
		if (url instanceof String) {
			return (String) url;
		}
		if (url instanceof Url) {
			return ((Url) url).getRaw();
		}
		return url.toString();
	}

	/**
	 * Gets the URL as Url object
	 * 
	 * @return URL as Url object or null
	 */
	public Url getUrlAsObject() {
		if (url instanceof Url) {
			return (Url) url;
		}
		if (url instanceof String) {
			return Url.create((String) url);
		}
		return null;
	}

	/**
	 * Gets the HTTP method as string
	 * 
	 * @return Method as string
	 */
	public String getMethodAsString() {
		return method != null ? method.getValue() : RequestMethod.GET.getValue();
	}

	/**
	 * Checks if this request has authentication
	 * 
	 * @return true if has authentication
	 */
	public boolean hasAuth() {
		return auth != null;
	}

	/**
	 * Checks if this request has headers
	 * 
	 * @return true if has headers
	 */
	public boolean hasHeaders() {
		return header != null && !header.isEmpty();
	}

	/**
	 * Checks if this request has a body
	 * 
	 * @return true if has body
	 */
	public boolean hasBody() {
		return body != null;
	}

	/**
	 * Checks if this request has a proxy
	 * 
	 * @return true if has proxy
	 */
	public boolean hasProxy() {
		return proxy != null && proxy.isEnabled();
	}

	/**
	 * Checks if this request has a certificate
	 * 
	 * @return true if has certificate
	 */
	public boolean hasCertificate() {
		return certificate != null;
	}

	/**
	 * Checks if this is a GET request
	 * 
	 * @return true if GET method
	 */
	public boolean isGet() {
		return RequestMethod.GET.equals(method);
	}

	/**
	 * Checks if this is a POST request
	 * 
	 * @return true if POST method
	 */
	public boolean isPost() {
		return RequestMethod.POST.equals(method);
	}

	/**
	 * Checks if this is a PUT request
	 * 
	 * @return true if PUT method
	 */
	public boolean isPut() {
		return RequestMethod.PUT.equals(method);
	}

	/**
	 * Checks if this is a DELETE request
	 * 
	 * @return true if DELETE method
	 */
	public boolean isDelete() {
		return RequestMethod.DELETE.equals(method);
	}

	/**
	 * Checks if this is a PATCH request
	 * 
	 * @return true if PATCH method
	 */
	public boolean isPatch() {
		return RequestMethod.PATCH.equals(method);
	}

	/**
	 * Gets a header value by key
	 * 
	 * @param key Header key
	 * @return Header value or null if not found
	 */
	public String getHeaderValue(String key) {
		if (header == null)
			return null;

		return header.stream().filter(h -> key.equalsIgnoreCase(h.getKey())).map(Header::getValue).findFirst()
				.orElse(null);
	}

	/**
	 * Gets the Content-Type header value
	 * 
	 * @return Content-Type value or null
	 */
	public String getContentType() {
		return getHeaderValue("Content-Type");
	}

	/**
	 * Gets the Accept header value
	 * 
	 * @return Accept value or null
	 */
	public String getAccept() {
		return getHeaderValue("Accept");
	}

	/**
	 * Gets the Authorization header value
	 * 
	 * @return Authorization value or null
	 */
	public String getAuthorization() {
		return getHeaderValue("Authorization");
	}

	/**
	 * Removes a header by key
	 * 
	 * @param key Header key to remove
	 * @return this instance for method chaining
	 */
	public Request removeHeader(String key) {
		if (header != null) {
			header.removeIf(h -> key.equalsIgnoreCase(h.getKey()));
		}
		return this;
	}

	/**
	 * Updates or adds a header
	 * 
	 * @param key   Header key
	 * @param value Header value
	 * @return this instance for method chaining
	 */
	public Request setHeader(String key, String value) {
		removeHeader(key);
		return addHeader(key, value);
	}

	/**
	 * Gets the number of headers
	 * 
	 * @return Number of headers
	 */
	public int getHeaderCount() {
		return header != null ? header.size() : 0;
	}

	/**
	 * Creates a copy of this request
	 * 
	 * @return Copy of this request
	 */
	public Request copy() {
		Request.RequestBuilder builder = Request.builder().url(this.url).method(this.method)
				.auth(this.auth != null ? this.auth.copy() : null).body(this.body != null ? this.body.copy() : null)
				.proxy(this.proxy != null ? this.proxy.copy() : null)
				.certificate(this.certificate != null ? this.certificate.copy() : null)
				.description(this.description != null ? this.description.copy() : null);

		// Copy headers
		if (this.header != null) {
			List<Header> copiedHeaders = new ArrayList<>();
			for (Header h : this.header) {
				copiedHeaders.add(h != null ? h.copy() : null);
			}
			builder.header(copiedHeaders);
		}

		return builder.build();
	}

	/**
	 * Validates the request
	 * 
	 * @return true if request is valid
	 */
	public boolean isValid() {
		// Must have a URL
		String urlStr = getUrlAsString();
		if (urlStr == null || urlStr.trim().isEmpty()) {
			return false;
		}

		// Must have a method
		if (method == null) {
			return false;
		}

		return true;
	}

	/**
	 * Gets request summary
	 * 
	 * @return Request summary as string
	 */
	public String getSummary() {
		return String.format(
				"Request: %s %s\n" + "Headers: %d\n" + "Has Body: %s\n" + "Has Auth: %s\n" + "Has Proxy: %s\n"
						+ "Has Certificate: %s\n" + "Content-Type: %s",
				method != null ? method.getValue().toUpperCase() : "UNKNOWN",
				getUrlAsString() != null ? getUrlAsString() : "NO_URL", getHeaderCount(), hasBody() ? "Yes" : "No",
				hasAuth() ? "Yes" : "No", hasProxy() ? "Yes" : "No", hasCertificate() ? "Yes" : "No",
				getContentType() != null ? getContentType() : "Not set");
	}

	/**
	 * Clears all headers
	 * 
	 * @return this instance for method chaining
	 */
	public Request clearHeaders() {
		if (this.header != null) {
			this.header.clear();
		}
		return this;
	}

	/**
	 * Clears authentication
	 * 
	 * @return this instance for method chaining
	 */
	public Request clearAuth() {
		this.auth = null;
		return this;
	}

	/**
	 * Clears body
	 * 
	 * @return this instance for method chaining
	 */
	public Request clearBody() {
		this.body = null;
		return this;
	}

	/**
	 * Checks if request has description
	 * 
	 * @return true if has description
	 */
	public boolean hasDescription() {
		return description != null;
	}

	/**
	 * Gets all header keys
	 * 
	 * @return List of header keys
	 */
	public List<String> getHeaderKeys() {
		if (header == null) {
			return new ArrayList<>();
		}
		return header.stream().map(Header::getKey).filter(key -> key != null).toList();
	}

	/**
	 * Checks if header exists
	 * 
	 * @param key Header key
	 * @return true if header exists
	 */
	public boolean hasHeader(String key) {
		return getHeaderValue(key) != null;
	}

	/**
	 * Gets header by key
	 * 
	 * @param key Header key
	 * @return Header object or null if not found
	 */
	public Header getHeader(String key) {
		if (header == null || key == null) {
			return null;
		}

		return header.stream().filter(h -> key.equalsIgnoreCase(h.getKey())).findFirst().orElse(null);
	}

	/**
	 * Gets all headers with a specific key (case-insensitive)
	 * 
	 * @param key Header key
	 * @return List of headers with the key
	 */
	public List<Header> getHeaders(String key) {
		if (header == null || key == null) {
			return new ArrayList<>();
		}

		return header.stream().filter(h -> key.equalsIgnoreCase(h.getKey())).toList();
	}

	/**
	 * Sets default values for missing fields
	 * 
	 * @return this instance for method chaining
	 */
	public Request withDefaults() {
		if (this.method == null) {
			this.method = RequestMethod.GET;
		}

		if (this.header == null) {
			this.header = new ArrayList<>();
		}

		return this;
	}

	/**
	 * Converts to string representation
	 */
	@Override
	public String toString() {
		return String.format("%s %s", method != null ? method.getValue().toUpperCase() : "UNKNOWN",
				getUrlAsString() != null ? getUrlAsString() : "NO_URL");
	}

	// Static utility methods

	/**
	 * Creates a SOAP request
	 * 
	 * @param url        SOAP endpoint URL
	 * @param soapAction SOAP action
	 * @param soapBody   SOAP request body
	 * @return SOAP request
	 */
	public static Request soap(String url, String soapAction, String soapBody) {
		return Request.post(url).withContentType("text/xml; charset=utf-8")
				.addHeader("SOAPAction", soapAction != null ? soapAction : "").withRawBody(soapBody);
	}

	/**
	 * Creates a JSON request
	 * 
	 * @param method HTTP method enum
	 * @param url    Request URL
	 * @param json   JSON body
	 * @return JSON request
	 */
	public static Request json(RequestMethod method, String url, String json) {
		return Request.of(method, url).withJsonBody(json);
	}

	/**
	 * Creates a JSON request with string method
	 * 
	 * @param method HTTP method string
	 * @param url    Request URL
	 * @param json   JSON body
	 * @return JSON request
	 */
	public static Request json(String method, String url, String json) {
		return Request.of(method, url).withJsonBody(json);
	}

	/**
	 * Creates a form request
	 * 
	 * @param url      Request URL
	 * @param formData Form data
	 * @return Form request
	 */
	public static Request form(String url, List<FormParameter> formData) {
		return Request.post(url).withFormData(formData);
	}

	/**
	 * Creates a URL encoded request
	 * 
	 * @param url        Request URL
	 * @param urlencoded URL encoded data
	 * @return URL encoded request
	 */
	public static Request urlencoded(String url, List<UrlEncodedParameter> urlencoded) {
		return Request.post(url).withUrlEncoded(urlencoded);
	}

	/**
	 * Creates a request with basic authentication
	 * 
	 * @param method   HTTP method enum
	 * @param url      Request URL
	 * @param username Username
	 * @param password Password
	 * @return Request with basic auth
	 */
	public static Request withBasicAuth(RequestMethod method, String url, String username, String password) {
		return Request.of(method, url).withBasicAuth(username, password);
	}

	/**
	 * Creates a request with basic authentication (string method)
	 * 
	 * @param method   HTTP method string
	 * @param url      Request URL
	 * @param username Username
	 * @param password Password
	 * @return Request with basic auth
	 */
	public static Request withBasicAuth(String method, String url, String username, String password) {
		return Request.of(method, url).withBasicAuth(username, password);
	}

	/**
	 * Creates a request with bearer token
	 * 
	 * @param method HTTP method enum
	 * @param url    Request URL
	 * @param token  Bearer token
	 * @return Request with bearer token
	 */
	public static Request withBearerToken(RequestMethod method, String url, String token) {
		return Request.of(method, url).withBearerToken(token);
	}

	/**
	 * Creates a request with bearer token (string method)
	 * 
	 * @param method HTTP method string
	 * @param url    Request URL
	 * @param token  Bearer token
	 * @return Request with bearer token
	 */
	public static Request withBearerToken(String method, String url, String token) {
		return Request.of(method, url).withBearerToken(token);
	}

	/**
	 * Validates a list of requests
	 * 
	 * @param requests List of requests to validate
	 * @return List of validation errors
	 */
	public static List<String> validateRequests(List<Request> requests) {
		List<String> errors = new ArrayList<>();

		if (requests == null) {
			errors.add("Requests list is null");
			return errors;
		}

		for (int i = 0; i < requests.size(); i++) {
			Request request = requests.get(i);
			if (request == null) {
				errors.add("Request at index " + i + " is null");
				continue;
			}

			if (!request.isValid()) {
				errors.add("Request at index " + i + " is invalid: " + request.toString());
			}
		}

		return errors;
	}
}