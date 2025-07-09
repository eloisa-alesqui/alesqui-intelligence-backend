package es.alesqui.postmangpt.model.postman;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import es.alesqui.postmangpt.model.postman.enums.EventType;

import org.springframework.data.mongodb.core.mapping.Field;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Defines a script associated with an associated event name
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Event {

	/**
	 * A unique identifier for the enclosing event.
	 */
	@Field("id")
	@JsonProperty("id")
	private String id;

	/**
	 * Can be set to `test` or `prerequest` for collection level events.
	 */
	@Field("listen")
	@JsonProperty("listen")
	private String listen;

	/**
	 * The script object represents a script associated with the event.
	 */
	@Field("script")
	@JsonProperty("script")
	private Script script;

	/**
	 * Indicates whether the event is disabled. If absent, the event is assumed to
	 * be enabled.
	 */
	@Field("disabled")
	@JsonProperty("disabled")
	@Builder.Default
	private Boolean disabled = false;

	// ========== CONSTRUCTORS ==========

	/**
	 * Constructor for basic event with listen type and script
	 * 
	 * @param listen Event type (prerequest/test)
	 * @param script Script object
	 */
	public Event(String listen, Script script) {
		this.listen = listen;
		this.script = script;
		this.disabled = false;
	}

	/**
	 * Constructor for event with listen type and script code
	 * 
	 * @param listen     Event type (prerequest/test)
	 * @param scriptCode Script code as string
	 */
	public Event(String listen, String scriptCode) {
		this.listen = listen;
		this.script = scriptCode != null ? new Script(scriptCode) : null;
		this.disabled = false;
	}

	/**
	 * Constructor with type enum and script code
	 * 
	 * @param type       EventType enum
	 * @param scriptCode Script code as string
	 */
	public Event(EventType type, String scriptCode) {
		this.listen = type != null ? type.getValue() : null;
		this.script = scriptCode != null ? new Script(scriptCode) : null;
		this.disabled = false;
	}

	// ========== FACTORY METHODS ==========

	/**
	 * Creates a pre-request event
	 * 
	 * @param scriptCode Script code
	 * @return Event instance for pre-request
	 */
	public static Event preRequest(String scriptCode) {
		return new Event(EventType.PREREQUEST, scriptCode);
	}

	/**
	 * Creates a pre-request event with script object
	 * 
	 * @param script Script object
	 * @return Event instance for pre-request
	 */
	public static Event preRequest(Script script) {
		return new Event(EventType.PREREQUEST.getValue(), script);
	}

	/**
	 * Creates a test event
	 * 
	 * @param scriptCode Script code
	 * @return Event instance for test
	 */
	public static Event test(String scriptCode) {
		return new Event(EventType.TEST, scriptCode);
	}

	/**
	 * Creates a test event with script object
	 * 
	 * @param script Script object
	 * @return Event instance for test
	 */
	public static Event test(Script script) {
		return new Event(EventType.TEST.getValue(), script);
	}

	/**
	 * Creates an event with custom listen type
	 * 
	 * @param listen     Listen type
	 * @param scriptCode Script code
	 * @return Event instance
	 */
	public static Event of(String listen, String scriptCode) {
		return new Event(listen, scriptCode);
	}

	/**
	 * Creates an event with custom listen type and script object
	 * 
	 * @param listen Listen type
	 * @param script Script object
	 * @return Event instance
	 */
	public static Event of(String listen, Script script) {
		return new Event(listen, script);
	}

	/**
	 * Sets the event as disabled
	 * 
	 * @return this instance for method chaining
	 */
	public Event disable() {
		this.disabled = true;
		return this;
	}

	/**
	 * Sets the event as enabled
	 * 
	 * @return this instance for method chaining
	 */
	public Event enable() {
		this.disabled = false;
		return this;
	}

	/**
	 * Sets the disabled state
	 * 
	 * @param disabled Whether the event is disabled
	 * @return this instance for method chaining
	 */
	public Event setDisabled(boolean disabled) {
		this.disabled = disabled;
		return this;
	}

	/**
	 * Sets the ID and returns this instance
	 * 
	 * @param id Event ID
	 * @return this instance for method chaining
	 */
	public Event withId(String id) {
		this.id = id;
		return this;
	}

	/**
	 * Sets the script and returns this instance
	 * 
	 * @param script Script object
	 * @return this instance for method chaining
	 */
	public Event withScript(Script script) {
		this.script = script;
		return this;
	}

	/**
	 * Sets the script code and returns this instance
	 * 
	 * @param scriptCode Script code
	 * @return this instance for method chaining
	 */
	public Event withScript(String scriptCode) {
		this.script = scriptCode != null ? new Script(scriptCode) : null;
		return this;
	}

	/**
	 * Checks if this is a pre-request event
	 * 
	 * @return true if pre-request event
	 */
	public boolean isPreRequest() {
		return EventType.PREREQUEST.getValue().equals(listen);
	}

	/**
	 * Checks if this is a test event
	 * 
	 * @return true if test event
	 */
	public boolean isTest() {
		return EventType.TEST.getValue().equals(listen);
	}

	/**
	 * Checks if the event is enabled
	 * 
	 * @return true if enabled (not disabled)
	 */
	public boolean isEnabled() {
		return disabled == null || !disabled;
	}

	/**
	 * Gets the event type as enum
	 * 
	 * @return EventType enum or null if not found
	 */
	public EventType getTypeEnum() {
		if (listen == null)
			return null;

		for (EventType type : EventType.values()) {
			if (type.getValue().equals(listen)) {
				return type;
			}
		}
		return null;
	}

	/**
	 * Gets the script code
	 * 
	 * @return Script code or null if no script
	 */
	public String getScriptCode() {
		return script != null ? script.getExecAsString() : null;
	}

	/**
	 * Checks if the event has a script
	 * 
	 * @return true if script is not null and has content
	 */
	public boolean hasScript() {
		return script != null && script.getExec() != null && !script.getExec().isEmpty();
	}

	/**
	 * Checks if the event has a valid listen type
	 * 
	 * @return true if listen is not null and not empty
	 */
	public boolean hasListen() {
		return listen != null && !listen.trim().isEmpty();
	}

	/**
	 * Checks if this is a valid event
	 * 
	 * @return true if has listen type and script
	 */
	public boolean isValid() {
		return hasListen() && hasScript();
	}

	/**
	 * Appends code to the existing script
	 * 
	 * @param additionalCode Code to append
	 * @return this instance for method chaining
	 */
	public Event appendScript(String additionalCode) {
		if (additionalCode == null || additionalCode.trim().isEmpty()) {
			return this;
		}

		if (script == null) {
			script = new Script(additionalCode);
		} else {
			script.appendExec(additionalCode);
		}
		return this;
	}

	/**
	 * Prepends code to the existing script
	 * 
	 * @param prependCode Code to prepend
	 * @return this instance for method chaining
	 */
	public Event prependScript(String prependCode) {
		if (prependCode == null || prependCode.trim().isEmpty()) {
			return this;
		}

		if (script == null) {
			script = new Script(prependCode);
		} else {
			script.prependExec(prependCode);
		}
		return this;
	}

	/**
	 * Creates a copy of this event
	 * 
	 * @return Copy of this event
	 */
	public Event copy() {
		return Event.builder().id(this.id).listen(this.listen).script(this.script != null ? this.script.copy() : null)
				.disabled(this.disabled).build();
	}

	/**
	 * Creates a copy with a new script
	 * 
	 * @param newScript The new script for the copy
	 * @return Copy of this event with new script
	 */
	public Event copyWithScript(Script newScript) {
		Event copy = copy();
		copy.script = newScript != null ? newScript.copy() : null;
		return copy;
	}

	/**
	 * Creates a copy with new script code
	 * 
	 * @param newScriptCode The new script code for the copy
	 * @return Copy of this event with new script code
	 */
	public Event copyWithScript(String newScriptCode) {
		Event copy = copy();
		copy.script = newScriptCode != null ? new Script(newScriptCode) : null;
		return copy;
	}

	/**
	 * Creates a copy with a new listen type
	 * 
	 * @param newListen The new listen type for the copy
	 * @return Copy of this event with new listen type
	 */
	public Event copyWithListen(String newListen) {
		Event copy = copy();
		copy.listen = newListen;
		return copy;
	}

	/**
	 * Creates a copy with a new listen type using enum
	 * 
	 * @param newType The new EventType for the copy
	 * @return Copy of this event with new listen type
	 */
	public Event copyWithListen(EventType newType) {
		Event copy = copy();
		copy.listen = newType != null ? newType.getValue() : null;
		return copy;
	}

	/**
	 * Creates a copy with a new ID
	 * 
	 * @param newId The new ID for the copy
	 * @return Copy of this event with new ID
	 */
	public Event copyWithId(String newId) {
		Event copy = copy();
		copy.id = newId;
		return copy;
	}

	/**
	 * Creates a copy with new disabled state
	 * 
	 * @param newDisabled The new disabled state
	 * @return Copy of this event with new disabled state
	 */
	public Event copyWithDisabled(boolean newDisabled) {
		Event copy = copy();
		copy.disabled = newDisabled;
		return copy;
	}

	/**
	 * Creates an enabled copy of this event
	 * 
	 * @return Enabled copy of this event
	 */
	public Event copyEnabled() {
		return copyWithDisabled(false);
	}

	/**
	 * Creates a disabled copy of this event
	 * 
	 * @return Disabled copy of this event
	 */
	public Event copyDisabled() {
		return copyWithDisabled(true);
	}

	/**
	 * Creates a copy as pre-request event
	 * 
	 * @return Copy of this event as pre-request
	 */
	public Event copyAsPreRequest() {
		return copyWithListen(EventType.PREREQUEST);
	}

	/**
	 * Creates a copy as test event
	 * 
	 * @return Copy of this event as test
	 */
	public Event copyAsTest() {
		return copyWithListen(EventType.TEST);
	}

	/**
	 * Creates a copy with additional script code appended
	 * 
	 * @param additionalCode Code to append to the script
	 * @return Copy of this event with appended script
	 */
	public Event copyWithAppendedScript(String additionalCode) {
		Event copy = copy();
		copy.appendScript(additionalCode);
		return copy;
	}

	/**
	 * Creates a copy with code prepended to the script
	 * 
	 * @param prependCode Code to prepend to the script
	 * @return Copy of this event with prepended script
	 */
	public Event copyWithPrependedScript(String prependCode) {
		Event copy = copy();
		copy.prependScript(prependCode);
		return copy;
	}
}