package es.alesqui.postmangpt.model.unified;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Represents a discriminator for OpenAPI schemas.
 * A discriminator is used in polymorphism to differentiate between multiple possible schemas.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UnifiedDiscriminator {

    /**
     * The name of the property in the schema used as the discriminator.
     * This property determines which schema to use when multiple schemas are possible.
     */
    private String propertyName;

    /**
     * A mapping of discriminator values to schema references.
     * For example, this can map a value like "cat" to a specific schema reference such as "#/components/schemas/Cat".
     */
    private Map<String, String> mapping;
}

