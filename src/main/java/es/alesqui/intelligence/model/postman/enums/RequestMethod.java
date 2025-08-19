package es.alesqui.intelligence.model.postman.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * HTTP request methods supported by Postman
 */
public enum RequestMethod {
	GET("GET"), POST("POST"), PUT("PUT"), DELETE("DELETE"), PATCH("PATCH"), HEAD("HEAD"), OPTIONS("OPTIONS"),
	CONNECT("CONNECT"), TRACE("TRACE");

	private final String value;

	RequestMethod(String value) {
		this.value = value;
	}

	@JsonValue
	public String getValue() {
		return value;
	}

	public static RequestMethod fromString(String method) {
		if (method == null)
			return GET;

		for (RequestMethod rm : values()) {
			if (rm.value.equalsIgnoreCase(method)) {
				return rm;
			}
		}
		return GET; // Default fallback
	}

	@Override
	public String toString() {
		return value;
	}
}
