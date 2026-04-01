package es.alesqui.intelligence.model.api_spec.postman;

import org.springframework.data.mongodb.core.mapping.Field;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Variables in collections remove the need to duplicate data in multiple
 * places. Variables can be defined and referenced from any part of a
 * collection.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Variable {

	/**
	 * A unique user-defined value that identifies the variable within a collection.
	 * Required field.
	 */
	@Field("id")
	@JsonProperty("id")
	private String id;

	/**
	 * A human-friendly value that identifies the variable within a collection.
	 * Required field.
	 */
	@Field("key")
	@JsonProperty("key")
	private String key;

	/**
	 * The value that a variable holds in this collection. The variables are
	 * replaced by this value when you run a set of requests from a collection.
	 * Optional field.
	 */
	@Field("value")
	@JsonProperty("value")
	private String value;

	/**
	 * A variable may have multiple types. This field specifies the type of the
	 * variable. Optional field.
	 */
	@Field("type")
	@JsonProperty("type")
	private Object type;

	/**
	 * The name of the variable. Optional field.
	 */
	@Field("name")
	@JsonProperty("name")
	private String name;

	/**
	 * The description of the variable. Optional field.
	 */
	@Field("description")
	@JsonProperty("description")
	private Description description;

	/**
	 * When set to true, indicates that this variable has been set by Postman.
	 * Optional field.
	 */
	@Field("system")
	@JsonProperty("system")
	@Builder.Default
	private Boolean system = false;

	/**
	 * When set to true, the variable is ignored at runtime. Optional field.
	 */
	@Field("disabled")
	@JsonProperty("disabled")
	@Builder.Default
	private Boolean disabled = false;

	/**
	 * Constructor for basic variable with required fields only
	 * 
	 * @param id  Unique identifier
	 * @param key Human-friendly key
	 */
	public Variable(String id, String key) {
		this.id = id;
		this.key = key;
		this.system = false;
		this.disabled = false;
	}

	/**
	 * Constructor for variable with id, key and value
	 * 
	 * @param id    Unique identifier
	 * @param key   Human-friendly key
	 * @param value Variable value
	 */
	public Variable(String id, String key, String value) {
		this.id = id;
		this.key = key;
		this.value = value;
		this.system = false;
		this.disabled = false;
	}

	/**
	 * Constructor for complete variable
	 * 
	 * @param id    Unique identifier
	 * @param key   Human-friendly key
	 * @param value Variable value
	 * @param name  Variable name
	 */
	public Variable(String id, String key, String value, String name) {
		this.id = id;
		this.key = key;
		this.value = value;
		this.name = name;
		this.system = false;
		this.disabled = false;
	}

	/**
	 * Creates a simple variable with key and value
	 * 
	 * @param key   Variable key
	 * @param value Variable value
	 * @return Variable instance
	 */
	public static Variable of(String key, String value) {
		return new Variable(key, key, value);
	}

	/**
	 * Creates a variable with key, value and description
	 * 
	 * @param key         Variable key
	 * @param value       Variable value
	 * @param description Variable description
	 * @return Variable instance
	 */
	public static Variable of(String key, String value, String description) {
		return Variable.builder().id(key).key(key).value(value).description(new Description(description)).build();
	}

	/**
	 * Creates a system variable (set by Postman)
	 * 
	 * @param key   Variable key
	 * @param value Variable value
	 * @return Variable instance marked as system
	 */
	public static Variable system(String key, String value) {
		return Variable.builder().id(key).key(key).value(value).system(true).build();
	}

	/**
	 * Creates a disabled variable
	 * 
	 * @param key   Variable key
	 * @param value Variable value
	 * @return Variable instance marked as disabled
	 */
	public static Variable disabled(String key, String value) {
		return Variable.builder().id(key).key(key).value(value).disabled(true).build();
	}

	/**
	 * Enables this variable
	 * 
	 * @return this instance for method chaining
	 */
	public Variable enable() {
		this.disabled = false;
		return this;
	}

	/**
	 * Disables this variable
	 * 
	 * @return this instance for method chaining
	 */
	public Variable disable() {
		this.disabled = true;
		return this;
	}

	/**
	 * Checks if this variable is active (not disabled)
	 * 
	 * @return true if variable is active
	 */
	public boolean isActive() {
		return !Boolean.TRUE.equals(disabled);
	}

	/**
	 * Checks if this is a system variable
	 * 
	 * @return true if variable is set by Postman
	 */
	public boolean isSystemVariable() {
		return Boolean.TRUE.equals(system);
	}

	/**
	 * Creates a copy of this variable
	 * 
	 * @return Copy of this variable
	 */
	public Variable copy() {
		return Variable.builder().id(this.id).key(this.key).value(this.value).type(this.type).name(this.name)
				.description(this.description != null ? this.description.copy() : null).system(this.system)
				.disabled(this.disabled).build();
	}

	/**
	 * Creates a copy with a new value
	 * 
	 * @param newValue The new value for the copy
	 * @return Copy of this variable with new value
	 */
	public Variable copyWithValue(String newValue) {
		Variable copy = copy();
		copy.value = newValue;
		return copy;
	}

	/**
	 * Creates a copy with a new key
	 * 
	 * @param newKey The new key for the copy
	 * @return Copy of this variable with new key
	 */
	public Variable copyWithKey(String newKey) {
		Variable copy = copy();
		copy.key = newKey;
		return copy;
	}

	/**
	 * Creates a copy with a new id
	 * 
	 * @param newId The new id for the copy
	 * @return Copy of this variable with new id
	 */
	public Variable copyWithId(String newId) {
		Variable copy = copy();
		copy.id = newId;
		return copy;
	}

	/**
	 * Creates a copy with new key and id (both set to the same value)
	 * 
	 * @param newKeyId The new key and id for the copy
	 * @return Copy of this variable with new key and id
	 */
	public Variable copyWithKeyAndId(String newKeyId) {
		Variable copy = copy();
		copy.key = newKeyId;
		copy.id = newKeyId;
		return copy;
	}

	/**
	 * Creates a copy with a new name
	 * 
	 * @param newName The new name for the copy
	 * @return Copy of this variable with new name
	 */
	public Variable copyWithName(String newName) {
		Variable copy = copy();
		copy.name = newName;
		return copy;
	}

	/**
	 * Creates a copy with a new type
	 * 
	 * @param newType The new type for the copy
	 * @return Copy of this variable with new type
	 */
	public Variable copyWithType(Object newType) {
		Variable copy = copy();
		copy.type = newType;
		return copy;
	}

	/**
	 * Creates a copy with new disabled state
	 * 
	 * @param newDisabled The new disabled state
	 * @return Copy of this variable with new disabled state
	 */
	public Variable copyWithDisabled(boolean newDisabled) {
		Variable copy = copy();
		copy.disabled = newDisabled;
		return copy;
	}

	/**
	 * Creates an enabled copy of this variable
	 * 
	 * @return Enabled copy of this variable
	 */
	public Variable copyEnabled() {
		return copyWithDisabled(false);
	}

	/**
	 * Creates a disabled copy of this variable
	 * 
	 * @return Disabled copy of this variable
	 */
	public Variable copyDisabled() {
		return copyWithDisabled(true);
	}

	/**
	 * Creates a copy with new system state
	 * 
	 * @param newSystem The new system state
	 * @return Copy of this variable with new system state
	 */
	public Variable copyWithSystem(boolean newSystem) {
		Variable copy = copy();
		copy.system = newSystem;
		return copy;
	}

	/**
	 * Creates a system copy of this variable
	 * 
	 * @return System copy of this variable
	 */
	public Variable copyAsSystem() {
		return copyWithSystem(true);
	}

	/**
	 * Creates a non-system copy of this variable
	 * 
	 * @return Non-system copy of this variable
	 */
	public Variable copyAsNonSystem() {
		return copyWithSystem(false);
	}

	/**
	 * Creates a copy with a new description
	 * 
	 * @param newDescription The new description
	 * @return Copy of this variable with new description
	 */
	public Variable copyWithDescription(String newDescription) {
		Variable copy = copy();
		copy.description = newDescription != null ? new Description(newDescription) : null;
		return copy;
	}

	/**
	 * Creates a copy with a new description object
	 * 
	 * @param newDescription The new description object
	 * @return Copy of this variable with new description
	 */
	public Variable copyWithDescription(Description newDescription) {
		Variable copy = copy();
		copy.description = newDescription != null ? newDescription.copy() : null;
		return copy;
	}

}