package es.alesqui.intelligence.model.api_spec.postman.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Variable types in Postman
 */
public enum VariableType {
    STRING("string"),
    BOOLEAN("boolean"),
    ANY("any"),
    NUMBER("number");

    private final String value;

    VariableType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static VariableType fromString(String type) {
        if (type == null) return STRING;
        
        for (VariableType vt : values()) {
            if (vt.value.equalsIgnoreCase(type)) {
                return vt;
            }
        }
        return STRING;
    }

    @Override
    public String toString() {
        return value;
    }
}
