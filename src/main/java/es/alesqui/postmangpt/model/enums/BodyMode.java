package es.alesqui.postmangpt.model.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Body modes for HTTP requests
 */
public enum BodyMode {
    RAW("raw"),
    URLENCODED("urlencoded"),
    FORMDATA("formdata"),
    FILE("file"),
    BINARY("binary"),
    GRAPHQL("graphql");

    private final String value;

    BodyMode(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static BodyMode fromString(String mode) {
        if (mode == null) return RAW;
        
        for (BodyMode bm : values()) {
            if (bm.value.equalsIgnoreCase(mode)) {
                return bm;
            }
        }
        return RAW;
    }

    @Override
    public String toString() {
        return value;
    }
}

