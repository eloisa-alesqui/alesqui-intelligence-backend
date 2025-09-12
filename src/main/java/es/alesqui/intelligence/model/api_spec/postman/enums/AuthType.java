package es.alesqui.intelligence.model.api_spec.postman.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Authentication types supported by Postman
 */
public enum AuthType {
	NOAUTH("noauth"), APIKEY("apikey"), AWSV4("awsv4"), BASIC("basic"), BEARER("bearer"), DIGEST("digest"),
	HAWK("hawk"), NTLM("ntlm"), OAUTH1("oauth1"), OAUTH2("oauth2");

	private final String value;

	AuthType(String value) {
		this.value = value;
	}

	@JsonValue
	public String getValue() {
		return value;
	}

	public static AuthType fromString(String type) {
		if (type == null)
			return NOAUTH;

		for (AuthType at : values()) {
			if (at.value.equalsIgnoreCase(type)) {
				return at;
			}
		}
		return NOAUTH;
	}

	@Override
	public String toString() {
		return value;
	}
}
