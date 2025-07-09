package es.alesqui.postmangpt.model.postman.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Event types in Postman collections
 */
public enum EventType {
	PREREQUEST("prerequest"), TEST("test");

	private final String value;

	EventType(String value) {
		this.value = value;
	}

	@JsonValue
	public String getValue() {
		return value;
	}

	public static EventType fromString(String type) {
		if (type == null)
			return TEST;

		for (EventType et : values()) {
			if (et.value.equalsIgnoreCase(type)) {
				return et;
			}
		}
		return TEST;
	}

	@Override
	public String toString() {
		return value;
	}
}
