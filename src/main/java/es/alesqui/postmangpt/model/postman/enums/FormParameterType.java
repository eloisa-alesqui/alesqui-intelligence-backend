package es.alesqui.postmangpt.model.postman.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Form parameter types supported by Postman
 */
public enum FormParameterType {
	TEXT("text"), FILE("file");

	private final String value;

	FormParameterType(String value) {
		this.value = value;
	}

	@JsonValue
	public String getValue() {
		return value;
	}

	public static FormParameterType fromString(String type) {
		if (type == null)
			return TEXT;

		for (FormParameterType fpt : values()) {
			if (fpt.value.equalsIgnoreCase(type)) {
				return fpt;
			}
		}
		return TEXT;
	}

	@Override
	public String toString() {
		return value;
	}
}
