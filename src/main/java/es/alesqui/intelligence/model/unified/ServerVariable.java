package es.alesqui.intelligence.model.unified;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Represents a variable that can be used in a server URL template.
 * Server variables allow for parameterized server configurations.
 * 
 * For example, if a server URL is "https://{environment}.api.com",
 * the environment variable might have enum values like ["prod", "staging", "dev"]
 * with "prod" as the default.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ServerVariable {
    
    /**
     * The default value to use for substitution if no value is provided.
     * This value MUST be provided and SHOULD be one of the enum values if enum is defined.
     */
    private String defaultValue;
    
    /**
     * An optional list of allowed values for this variable.
     * If provided, the variable value must be one of these values.
     * Example: ["prod", "staging", "dev"] for an environment variable
     */
    private List<String> enumValues;
    
    /**
     * Optional description for the server variable.
     * Helps API consumers understand the purpose and usage of the variable.
     * Example: "The target environment for API requests"
     */
    private String description;
}
