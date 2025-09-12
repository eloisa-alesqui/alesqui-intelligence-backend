package es.alesqui.intelligence.model.api_spec.postman;

import org.springframework.data.mongodb.core.mapping.Field;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a URL encoded parameter
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UrlEncodedParameter {

	/**
	 * The key of the URL encoded parameter
	 */
	@Field("key")
	@JsonProperty("key")
	private String key;

	/**
	 * The value of the URL encoded parameter
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
	 * Description of the URL encoded parameter
	 */
	@Field("description")
	@JsonProperty("description")
	private Description description;

	/**
	 * Constructor for URL encoded parameter
	 * 
	 * @param key   Parameter key
	 * @param value Parameter value
	 */
	public UrlEncodedParameter(String key, String value) {
		this.key = key;
		this.value = value;
		this.disabled = false;
	}

	/**
	 * Creates a URL encoded parameter
	 * 
	 * @param key   Parameter key
	 * @param value Parameter value
	 * @return UrlEncodedParameter instance
	 */
	public static UrlEncodedParameter of(String key, String value) {
		return new UrlEncodedParameter(key, value);
	}

	/**
	 * Creates a disabled URL encoded parameter
	 * 
	 * @param key   Parameter key
	 * @param value Parameter value
	 * @return Disabled UrlEncodedParameter instance
	 */
	public static UrlEncodedParameter disabled(String key, String value) {
		UrlEncodedParameter param = new UrlEncodedParameter(key, value);
		param.disabled = true;
		return param;
	}

	// Fluent API methods

	/**
	 * Sets the parameter as disabled
	 * 
	 * @return this instance for method chaining
	 */
	public UrlEncodedParameter disable() {
		this.disabled = true;
		return this;
	}

	/**
	 * Sets the parameter as enabled
	 * 
	 * @return this instance for method chaining
	 */
	public UrlEncodedParameter enable() {
		this.disabled = false;
		return this;
	}

	/**
	 * Sets the description
	 * 
	 * @param description Description text
	 * @return this instance for method chaining
	 */
	public UrlEncodedParameter withDescription(String description) {
		this.description = new Description(description);
		return this;
	}

	/**
	 * Sets the description object
	 * 
	 * @param description Description object
	 * @return this instance for method chaining
	 */
	public UrlEncodedParameter withDescription(Description description) {
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
	 * Checks if the parameter has a value
	 * 
	 * @return true if has value
	 */
	public boolean hasValue() {
		return value != null && !value.trim().isEmpty();
	}

	/**
	 * Gets the parameter as URL encoded string
	 * 
	 * @return URL encoded string (e.g., "key=value")
	 */
	public String toUrlEncodedString() {
		if (key == null)
			return "";
		String encodedValue = value != null ? value : "";
		return key + "=" + encodedValue;
	}

	/**
	 * Creates a copy of this URL encoded parameter
	 * 
	 * @return Copy of this URL encoded parameter
	 */
	public UrlEncodedParameter copy() {
		return UrlEncodedParameter.builder().key(this.key).value(this.value).disabled(this.disabled)
				.description(this.description != null ? this.description.copy() : null).build();
	}

	/**
	 * Creates a copy with new key
	 * 
	 * @param newKey The new key for the copy
	 * @return Copy of this parameter with new key
	 */
	public UrlEncodedParameter copyWithKey(String newKey) {
		UrlEncodedParameter copy = copy();
		copy.key = newKey;
		return copy;
	}

	/**
	 * Creates a copy with new value
	 * 
	 * @param newValue The new value for the copy
	 * @return Copy of this parameter with new value
	 */
	public UrlEncodedParameter copyWithValue(String newValue) {
		UrlEncodedParameter copy = copy();
		copy.value = newValue;
		return copy;
	}

	/**
	 * Creates a copy with new key and value
	 * 
	 * @param newKey   The new key for the copy
	 * @param newValue The new value for the copy
	 * @return Copy of this parameter with new key and value
	 */
	public UrlEncodedParameter copyWithKeyAndValue(String newKey, String newValue) {
		UrlEncodedParameter copy = copy();
		copy.key = newKey;
		copy.value = newValue;
		return copy;
	}

	/**
	 * Creates a copy with new disabled state
	 * 
	 * @param disabled Whether the copy should be disabled
	 * @return Copy of this parameter with new disabled state
	 */
	public UrlEncodedParameter copyWithDisabled(boolean disabled) {
		UrlEncodedParameter copy = copy();
		copy.disabled = disabled;
		return copy;
	}

	/**
	 * Creates a copy with new description
	 * 
	 * @param description The new description for the copy
	 * @return Copy of this parameter with new description
	 */
	public UrlEncodedParameter copyWithDescription(String description) {
		UrlEncodedParameter copy = copy();
		copy.description = description != null ? new Description(description) : null;
		return copy;
	}

	/**
	 * Creates a copy with new description object
	 * 
	 * @param description The new description object for the copy
	 * @return Copy of this parameter with new description
	 */
	public UrlEncodedParameter copyWithDescription(Description description) {
		UrlEncodedParameter copy = copy();
		copy.description = description != null ? description.copy() : null;
		return copy;
	}

	/**
	 * Creates an enabled copy of this parameter
	 * 
	 * @return Copy of this parameter as enabled
	 */
	public UrlEncodedParameter copyAsEnabled() {
		return copyWithDisabled(false);
	}

	/**
	 * Creates a disabled copy of this parameter
	 * 
	 * @return Copy of this parameter as disabled
	 */
	public UrlEncodedParameter copyAsDisabled() {
		return copyWithDisabled(true);
	}

	/**
	 * Creates a copy without description
	 * 
	 * @return Copy of this parameter without description
	 */
	public UrlEncodedParameter copyWithoutDescription() {
		return copyWithDescription((Description) null);
	}

	/**
	 * Creates a copy without value (empty value)
	 * 
	 * @return Copy of this parameter without value
	 */
	public UrlEncodedParameter copyWithoutValue() {
		return copyWithValue(null);
	}

	/**
	 * Creates a copy with empty value
	 * 
	 * @return Copy of this parameter with empty value
	 */
	public UrlEncodedParameter copyWithEmptyValue() {
		return copyWithValue("");
	}

	/**
	 * Creates a copy with a prefix added to the key
	 * 
	 * @param prefix Prefix to add to key
	 * @return Copy with prefixed key
	 */
	public UrlEncodedParameter copyWithKeyPrefix(String prefix) {
		String newKey = (prefix != null ? prefix : "") + (this.key != null ? this.key : "");
		return copyWithKey(newKey);
	}

	/**
	 * Creates a copy with a suffix added to the key
	 * 
	 * @param suffix Suffix to add to key
	 * @return Copy with suffixed key
	 */
	public UrlEncodedParameter copyWithKeySuffix(String suffix) {
		String newKey = (this.key != null ? this.key : "") + (suffix != null ? suffix : "");
		return copyWithKey(newKey);
	}

	/**
	 * Creates a copy with a prefix added to the value
	 * 
	 * @param prefix Prefix to add to value
	 * @return Copy with prefixed value
	 */
	public UrlEncodedParameter copyWithValuePrefix(String prefix) {
		String newValue = (prefix != null ? prefix : "") + (this.value != null ? this.value : "");
		return copyWithValue(newValue);
	}

	/**
	 * Creates a copy with a suffix added to the value
	 * 
	 * @param suffix Suffix to add to value
	 * @return Copy with suffixed value
	 */
	public UrlEncodedParameter copyWithValueSuffix(String suffix) {
		String newValue = (this.value != null ? this.value : "") + (suffix != null ? suffix : "");
		return copyWithValue(newValue);
	}

	/**
	 * Creates a copy with the key transformed to uppercase
	 * 
	 * @return Copy with uppercase key
	 */
	public UrlEncodedParameter copyWithUppercaseKey() {
		return copyWithKey(this.key != null ? this.key.toUpperCase() : null);
	}

	/**
	 * Creates a copy with the key transformed to lowercase
	 * 
	 * @return Copy with lowercase key
	 */
	public UrlEncodedParameter copyWithLowercaseKey() {
		return copyWithKey(this.key != null ? this.key.toLowerCase() : null);
	}

	/**
	 * Creates a copy with the value transformed to uppercase
	 * 
	 * @return Copy with uppercase value
	 */
	public UrlEncodedParameter copyWithUppercaseValue() {
		return copyWithValue(this.value != null ? this.value.toUpperCase() : null);
	}

	/**
	 * Creates a copy with the value transformed to lowercase
	 * 
	 * @return Copy with lowercase value
	 */
	public UrlEncodedParameter copyWithLowercaseValue() {
		return copyWithValue(this.value != null ? this.value.toLowerCase() : null);
	}

	/**
	 * Creates a copy with trimmed key and value
	 * 
	 * @return Copy with trimmed key and value
	 */
	public UrlEncodedParameter copyTrimmed() {
		UrlEncodedParameter copy = copy();
		copy.key = this.key != null ? this.key.trim() : null;
		copy.value = this.value != null ? this.value.trim() : null;
		return copy;
	}

	/**
	 * Creates a copy with key replaced using regex
	 * 
	 * @param regex       Regex pattern to match
	 * @param replacement Replacement string
	 * @return Copy with replaced key
	 */
	public UrlEncodedParameter copyWithKeyReplaced(String regex, String replacement) {
		String newKey = this.key != null ? this.key.replaceAll(regex, replacement) : null;
		return copyWithKey(newKey);
	}

	/**
	 * Creates a copy with value replaced using regex
	 * 
	 * @param regex       Regex pattern to match
	 * @param replacement Replacement string
	 * @return Copy with replaced value
	 */
	public UrlEncodedParameter copyWithValueReplaced(String regex, String replacement) {
		String newValue = this.value != null ? this.value.replaceAll(regex, replacement) : null;
		return copyWithValue(newValue);
	}

	/**
	 * Creates a copy for a different environment/context
	 * 
	 * @param environmentPrefix Prefix to add to identify environment
	 * @return Copy with environment prefix
	 */
	public UrlEncodedParameter copyForEnvironment(String environmentPrefix) {
		return copyWithKeyPrefix(environmentPrefix + "_");
	}

	/**
	 * Creates a copy as a test parameter
	 * 
	 * @return Copy with "test_" prefix
	 */
	public UrlEncodedParameter copyAsTest() {
		return copyForEnvironment("test");
	}

	/**
	 * Creates a copy as a development parameter
	 * 
	 * @return Copy with "dev_" prefix
	 */
	public UrlEncodedParameter copyAsDev() {
		return copyForEnvironment("dev");
	}

	/**
	 * Creates a copy as a production parameter
	 * 
	 * @return Copy with "prod_" prefix
	 */
	public UrlEncodedParameter copyAsProd() {
		return copyForEnvironment("prod");
	}

	/**
	 * Creates a copy with boolean value
	 * 
	 * @param boolValue Boolean value to set
	 * @return Copy with boolean value as string
	 */
	public UrlEncodedParameter copyWithBooleanValue(boolean boolValue) {
		return copyWithValue(String.valueOf(boolValue));
	}

	/**
	 * Creates a copy with integer value
	 * 
	 * @param intValue Integer value to set
	 * @return Copy with integer value as string
	 */
	public UrlEncodedParameter copyWithIntValue(int intValue) {
		return copyWithValue(String.valueOf(intValue));
	}

	/**
	 * Creates a copy with long value
	 * 
	 * @param longValue Long value to set
	 * @return Copy with long value as string
	 */
	public UrlEncodedParameter copyWithLongValue(long longValue) {
		return copyWithValue(String.valueOf(longValue));
	}

	/**
	 * Creates a copy with double value
	 * 
	 * @param doubleValue Double value to set
	 * @return Copy with double value as string
	 */
	public UrlEncodedParameter copyWithDoubleValue(double doubleValue) {
		return copyWithValue(String.valueOf(doubleValue));
	}

	/**
	 * Creates a copy with current timestamp as value
	 * 
	 * @return Copy with timestamp value
	 */
	public UrlEncodedParameter copyWithTimestampValue() {
		return copyWithValue(String.valueOf(System.currentTimeMillis()));
	}

	/**
	 * Creates a copy with UUID as value
	 * 
	 * @return Copy with UUID value
	 */
	public UrlEncodedParameter copyWithUuidValue() {
		return copyWithValue(java.util.UUID.randomUUID().toString());
	}

	/**
	 * Creates a copy and toggles the enabled/disabled state
	 * 
	 * @return Copy with toggled state
	 */
	public UrlEncodedParameter copyToggled() {
		return copyWithDisabled(!isEnabled());
	}

}