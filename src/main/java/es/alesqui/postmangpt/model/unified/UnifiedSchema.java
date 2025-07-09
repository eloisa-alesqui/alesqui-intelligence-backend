package es.alesqui.postmangpt.model.unified;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Represents a unified schema model based on an OpenAPI schema.
 * This class is used to define reusable data models and their properties.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UnifiedSchema {

    // Basic properties of the schema
    /**
     * The type of the schema (e.g., object, array, string, etc.).
     */
    private String type;

    /**
     * The format of the schema (e.g., date-time, email, etc.).
     */
    private String format;

    /**
     * The title of the schema, providing a brief description.
     */
    private String title;

    /**
     * A detailed description of the schema.
     */
    private String description;

    /**
     * An example value for the schema.
     */
    private Object example;

    /**
     * The default value for the schema.
     */
    private Object defaultValue;

    // References
    /**
     * A reference to another schema, typically in the format "#/components/schemas/SchemaName".
     */
    @JsonProperty("$ref")
    private String ref;

    // Numeric validations
    /**
     * The minimum value allowed for numeric types.
     */
    private BigDecimal minimum;

    /**
     * The maximum value allowed for numeric types.
     */
    private BigDecimal maximum;

    /**
     * Whether the minimum value is exclusive (i.e., not inclusive).
     */
    private Boolean exclusiveMinimum;

    /**
     * Whether the maximum value is exclusive (i.e., not inclusive).
     */
    private Boolean exclusiveMaximum;

    /**
     * A value that the numeric type must be a multiple of.
     */
    private BigDecimal multipleOf;

    // String validations
    /**
     * The minimum length allowed for string types.
     */
    private Integer minLength;

    /**
     * The maximum length allowed for string types.
     */
    private Integer maxLength;

    /**
     * A regular expression pattern that the string must match.
     */
    private String pattern;

    // Array validations
    /**
     * The minimum number of items allowed in an array.
     */
    private Integer minItems;

    /**
     * The maximum number of items allowed in an array.
     */
    private Integer maxItems;

    /**
     * Whether all items in the array must be unique.
     */
    private Boolean uniqueItems;

    // Object properties
    /**
     * A map of property names to their corresponding schemas for objects.
     */
    private Map<String, UnifiedSchema> properties;

    /**
     * A list of required property names for objects.
     */
    private List<String> required;

    /**
     * The minimum number of properties allowed in an object.
     */
    private Integer minProperties;

    /**
     * The maximum number of properties allowed in an object.
     */
    private Integer maxProperties;

    // Additional properties
    /**
     * Specifies whether additional properties are allowed. 
     * Can be a boolean or a schema defining the structure of additional properties.
     */
    private Object additionalProperties;

    /**
     * The schema for additional properties if they are allowed.
     */
    private UnifiedSchema additionalPropertiesSchema;

    // Array items
    /**
     * The schema of items in an array.
     */
    private UnifiedSchema items;

    // Enumerations
    /**
     * A list of allowed values for the schema.
     */
    private List<Object> enumValues;

    // Composition
    /**
     * A list of schemas that must all be satisfied (allOf composition).
     */
    private List<UnifiedSchema> allOf;

    /**
     * A list of schemas where at least one must be satisfied (oneOf composition).
     */
    private List<UnifiedSchema> oneOf;

    /**
     * A list of schemas where any combination can be satisfied (anyOf composition).
     */
    private List<UnifiedSchema> anyOf;

    /**
     * A schema that must not be satisfied (not composition).
     */
    private UnifiedSchema not;

    // Discriminator for inheritance
    /**
     * The discriminator used to differentiate between schemas in polymorphism.
     */
    private UnifiedDiscriminator discriminator;

    // Metadata
    /**
     * Indicates whether the schema is nullable.
     */
    private Boolean nullable;

    /**
     * Indicates whether the schema is read-only.
     */
    private Boolean readOnly;

    /**
     * Indicates whether the schema is write-only.
     */
    private Boolean writeOnly;

    /**
     * Indicates whether the schema is deprecated.
     */
    private Boolean deprecated;

    // XML
    /**
     * XML-specific metadata for the schema.
     */
    private Object xml;

    // Extensions
    /**
     * A map of custom extensions for the schema.
     */
    private Map<String, Object> extensions;
}
