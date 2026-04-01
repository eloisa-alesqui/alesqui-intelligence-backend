package es.alesqui.intelligence.model.api_spec.postman.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Common HTTP header types
 */
public enum HeaderType {
    CONTENT_TYPE("Content-Type"),
    AUTHORIZATION("Authorization"),
    ACCEPT("Accept"),
    USER_AGENT("User-Agent"),
    CACHE_CONTROL("Cache-Control"),
    COOKIE("Cookie"),
    SET_COOKIE("Set-Cookie"),
    CUSTOM("Custom");

    private final String value;

    HeaderType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static HeaderType fromString(String type) {
        if (type == null) return CUSTOM;
        
        for (HeaderType ht : values()) {
            if (ht.value.equalsIgnoreCase(type)) {
                return ht;
            }
        }
        return CUSTOM;
    }

    @Override
    public String toString() {
        return value;
    }
}
