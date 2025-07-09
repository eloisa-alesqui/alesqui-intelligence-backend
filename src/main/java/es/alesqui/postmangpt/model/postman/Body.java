package es.alesqui.postmangpt.model.postman;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

import org.springframework.data.mongodb.core.mapping.Field;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import es.alesqui.postmangpt.model.postman.enums.BodyMode;

import java.util.ArrayList;

/**
 * This field contains the data usually contained in the request body.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Body {

	/**
	 * Postman stores the type of data associated with this request in this field.
	 */
	@Field("mode")           
	@JsonProperty("mode")
	private String mode;

	/**
	 * Raw body content (for raw mode)
	 */
	@Field("raw")           
	@JsonProperty("raw")
	private String raw;

	/**
	 * GraphQL query (for graphql mode)
	 */
	@Field("graphql")           
	@JsonProperty("graphql")
	private GraphQLBody graphql;

	/**
	 * Form data parameters (for formdata mode)
	 */
	@Field("formdata")           
	@JsonProperty("formdata")
	@Builder.Default
	private List<FormParameter> formdata = new ArrayList<>();

	/**
	 * URL encoded parameters (for urlencoded mode)
	 */
	@Field("urlencoded")           
	@JsonProperty("urlencoded")
	@Builder.Default
	private List<UrlEncodedParameter> urlencoded = new ArrayList<>();

	/**
	 * File information (for file mode)
	 */
	@Field("file")           
	@JsonProperty("file")
	private FileBody file;

	/**
	 * Body options
	 */
	@Field("options")           
	@JsonProperty("options")
	private BodyOptions options;

	/**
	 * Indicates whether the body is disabled
	 */
	@Field("disabled")           
	@JsonProperty("disabled")
	@Builder.Default
	private Boolean disabled = false;

	/**
	 * Constructor for raw body
	 * 
	 * @param raw Raw content
	 */
	public Body(String raw) {
		this.mode = BodyMode.RAW.getValue();
		this.raw = raw;
		this.disabled = false;
	}

	/**
	 * Constructor with mode and raw content
	 * 
	 * @param mode Body mode
	 * @param raw  Raw content
	 */
	public Body(BodyMode mode, String raw) {
		this.mode = mode.getValue();
		this.raw = raw;
		this.disabled = false;
	}

	// Factory methods for different body types

	/**
	 * Creates a raw body
	 * 
	 * @param content Raw content
	 * @return Body instance
	 */
	public static Body raw(String content) {
		return new Body(BodyMode.RAW, content);
	}

	/**
	 * Creates a JSON body
	 * 
	 * @param json JSON content
	 * @return Body instance with JSON
	 */
	public static Body json(String json) {
		Body body = new Body(BodyMode.RAW, json);
		body.options = BodyOptions.json();
		return body;
	}

	/**
	 * Creates an XML body
	 * 
	 * @param xml XML content
	 * @return Body instance with XML
	 */
	public static Body xml(String xml) {
		Body body = new Body(BodyMode.RAW, xml);
		body.options = BodyOptions.xml();
		return body;
	}

	/**
	 * Creates a text body
	 * 
	 * @param text Text content
	 * @return Body instance with text
	 */
	public static Body text(String text) {
		Body body = new Body(BodyMode.RAW, text);
		body.options = BodyOptions.text();
		return body;
	}

	/**
	 * Creates an HTML body
	 * 
	 * @param html HTML content
	 * @return Body instance with HTML
	 */
	public static Body html(String html) {
		Body body = new Body(BodyMode.RAW, html);
		body.options = BodyOptions.html();
		return body;
	}

	/**
	 * Creates a JavaScript body
	 * 
	 * @param javascript JavaScript content
	 * @return Body instance with JavaScript
	 */
	public static Body javascript(String javascript) {
		Body body = new Body(BodyMode.RAW, javascript);
		body.options = BodyOptions.javascript();
		return body;
	}

	/**
	 * Creates a form-data body
	 * 
	 * @param formData List of form parameters
	 * @return Body instance with form data
	 */
	public static Body formData(List<FormParameter> formData) {
		Body body = new Body();
		body.mode = BodyMode.FORMDATA.getValue();
		body.formdata = formData != null ? new ArrayList<>(formData) : new ArrayList<>();
		return body;
	}

	/**
	 * Creates a form-data body with parameters
	 * 
	 * @param parameters Form parameters
	 * @return Body instance with form data
	 */
	public static Body formData(FormParameter... parameters) {
		Body body = new Body();
		body.mode = BodyMode.FORMDATA.getValue();
		body.formdata = new ArrayList<>();
		for (FormParameter param : parameters) {
			body.formdata.add(param);
		}
		return body;
	}

	/**
	 * Creates a URL encoded body
	 * 
	 * @param urlEncodedData List of URL encoded parameters
	 * @return Body instance with URL encoded data
	 */
	public static Body urlencoded(List<UrlEncodedParameter> urlEncodedData) {
		Body body = new Body();
		body.mode = BodyMode.URLENCODED.getValue();
		body.urlencoded = urlEncodedData != null ? new ArrayList<>(urlEncodedData) : new ArrayList<>();
		return body;
	}

	/**
	 * Creates a URL encoded body with parameters
	 * 
	 * @param parameters URL encoded parameters
	 * @return Body instance with URL encoded data
	 */
	public static Body urlencoded(UrlEncodedParameter... parameters) {
		Body body = new Body();
		body.mode = BodyMode.URLENCODED.getValue();
		body.urlencoded = new ArrayList<>();
		for (UrlEncodedParameter param : parameters) {
			body.urlencoded.add(param);
		}
		return body;
	}

	/**
	 * Creates a binary body
	 * 
	 * @return Body instance for binary data
	 */
	public static Body binary() {
		Body body = new Body();
		body.mode = BodyMode.BINARY.getValue();
		return body;
	}

	/**
	 * Creates a file body
	 * 
	 * @param src File source
	 * @return Body instance with file
	 */
	public static Body file(String src) {
		Body body = new Body();
		body.mode = BodyMode.FILE.getValue();
		body.file = new FileBody(src);
		return body;
	}

	/**
	 * Creates a GraphQL body
	 * 
	 * @param query GraphQL query
	 * @return Body instance with GraphQL
	 */
	public static Body graphql(String query) {
		Body body = new Body();
		body.mode = BodyMode.GRAPHQL.getValue();
		body.graphql = new GraphQLBody(query);
		return body;
	}

	/**
	 * Creates a GraphQL body with variables
	 * 
	 * @param query     GraphQL query
	 * @param variables GraphQL variables
	 * @return Body instance with GraphQL
	 */
	public static Body graphql(String query, String variables) {
		Body body = new Body();
		body.mode = BodyMode.GRAPHQL.getValue();
		body.graphql = new GraphQLBody(query, variables);
		return body;
	}

	// Fluent API methods

	/**
	 * Sets the body as disabled
	 * 
	 * @return this instance for method chaining
	 */
	public Body disable() {
		this.disabled = true;
		return this;
	}

	/**
	 * Sets the body as enabled
	 * 
	 * @return this instance for method chaining
	 */
	public Body enable() {
		this.disabled = false;
		return this;
	}

	/**
	 * Sets the body options
	 * 
	 * @param options Body options
	 * @return this instance for method chaining
	 */
	public Body withOptions(BodyOptions options) {
		this.options = options;
		return this;
	}

	/**
	 * Adds a form data parameter
	 * 
	 * @param parameter Form parameter to add
	 * @return this instance for method chaining
	 */
	public Body addFormData(FormParameter parameter) {
		if (this.formdata == null) {
			this.formdata = new ArrayList<>();
		}
		this.formdata.add(parameter);
		return this;
	}

	/**
	 * Adds a form data parameter with key and value
	 * 
	 * @param key   Parameter key
	 * @param value Parameter value
	 * @return this instance for method chaining
	 */
	public Body addFormData(String key, String value) {
		return addFormData(FormParameter.of(key, value));
	}

	/**
	 * Adds a URL encoded parameter
	 * 
	 * @param parameter URL encoded parameter to add
	 * @return this instance for method chaining
	 */
	public Body addUrlEncoded(UrlEncodedParameter parameter) {
		if (this.urlencoded == null) {
			this.urlencoded = new ArrayList<>();
		}
		this.urlencoded.add(parameter);
		return this;
	}

	/**
	 * Adds a URL encoded parameter with key and value
	 * 
	 * @param key   Parameter key
	 * @param value Parameter value
	 * @return this instance for method chaining
	 */
	public Body addUrlEncoded(String key, String value) {
		return addUrlEncoded(UrlEncodedParameter.of(key, value));
	}

	// Utility methods

	/**
	 * Checks if the body is enabled
	 * 
	 * @return true if enabled (not disabled)
	 */
	public boolean isEnabled() {
		return disabled == null || !disabled;
	}

	/**
	 * Gets the body mode as enum
	 * 
	 * @return BodyMode enum or null if not found
	 */
	public BodyMode getModeEnum() {
		if (mode == null)
			return null;

		for (BodyMode m : BodyMode.values()) {
			if (m.getValue().equals(mode)) {
				return m;
			}
		}
		return null;
	}

	/**
	 * Checks if this is a raw body
	 * 
	 * @return true if raw mode
	 */
	public boolean isRaw() {
		return BodyMode.RAW.getValue().equals(mode);
	}

	/**
	 * Checks if this is a form data body
	 * 
	 * @return true if form data mode
	 */
	public boolean isFormData() {
		return BodyMode.FORMDATA.getValue().equals(mode);
	}

	/**
	 * Checks if this is a URL encoded body
	 * 
	 * @return true if URL encoded mode
	 */
	public boolean isUrlEncoded() {
		return BodyMode.URLENCODED.getValue().equals(mode);
	}

	/**
	 * Checks if this is a binary body
	 * 
	 * @return true if binary mode
	 */
	public boolean isBinary() {
		return BodyMode.BINARY.getValue().equals(mode);
	}

	/**
	 * Checks if this is a file body
	 * 
	 * @return true if file mode
	 */
	public boolean isFile() {
		return BodyMode.FILE.getValue().equals(mode);
	}

	/**
	 * Checks if this is a GraphQL body
	 * 
	 * @return true if GraphQL mode
	 */
	public boolean isGraphQL() {
		return BodyMode.GRAPHQL.getValue().equals(mode);
	}

	/**
	 * Checks if the body has content
	 * 
	 * @return true if has content
	 */
	public boolean hasContent() {
		switch (getModeEnum()) {
		case RAW:
			return raw != null && !raw.trim().isEmpty();
		case FORMDATA:
			return formdata != null && !formdata.isEmpty();
		case URLENCODED:
			return urlencoded != null && !urlencoded.isEmpty();
		case GRAPHQL:
			return graphql != null && graphql.hasQuery();
		case FILE:
			return file != null && file.hasSrc();
		case BINARY:
			return true; // Binary is assumed to have content if mode is set
		default:
			return false;
		}
	}

	/**
	 * Gets the content size (approximate)
	 * 
	 * @return Content size or 0 if no content
	 */
	public int getContentSize() {
		if (!hasContent())
			return 0;

		switch (getModeEnum()) {
		case RAW:
			return raw != null ? raw.length() : 0;
		case FORMDATA:
			return formdata != null ? formdata.size() : 0;
		case URLENCODED:
			return urlencoded != null ? urlencoded.size() : 0;
		default:
			return 0;
		}
	}

	/**
	 * Creates a copy of this body
	 * 
	 * @return Copy of this body
	 */
	public Body copy() {
		return Body.builder().mode(this.mode).raw(this.raw).graphql(this.graphql != null ? this.graphql.copy() : null)
				.formdata(this.formdata != null
						? this.formdata.stream().map(FormParameter::copy).collect(java.util.stream.Collectors.toList())
						: new ArrayList<>())
				.urlencoded(this.urlencoded != null ? this.urlencoded.stream().map(UrlEncodedParameter::copy)
						.collect(java.util.stream.Collectors.toList()) : new ArrayList<>())
				.file(this.file != null ? this.file.copy() : null)
				.options(this.options != null ? this.options.copy() : null).disabled(this.disabled).build();
	}

	/**
	 * Creates a copy with new raw content
	 * 
	 * @param newRaw The new raw content
	 * @return Copy of this body with new raw content
	 */
	public Body copyWithRaw(String newRaw) {
		Body copy = copy();
		copy.raw = newRaw;
		return copy;
	}

	/**
	 * Creates a copy with new mode
	 * 
	 * @param newMode The new mode
	 * @return Copy of this body with new mode
	 */
	public Body copyWithMode(BodyMode newMode) {
		Body copy = copy();
		copy.mode = newMode.getValue();
		return copy;
	}

	/**
	 * Creates a copy with new disabled state
	 * 
	 * @param newDisabled The new disabled state
	 * @return Copy of this body with new disabled state
	 */
	public Body copyWithDisabled(boolean newDisabled) {
		Body copy = copy();
		copy.disabled = newDisabled;
		return copy;
	}

	/**
	 * Creates an enabled copy of this body
	 * 
	 * @return Enabled copy of this body
	 */
	public Body copyEnabled() {
		return copyWithDisabled(false);
	}

	/**
	 * Creates a disabled copy of this body
	 * 
	 * @return Disabled copy of this body
	 */
	public Body copyDisabled() {
		return copyWithDisabled(true);
	}

}