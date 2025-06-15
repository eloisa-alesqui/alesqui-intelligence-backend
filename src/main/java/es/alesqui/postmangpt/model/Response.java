package es.alesqui.postmangpt.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

import org.springframework.data.mongodb.core.mapping.Field;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;

/**
 * A response represents an HTTP response.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Response {

	/**
	 * A unique, user defined identifier that can be used to refer to this response
	 * from requests.
	 */
	@Field("id")
	@JsonProperty("id")
	private String id;

	/**
	 * The name of the response
	 */
	@Field("name")
	@JsonProperty("name")
	private String name;

	/**
	 * The raw text of the response.
	 */
	@Field("body")
	@JsonProperty("body")
	private String body;

	/**
	 * The HTTP response code.
	 */
	@Field("code")
	@JsonProperty("code")
	private Integer code;

	/**
	 * No HTTP request is complete without its headers, and the same is true for a
	 * Postman request. This field is an array containing all the headers.
	 */
	@Field("header")
	@JsonProperty("header")
	@Builder.Default
	private List<Header> header = new ArrayList<>();

	/**
	 * A list of cookies which were set in response to this request.
	 */
	@Field("cookie")
	@JsonProperty("cookie")
	@Builder.Default
	private List<Cookie> cookie = new ArrayList<>();

	/**
	 * The time taken by the request to complete. If a number, the unit is
	 * milliseconds.
	 */
	@Field("responseTime")
	@JsonProperty("responseTime")
	private String responseTime;

	/**
	 * The response status, e.g. '200 OK'
	 */
	@Field("status")
	@JsonProperty("status")
	private String status;

	/**
	 * The original request that this response is associated with
	 */
	@Field("originalRequest")
	@JsonProperty("originalRequest")
	private Request originalRequest;

	/**
	 * Constructor for basic response with status code
	 * 
	 * @param code HTTP status code
	 */
	public Response(Integer code) {
		this.code = code;
		this.header = new ArrayList<>();
		this.cookie = new ArrayList<>();
	}

	/**
	 * Constructor for response with code and body
	 * 
	 * @param code HTTP status code
	 * @param body Response body
	 */
	public Response(Integer code, String body) {
		this.code = code;
		this.body = body;
		this.header = new ArrayList<>();
		this.cookie = new ArrayList<>();
	}

	/**
	 * Constructor for response with code, status and body
	 * 
	 * @param code   HTTP status code
	 * @param status HTTP status text
	 * @param body   Response body
	 */
	public Response(Integer code, String status, String body) {
		this.code = code;
		this.status = status;
		this.body = body;
		this.header = new ArrayList<>();
		this.cookie = new ArrayList<>();
	}

	// Factory methods for common HTTP responses

	/**
	 * Creates a 200 OK response
	 * 
	 * @return Response instance
	 */
	public static Response ok() {
		return new Response(200, "OK", null);
	}

	/**
	 * Creates a 200 OK response with body
	 * 
	 * @param body Response body
	 * @return Response instance
	 */
	public static Response ok(String body) {
		return new Response(200, "OK", body);
	}

	/**
	 * Creates a 201 Created response
	 * 
	 * @return Response instance
	 */
	public static Response created() {
		return new Response(201, "Created", null);
	}

	/**
	 * Creates a 201 Created response with body
	 * 
	 * @param body Response body
	 * @return Response instance
	 */
	public static Response created(String body) {
		return new Response(201, "Created", body);
	}

	/**
	 * Creates a 204 No Content response
	 * 
	 * @return Response instance
	 */
	public static Response noContent() {
		return new Response(204, "No Content", null);
	}

	/**
	 * Creates a 400 Bad Request response
	 * 
	 * @return Response instance
	 */
	public static Response badRequest() {
		return new Response(400, "Bad Request", null);
	}

	/**
	 * Creates a 400 Bad Request response with body
	 * 
	 * @param body Response body
	 * @return Response instance
	 */
	public static Response badRequest(String body) {
		return new Response(400, "Bad Request", body);
	}

	/**
	 * Creates a 401 Unauthorized response
	 * 
	 * @return Response instance
	 */
	public static Response unauthorized() {
		return new Response(401, "Unauthorized", null);
	}

	/**
	 * Creates a 403 Forbidden response
	 * 
	 * @return Response instance
	 */
	public static Response forbidden() {
		return new Response(403, "Forbidden", null);
	}

	/**
	 * Creates a 404 Not Found response
	 * 
	 * @return Response instance
	 */
	public static Response notFound() {
		return new Response(404, "Not Found", null);
	}

	/**
	 * Creates a 500 Internal Server Error response
	 * 
	 * @return Response instance
	 */
	public static Response internalServerError() {
		return new Response(500, "Internal Server Error", null);
	}

	/**
	 * Creates a response with custom status
	 * 
	 * @param code   HTTP status code
	 * @param status HTTP status text
	 * @return Response instance
	 */
	public static Response of(Integer code, String status) {
		return new Response(code, status, null);
	}

	/**
	 * Creates a response with custom status and body
	 * 
	 * @param code   HTTP status code
	 * @param status HTTP status text
	 * @param body   Response body
	 * @return Response instance
	 */
	public static Response of(Integer code, String status, String body) {
		return new Response(code, status, body);
	}

	// Fluent API methods

	/**
	 * Sets the response name
	 * 
	 * @param name Response name
	 * @return this instance for method chaining
	 */
	public Response withName(String name) {
		this.name = name;
		return this;
	}

	/**
	 * Sets the response ID
	 * 
	 * @param id Response ID
	 * @return this instance for method chaining
	 */
	public Response withId(String id) {
		this.id = id;
		return this;
	}

	/**
	 * Sets the response body
	 * 
	 * @param body Response body
	 * @return this instance for method chaining
	 */
	public Response withBody(String body) {
		this.body = body;
		return this;
	}

	/**
	 * Sets the response time
	 * 
	 * @param responseTime Response time
	 * @return this instance for method chaining
	 */
	public Response withResponseTime(String responseTime) {
		this.responseTime = responseTime;
		return this;
	}

	/**
	 * Sets the response time in milliseconds
	 * 
	 * @param milliseconds Response time in milliseconds
	 * @return this instance for method chaining
	 */
	public Response withResponseTime(long milliseconds) {
		this.responseTime = milliseconds + "ms";
		return this;
	}

	/**
	 * Sets the original request
	 * 
	 * @param originalRequest Original request
	 * @return this instance for method chaining
	 */
	public Response withOriginalRequest(Request originalRequest) {
		this.originalRequest = originalRequest;
		return this;
	}

	/**
	 * Adds a header to the response
	 * 
	 * @param header Header to add
	 * @return this instance for method chaining
	 */
	public Response addHeader(Header header) {
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
	public Response addHeader(String key, String value) {
		return addHeader(Header.of(key, value));
	}

	/**
	 * Adds multiple headers
	 * 
	 * @param headers Headers to add
	 * @return this instance for method chaining
	 */
	public Response addHeaders(Header... headers) {
		if (this.header == null) {
			this.header = new ArrayList<>();
		}
		for (Header h : headers) {
			this.header.add(h);
		}
		return this;
	}

	/**
	 * Adds a cookie to the response
	 * 
	 * @param cookie Cookie to add
	 * @return this instance for method chaining
	 */
	public Response addCookie(Cookie cookie) {
		if (this.cookie == null) {
			this.cookie = new ArrayList<>();
		}
		this.cookie.add(cookie);
		return this;
	}

	/**
	 * Creates a cookie with name and value (default domain and path for compatibility)
	 * Note: Uses empty domain and "/" path to satisfy Postman schema requirements
	 * 
	 * @param name  Cookie name
	 * @param value Cookie value
	 * @return Cookie instance
	 */
	public static Cookie of(String name, String value) {
	    return new Cookie(name, value, "", "/");
	}

	// Common response headers

	/**
	 * Sets Content-Type header
	 * 
	 * @param contentType Content type
	 * @return this instance for method chaining
	 */
	public Response withContentType(String contentType) {
		return addHeader("Content-Type", contentType);
	}

	/**
	 * Sets JSON content type
	 * 
	 * @return this instance for method chaining
	 */
	public Response withJsonContentType() {
		return withContentType("application/json");
	}

	/**
	 * Sets XML content type
	 * 
	 * @return this instance for method chaining
	 */
	public Response withXmlContentType() {
		return withContentType("application/xml");
	}

	/**
	 * Sets text content type
	 * 
	 * @return this instance for method chaining
	 */
	public Response withTextContentType() {
		return withContentType("text/plain");
	}

	/**
	 * Sets HTML content type
	 * 
	 * @return this instance for method chaining
	 */
	public Response withHtmlContentType() {
		return withContentType("text/html");
	}

	// Utility methods

	/**
	 * Checks if the response is successful (2xx status codes)
	 * 
	 * @return true if successful
	 */
	public boolean isSuccessful() {
		return code != null && code >= 200 && code < 300;
	}

	/**
	 * Checks if the response is a client error (4xx status codes)
	 * 
	 * @return true if client error
	 */
	public boolean isClientError() {
		return code != null && code >= 400 && code < 500;
	}

	/**
	 * Checks if the response is a server error (5xx status codes)
	 * 
	 * @return true if server error
	 */
	public boolean isServerError() {
		return code != null && code >= 500 && code < 600;
	}

	/**
	 * Checks if the response is an error (4xx or 5xx status codes)
	 * 
	 * @return true if error
	 */
	public boolean isError() {
		return isClientError() || isServerError();
	}

	/**
	 * Checks if the response has a body
	 * 
	 * @return true if has body
	 */
	public boolean hasBody() {
		return body != null && !body.trim().isEmpty();
	}

	/**
	 * Checks if the response has headers
	 * 
	 * @return true if has headers
	 */
	public boolean hasHeaders() {
		return header != null && !header.isEmpty();
	}

	/**
	 * Checks if the response has cookies
	 * 
	 * @return true if has cookies
	 */
	public boolean hasCookies() {
		return cookie != null && !cookie.isEmpty();
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
	 * Gets the Content-Length header value
	 * 
	 * @return Content-Length value or null
	 */
	public String getContentLength() {
		return getHeaderValue("Content-Length");
	}

	/**
	 * Gets the Server header value
	 * 
	 * @return Server value or null
	 */
	public String getServer() {
		return getHeaderValue("Server");
	}

	/**
	 * Gets a cookie by name
	 * 
	 * @param name Cookie name
	 * @return Cookie or null if not found
	 */
	public Cookie getCookie(String name) {
		if (cookie == null)
			return null;

		return cookie.stream().filter(c -> name.equals(c.getName())).findFirst().orElse(null);
	}

	/**
	 * Gets a cookie value by name
	 * 
	 * @param name Cookie name
	 * @return Cookie value or null if not found
	 */
	public String getCookieValue(String name) {
		Cookie c = getCookie(name);
		return c != null ? c.getValue() : null;
	}

	/**
	 * Gets the response time as long (in milliseconds)
	 * 
	 * @return Response time in milliseconds or null if not parseable
	 */
	public Long getResponseTimeMs() {
		if (responseTime == null)
			return null;

		try {
			// Remove 'ms' suffix if present
			String timeStr = responseTime.replaceAll("\\s*ms\\s*$", "");
			return Long.parseLong(timeStr);
		} catch (NumberFormatException e) {
			return null;
		}
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
	 * Gets the number of cookies
	 * 
	 * @return Number of cookies
	 */
	public int getCookieCount() {
		return cookie != null ? cookie.size() : 0;
	}

	/**
	 * Gets the body size (approximate)
	 * 
	 * @return Body size in characters or 0 if no body
	 */
	public int getBodySize() {
		return body != null ? body.length() : 0;
	}

	/**
	 * Checks if the response content type is JSON
	 * 
	 * @return true if JSON content type
	 */
	public boolean isJson() {
		String contentType = getContentType();
		return contentType != null && contentType.toLowerCase().contains("application/json");
	}

	/**
	 * Checks if the response content type is XML
	 * 
	 * @return true if XML content type
	 */
	public boolean isXml() {
		String contentType = getContentType();
		return contentType != null && (contentType.toLowerCase().contains("application/xml")
				|| contentType.toLowerCase().contains("text/xml"));
	}

	/**
	 * Checks if the response content type is HTML
	 * 
	 * @return true if HTML content type
	 */
	public boolean isHtml() {
		String contentType = getContentType();
		return contentType != null && contentType.toLowerCase().contains("text/html");
	}

	/**
	 * Gets a summary of the response
	 * 
	 * @return Response summary string
	 */
	public String getSummary() {
		StringBuilder sb = new StringBuilder();

		if (code != null) {
			sb.append(code);
		}

		if (status != null) {
			if (sb.length() > 0)
				sb.append(" ");
			sb.append(status);
		}

		if (responseTime != null) {
			if (sb.length() > 0)
				sb.append(" - ");
			sb.append(responseTime);
		}

		if (hasBody()) {
			if (sb.length() > 0)
				sb.append(" - ");
			sb.append(getBodySize()).append(" chars");
		}

		return sb.toString();
	}

	/**
	 * Creates a copy of this response
	 * 
	 * @return Copy of this response
	 */
	public Response copy() {
		return Response.builder().id(this.id).name(this.name).body(this.body).code(this.code)
				.header(this.header != null
						? this.header.stream().map(Header::copy).collect(java.util.stream.Collectors.toList())
						: new ArrayList<>())
				.cookie(this.cookie != null
						? this.cookie.stream().map(Cookie::copy).collect(java.util.stream.Collectors.toList())
						: new ArrayList<>())
				.responseTime(this.responseTime).status(this.status)
				.originalRequest(this.originalRequest != null ? this.originalRequest.copy() : null).build();
	}

	/**
	 * Creates a copy with new ID
	 * 
	 * @param newId The new ID for the copy
	 * @return Copy of this response with new ID
	 */
	public Response copyWithId(String newId) {
		Response copy = copy();
		copy.id = newId;
		return copy;
	}

	/**
	 * Creates a copy with new name
	 * 
	 * @param newName The new name for the copy
	 * @return Copy of this response with new name
	 */
	public Response copyWithName(String newName) {
		Response copy = copy();
		copy.name = newName;
		return copy;
	}

	/**
	 * Creates a copy with new body
	 * 
	 * @param newBody The new body for the copy
	 * @return Copy of this response with new body
	 */
	public Response copyWithBody(String newBody) {
		Response copy = copy();
		copy.body = newBody;
		return copy;
	}

	/**
	 * Creates a copy with new status code
	 * 
	 * @param newCode The new status code for the copy
	 * @return Copy of this response with new status code
	 */
	public Response copyWithCode(Integer newCode) {
		Response copy = copy();
		copy.code = newCode;
		return copy;
	}

	/**
	 * Creates a copy with new status text
	 * 
	 * @param newStatus The new status text for the copy
	 * @return Copy of this response with new status text
	 */
	public Response copyWithStatus(String newStatus) {
		Response copy = copy();
		copy.status = newStatus;
		return copy;
	}

	/**
	 * Creates a copy with new status code and text
	 * 
	 * @param newCode   The new status code
	 * @param newStatus The new status text
	 * @return Copy of this response with new status
	 */
	public Response copyWithStatus(Integer newCode, String newStatus) {
		Response copy = copy();
		copy.code = newCode;
		copy.status = newStatus;
		return copy;
	}

	/**
	 * Creates a copy with new response time
	 * 
	 * @param newResponseTime The new response time
	 * @return Copy of this response with new response time
	 */
	public Response copyWithResponseTime(String newResponseTime) {
		Response copy = copy();
		copy.responseTime = newResponseTime;
		return copy;
	}

	/**
	 * Creates a copy with new response time in milliseconds
	 * 
	 * @param milliseconds The new response time in milliseconds
	 * @return Copy of this response with new response time
	 */
	public Response copyWithResponseTime(long milliseconds) {
		Response copy = copy();
		copy.responseTime = milliseconds + "ms";
		return copy;
	}

	/**
	 * Creates a copy with new original request
	 * 
	 * @param newOriginalRequest The new original request
	 * @return Copy of this response with new original request
	 */
	public Response copyWithOriginalRequest(Request newOriginalRequest) {
		Response copy = copy();
		copy.originalRequest = newOriginalRequest != null ? newOriginalRequest.copy() : null;
		return copy;
	}

	/**
	 * Creates a copy with new headers
	 * 
	 * @param newHeaders The new headers for the copy
	 * @return Copy of this response with new headers
	 */
	public Response copyWithHeaders(List<Header> newHeaders) {
		Response copy = copy();
		copy.header = newHeaders != null
				? newHeaders.stream().map(Header::copy).collect(java.util.stream.Collectors.toList())
				: new ArrayList<>();
		return copy;
	}

	/**
	 * Creates a copy with new cookies
	 * 
	 * @param newCookies The new cookies for the copy
	 * @return Copy of this response with new cookies
	 */
	public Response copyWithCookies(List<Cookie> newCookies) {
		Response copy = copy();
		copy.cookie = newCookies != null
				? newCookies.stream().map(Cookie::copy).collect(java.util.stream.Collectors.toList())
				: new ArrayList<>();
		return copy;
	}

	/**
	 * Creates a copy with additional header
	 * 
	 * @param newHeader Header to add
	 * @return Copy of this response with additional header
	 */
	public Response copyWithAddedHeader(Header newHeader) {
		Response copy = copy();
		if (newHeader != null) {
			copy.addHeader(newHeader.copy());
		}
		return copy;
	}

	/**
	 * Creates a copy with additional header
	 * 
	 * @param key   Header key
	 * @param value Header value
	 * @return Copy of this response with additional header
	 */
	public Response copyWithAddedHeader(String key, String value) {
		Response copy = copy();
		copy.addHeader(key, value);
		return copy;
	}

	/**
	 * Creates a copy with additional cookie
	 * 
	 * @param newCookie Cookie to add
	 * @return Copy of this response with additional cookie
	 */
	public Response copyWithAddedCookie(Cookie newCookie) {
		Response copy = copy();
		if (newCookie != null) {
			copy.addCookie(newCookie.copy());
		}
		return copy;
	}

	/**
	 * Creates a copy with additional cookie
	 * 
	 * @param name  Cookie name
	 * @param value Cookie value
	 * @return Copy of this response with additional cookie
	 */
	public Response copyWithAddedCookie(String name, String value) {
		Response copy = copy();
		copy.addCookie(name, value);
		return copy;
	}
	
	/**
	 * Adds a cookie with name and value
	 * 
	 * @param name  Cookie name
	 * @param value Cookie value
	 * @return this instance for method chaining
	 */
	public Response addCookie(String name, String value) {
	    return addCookie(Cookie.of(name, value, "", "/"));
	}

	/**
	 * Creates a copy without headers
	 * 
	 * @return Copy of this response without headers
	 */
	public Response copyWithoutHeaders() {
		Response copy = copy();
		copy.header = new ArrayList<>();
		return copy;
	}

	/**
	 * Creates a copy without cookies
	 * 
	 * @return Copy of this response without cookies
	 */
	public Response copyWithoutCookies() {
		Response copy = copy();
		copy.cookie = new ArrayList<>();
		return copy;
	}

	/**
	 * Creates a copy without body
	 * 
	 * @return Copy of this response without body
	 */
	public Response copyWithoutBody() {
		Response copy = copy();
		copy.body = null;
		return copy;
	}

	/**
	 * Creates a copy without original request
	 * 
	 * @return Copy of this response without original request
	 */
	public Response copyWithoutOriginalRequest() {
		Response copy = copy();
		copy.originalRequest = null;
		return copy;
	}

	// Factory method copies for common HTTP responses

	/**
	 * Creates a copy as 200 OK response
	 * 
	 * @return Copy as 200 OK response
	 */
	public Response copyAsOk() {
		return copyWithStatus(200, "OK");
	}

	/**
	 * Creates a copy as 200 OK response with new body
	 * 
	 * @param newBody New response body
	 * @return Copy as 200 OK response with new body
	 */
	public Response copyAsOk(String newBody) {
		Response copy = copyWithStatus(200, "OK");
		copy.body = newBody;
		return copy;
	}

	/**
	 * Creates a copy as 201 Created response
	 * 
	 * @return Copy as 201 Created response
	 */
	public Response copyAsCreated() {
		return copyWithStatus(201, "Created");
	}

	/**
	 * Creates a copy as 201 Created response with new body
	 * 
	 * @param newBody New response body
	 * @return Copy as 201 Created response with new body
	 */
	public Response copyAsCreated(String newBody) {
		Response copy = copyWithStatus(201, "Created");
		copy.body = newBody;
		return copy;
	}

	/**
	 * Creates a copy as 204 No Content response
	 * 
	 * @return Copy as 204 No Content response
	 */
	public Response copyAsNoContent() {
		Response copy = copyWithStatus(204, "No Content");
		copy.body = null;
		return copy;
	}

	/**
	 * Creates a copy as 400 Bad Request response
	 * 
	 * @return Copy as 400 Bad Request response
	 */
	public Response copyAsBadRequest() {
		return copyWithStatus(400, "Bad Request");
	}

	/**
	 * Creates a copy as 400 Bad Request response with new body
	 * 
	 * @param newBody New response body
	 * @return Copy as 400 Bad Request response with new body
	 */
	public Response copyAsBadRequest(String newBody) {
		Response copy = copyWithStatus(400, "Bad Request");
		copy.body = newBody;
		return copy;
	}

	/**
	 * Creates a copy as 401 Unauthorized response
	 * 
	 * @return Copy as 401 Unauthorized response
	 */
	public Response copyAsUnauthorized() {
		return copyWithStatus(401, "Unauthorized");
	}

	/**
	 * Creates a copy as 403 Forbidden response
	 * 
	 * @return Copy as 403 Forbidden response
	 */
	public Response copyAsForbidden() {
		return copyWithStatus(403, "Forbidden");
	}

	/**
	 * Creates a copy as 404 Not Found response
	 * 
	 * @return Copy as 404 Not Found response
	 */
	public Response copyAsNotFound() {
		return copyWithStatus(404, "Not Found");
	}

	/**
	 * Creates a copy as 500 Internal Server Error response
	 * 
	 * @return Copy as 500 Internal Server Error response
	 */
	public Response copyAsInternalServerError() {
		return copyWithStatus(500, "Internal Server Error");
	}

	/**
	 * Creates a copy with JSON content type
	 * 
	 * @return Copy with JSON content type
	 */
	public Response copyWithJsonContentType() {
		return copyWithAddedHeader("Content-Type", "application/json");
	}

	/**
	 * Creates a copy with XML content type
	 * 
	 * @return Copy with XML content type
	 */
	public Response copyWithXmlContentType() {
		return copyWithAddedHeader("Content-Type", "application/xml");
	}

	/**
	 * Creates a copy with text content type
	 * 
	 * @return Copy with text content type
	 */
	public Response copyWithTextContentType() {
		return copyWithAddedHeader("Content-Type", "text/plain");
	}

	/**
	 * Creates a copy with HTML content type
	 * 
	 * @return Copy with HTML content type
	 */
	public Response copyWithHtmlContentType() {
		return copyWithAddedHeader("Content-Type", "text/html");
	}

	/**
	 * Creates a copy with custom content type
	 * 
	 * @param contentType Content type to set
	 * @return Copy with custom content type
	 */
	public Response copyWithContentType(String contentType) {
		return copyWithAddedHeader("Content-Type", contentType);
	}

}