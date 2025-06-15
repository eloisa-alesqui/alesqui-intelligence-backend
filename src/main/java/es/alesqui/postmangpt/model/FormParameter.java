package es.alesqui.postmangpt.model;

import org.springframework.data.mongodb.core.mapping.Field;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import es.alesqui.postmangpt.model.enums.FormParameterType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a form data parameter
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FormParameter {

	/**
	 * The key of the form parameter
	 */
	@Field("key")
	@JsonProperty("key")
	private String key;

	/**
	 * The value of the form parameter
	 */
	@Field("value")
	@JsonProperty("value")
	private String value;

	/**
	 * Indicates whether this parameter is disabled
	 */
	@Field("disabled")
	@JsonProperty("disabled")
	@Builder.Default
	private Boolean disabled = false;

	/**
	 * The type of the form parameter (text or file)
	 */
	@Field("type")
	@JsonProperty("type")
	@Builder.Default
	private String type = FormParameterType.TEXT.getValue();

	/**
	 * Content type of the form parameter
	 */
	@Field("contentType")
	@JsonProperty("contentType")
	private String contentType;

	/**
	 * Description of the form parameter
	 */
	@Field("description")
	@JsonProperty("description")
	private Description description;

	/**
	 * File source (for file type parameters)
	 */
	@Field("src")
	@JsonProperty("src")
	private String src;

	/**
	 * Constructor for text parameter
	 * 
	 * @param key   Parameter key
	 * @param value Parameter value
	 */
	public FormParameter(String key, String value) {
		this.key = key;
		this.value = value;
		this.type = FormParameterType.TEXT.getValue();
		this.disabled = false;
	}

	/**
	 * Constructor for file parameter
	 * 
	 * @param key         Parameter key
	 * @param src         File source
	 * @param contentType Content type
	 */
	public FormParameter(String key, String src, String contentType) {
		this.key = key;
		this.src = src;
		this.contentType = contentType;
		this.type = FormParameterType.FILE.getValue();
		this.disabled = false;
	}

	/**
	 * Creates a text form parameter
	 * 
	 * @param key   Parameter key
	 * @param value Parameter value
	 * @return FormParameter instance
	 */
	public static FormParameter of(String key, String value) {
		return new FormParameter(key, value);
	}

	/**
	 * Creates a text form parameter
	 * 
	 * @param key   Parameter key
	 * @param value Parameter value
	 * @return FormParameter instance
	 */
	public static FormParameter text(String key, String value) {
		return new FormParameter(key, value);
	}

	/**
	 * Creates a file form parameter
	 * 
	 * @param key Parameter key
	 * @param src File source
	 * @return FormParameter instance
	 */
	public static FormParameter file(String key, String src) {
		FormParameter param = new FormParameter();
		param.key = key;
		param.src = src;
		param.type = FormParameterType.FILE.getValue();
		param.disabled = false;
		return param;
	}

	/**
	 * Creates a file form parameter with content type
	 * 
	 * @param key         Parameter key
	 * @param src         File source
	 * @param contentType Content type
	 * @return FormParameter instance
	 */
	public static FormParameter file(String key, String src, String contentType) {
		return new FormParameter(key, src, contentType);
	}

	/**
	 * Creates a disabled form parameter
	 * 
	 * @param key   Parameter key
	 * @param value Parameter value
	 * @return Disabled FormParameter instance
	 */
	public static FormParameter disabled(String key, String value) {
		FormParameter param = new FormParameter(key, value);
		param.disabled = true;
		return param;
	}

	// Fluent API methods

	/**
	 * Sets the parameter as disabled
	 * 
	 * @return this instance for method chaining
	 */
	public FormParameter disable() {
		this.disabled = true;
		return this;
	}

	/**
	 * Sets the parameter as enabled
	 * 
	 * @return this instance for method chaining
	 */
	public FormParameter enable() {
		this.disabled = false;
		return this;
	}

	/**
	 * Sets the content type
	 * 
	 * @param contentType Content type
	 * @return this instance for method chaining
	 */
	public FormParameter withContentType(String contentType) {
		this.contentType = contentType;
		return this;
	}

	/**
	 * Sets the description
	 * 
	 * @param description Description text
	 * @return this instance for method chaining
	 */
	public FormParameter withDescription(String description) {
		this.description = new Description(description);
		return this;
	}

	/**
	 * Sets the description object
	 * 
	 * @param description Description object
	 * @return this instance for method chaining
	 */
	public FormParameter withDescription(Description description) {
		this.description = description;
		return this;
	}

	// Utility methods

	/**
	 * Checks if the parameter is enabled
	 * 
	 * @return true if enabled (not disabled)
	 */
	public boolean isEnabled() {
		return disabled == null || !disabled;
	}

	/**
	 * Gets the parameter type as enum
	 * 
	 * Uses the enum's fromString method for consistent conversion logic.
	 * 
	 * @return FormParameterType enum (defaults to TEXT if conversion fails)
	 */
	public FormParameterType getFormParameterType() {
		return FormParameterType.fromString(type);
	}

	/**
	 * Checks if this is a text parameter
	 * 
	 * @return true if text type
	 */
	public boolean isText() {
		return FormParameterType.TEXT.getValue().equals(type);
	}

	/**
	 * Checks if this is a file parameter
	 * 
	 * @return true if file type
	 */
	public boolean isFile() {
		return FormParameterType.FILE.getValue().equals(type);
	}

	/**
	 * Checks if the parameter has a value
	 * 
	 * @return true if has value
	 */
	public boolean hasValue() {
		return isText() ? (value != null && !value.trim().isEmpty())
				: isFile() ? (src != null && !src.trim().isEmpty()) : false;
	}

	/**
	 * Gets the effective value (value for text, src for file)
	 * 
	 * @return Effective value
	 */
	public String getEffectiveValue() {
		return isText() ? value : src;
	}

	/**
	 * Creates a copy of this form parameter
	 * 
	 * @return Copy of this form parameter
	 */
	public FormParameter copy() {
		return FormParameter.builder().key(this.key).value(this.value).disabled(this.disabled).type(this.type)
				.contentType(this.contentType).description(this.description != null ? this.description.copy() : null)
				.src(this.src).build();
	}

	/**
	 * Creates a copy with a new value
	 * 
	 * @param newValue The new value for the copy
	 * @return Copy of this form parameter with new value
	 */
	public FormParameter copyWithValue(String newValue) {
		FormParameter copy = copy();
		copy.value = newValue;
		return copy;
	}

	/**
	 * Creates a copy with a new key
	 * 
	 * @param newKey The new key for the copy
	 * @return Copy of this form parameter with new key
	 */
	public FormParameter copyWithKey(String newKey) {
		FormParameter copy = copy();
		copy.key = newKey;
		return copy;
	}

	/**
	 * Creates a copy with new disabled state
	 * 
	 * @param newDisabled The new disabled state
	 * @return Copy of this form parameter with new disabled state
	 */
	public FormParameter copyWithDisabled(boolean newDisabled) {
		FormParameter copy = copy();
		copy.disabled = newDisabled;
		return copy;
	}

	/**
	 * Creates an enabled copy of this form parameter
	 * 
	 * @return Enabled copy of this form parameter
	 */
	public FormParameter copyEnabled() {
		return copyWithDisabled(false);
	}

	/**
	 * Creates a disabled copy of this form parameter
	 * 
	 * @return Disabled copy of this form parameter
	 */
	public FormParameter copyDisabled() {
		return copyWithDisabled(true);
	}

	/**
	 * Creates a copy with a new content type
	 * 
	 * @param newContentType The new content type
	 * @return Copy of this form parameter with new content type
	 */
	public FormParameter copyWithContentType(String newContentType) {
		FormParameter copy = copy();
		copy.contentType = newContentType;
		return copy;
	}

	/**
	 * Creates a copy with a new type
	 * 
	 * @param newType The new FormParameterType
	 * @return Copy of this form parameter with new type
	 */
	public FormParameter copyWithType(FormParameterType newType) {
		FormParameter copy = copy();
		copy.type = newType.getValue();
		return copy;
	}

	/**
	 * Creates a copy with a new source (for file parameters)
	 * 
	 * @param newSrc The new source
	 * @return Copy of this form parameter with new source
	 */
	public FormParameter copyWithSrc(String newSrc) {
		FormParameter copy = copy();
		copy.src = newSrc;
		return copy;
	}

	/**
	 * Creates a copy with a new description
	 * 
	 * @param newDescription The new description
	 * @return Copy of this form parameter with new description
	 */
	public FormParameter copyWithDescription(String newDescription) {
		FormParameter copy = copy();
		copy.description = newDescription != null ? new Description(newDescription) : null;
		return copy;
	}

	/**
	 * Creates a copy with a new description object
	 * 
	 * @param newDescription The new description object
	 * @return Copy of this form parameter with new description
	 */
	public FormParameter copyWithDescription(Description newDescription) {
		FormParameter copy = copy();
		copy.description = newDescription != null ? newDescription.copy() : null;
		return copy;
	}

}