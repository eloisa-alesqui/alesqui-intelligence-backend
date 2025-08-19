package es.alesqui.intelligence.model.unified;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Represents a parameter for an API endpoint.
 * Parameters can be located in different parts of the request:
 * path, query string, headers, or cookies.
 * 
 * This unified model combines parameter definitions from both
 * OpenAPI/Swagger and Postman specifications.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UnifiedParameter {
    
    /**
     * The name of the parameter.
     * Must be unique within the parameter location (in).
     * Case sensitive based on the location rules.
     */
    private String name;
    
    /**
     * Location of the parameter in the request.
     * Valid values: "path", "query", "header", "cookie"
     * - path: Part of the URL path (e.g., /users/{id})
     * - query: Query string parameter (e.g., ?sort=name)
     * - header: HTTP header
     * - cookie: HTTP cookie value
     */
    private String in;
    
    /**
     * Description of the parameter's purpose and usage.
     * Should explain what values are acceptable and their effects.
     * May include markdown formatting.
     */
    private String description;
    
    /**
     * Whether this parameter is mandatory.
     * Path parameters are always required.
     * Query, header, and cookie parameters can be optional.
     */
    private boolean required;
    
    /**
     * The data type of the parameter.
     * Basic types: "string", "number", "integer", "boolean", "array", "object"
     * For complex types, use the schema property instead.
     */
    private String type;
    
    /**
     * Additional format specification for the type.
     * Examples:
     * - For "integer": "int32", "int64"
     * - For "string": "date", "date-time", "email", "uuid"
     * - For "number": "float", "double"
     */
    private String format;
    
    /**
     * Default value for the parameter if not provided.
     * Must be valid according to the parameter's type and format.
     * Only applies to optional parameters.
     */
    private Object defaultValue;
    
    /**
     * Example value for documentation and testing.
     * Should be a realistic value that passes validation.
     * Helps API consumers understand expected values.
     */
    private Object example;
    
    /**
     * List of allowed values for this parameter.
     * When specified, the parameter value must be one of these values.
     * Useful for parameters with a fixed set of valid options.
     */
    private List<Object> enumValues;
    
    /**
     * Detailed schema definition for complex parameter types.
     * Used when simple type/format is insufficient.
     * Allows for nested objects, arrays, and advanced validations.
     */
    private UnifiedSchema schema;
}
