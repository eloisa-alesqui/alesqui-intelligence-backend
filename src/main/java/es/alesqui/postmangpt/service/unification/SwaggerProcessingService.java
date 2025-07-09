package es.alesqui.postmangpt.service.unification;

import es.alesqui.postmangpt.annotation.HandleApiUnificationException;
import es.alesqui.postmangpt.model.unified.*;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.servers.ServerVariables;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service responsible for processing and extracting information from Swagger (OpenAPI) documents.
 * This service includes methods to extract metadata, endpoints, authentication schemes, and server configurations,
 * and convert them into a unified API format.
 */
@Service
@Slf4j
public class SwaggerProcessingService {

    /**
     * Extracts information from a Swagger (OpenAPI) document and populates the unified API document builder.
     * 
     * @param builder The builder for the unified API document.
     * @param openApi The OpenAPI object containing Swagger specifications.
     */
    @HandleApiUnificationException
    public void extractSwaggerInfo(UnifiedApiDocument.UnifiedApiDocumentBuilder builder, OpenAPI openApi) {
        if (builder == null) {
            throw new IllegalArgumentException("Builder cannot be null");
        }

        if (openApi == null) {
            log.debug("OpenAPI object is null, skipping Swagger info extraction");
            return;
        }

        log.debug("Extracting Swagger/OpenAPI information");
        extractBasicApiInfo(builder, openApi);
        extractServerConfigurations(builder, openApi);
        extractSchemaDefinitions(builder, openApi);
        extractAuthenticationSchemes(builder, openApi);

        log.debug("Successfully completed Swagger info extraction");
    }

    /**
     * Extracts basic API metadata such as title, version, and description from the OpenAPI object.
     * 
     * @param builder The builder for the unified API document.
     * @param openApi The OpenAPI object containing metadata.
     */
    @HandleApiUnificationException
    public void extractBasicApiInfo(UnifiedApiDocument.UnifiedApiDocumentBuilder builder, OpenAPI openApi) {
        if (openApi.getInfo() == null) {
            log.debug("OpenAPI info section is null, skipping basic info extraction");
            return;
        }

        var info = openApi.getInfo();
        log.debug("Processing OpenAPI info section");

        if (info.getVersion() != null && !info.getVersion().trim().isEmpty()) {
            builder.version(info.getVersion().trim());
            log.trace("Extracted API version: {}", info.getVersion());
        } else {
            log.warn("No version found in OpenAPI info section");
        }

        log.debug("Completed basic API info extraction");
    }

    /**
     * Extracts server configurations from the OpenAPI object and converts them into a unified format.
     * 
     * @param builder The builder for the unified API document.
     * @param openApi The OpenAPI object containing server configurations.
     */
    @HandleApiUnificationException
    public void extractServerConfigurations(UnifiedApiDocument.UnifiedApiDocumentBuilder builder, OpenAPI openApi) {
        if (openApi.getServers() == null || openApi.getServers().isEmpty()) {
            log.debug("No servers defined in OpenAPI specification");
            return;
        }

        List<Server> servers = openApi.getServers();
        log.debug("Processing {} server configurations", servers.size());

        List<ApiServer> unifiedServers = convertServers(servers);
        if (unifiedServers != null && !unifiedServers.isEmpty()) {
            builder.servers(unifiedServers);
            log.debug("Converted {} servers to unified format", unifiedServers.size());
        }

        Server primaryServer = servers.get(0);
        if (primaryServer.getUrl() != null && !primaryServer.getUrl().trim().isEmpty()) {
            String baseUrl = primaryServer.getUrl().trim();
            builder.baseUrl(baseUrl);
            log.debug("Set primary base URL: {}", baseUrl);
        } else {
            log.warn("Primary server has null or empty URL");
        }
    }
    
    /**
     * Extracts and converts all schemas defined in the `schemas` component of the OpenAPI specification.
     * These schemas represent reusable data models in the API.
     */
    @HandleApiUnificationException
    public void extractSchemaDefinitions(UnifiedApiDocument.UnifiedApiDocumentBuilder builder, OpenAPI openApi) {
        if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
            log.debug("No schema definitions found in OpenAPI specification");
            return;
        }

        Map<String, Schema> openApiSchemas = openApi.getComponents().getSchemas();
        log.info("Processing {} schema definitions", openApiSchemas.size());

        Map<String, UnifiedSchema> unifiedSchemas = new HashMap<>();
        
        // Convert each schema
        openApiSchemas.forEach((schemaName, schema) -> {
            try {
                log.debug("Converting schema: {}", schemaName);
                UnifiedSchema unifiedSchema = convertSwaggerSchema(schema, schemaName, new HashSet<>());
                unifiedSchemas.put(schemaName, unifiedSchema);
                log.debug("Successfully converted schema: {}", schemaName);
            } catch (Exception e) {
                log.error("Error converting schema '{}': {}", schemaName, e.getMessage(), e);
            }
        });

        if (!unifiedSchemas.isEmpty()) {
            builder.schemas(unifiedSchemas);
            log.info("Successfully extracted {} schema definitions", unifiedSchemas.size());
        }
    }

    /**
     * Improved version of the `convertSwaggerSchema` method that handles references 
     * and prevents infinite recursion.
     */
    private UnifiedSchema convertSwaggerSchema(Schema<?> schema, String schemaName, Set<String> visitedRefs) {
        if (schema == null) {
            return null;
        }

        // Handle references to avoid infinite recursion
        if (schema.get$ref() != null) {
            String ref = schema.get$ref();
            String refName = extractRefName(ref);
            
            if (visitedRefs.contains(refName)) {
                log.debug("Circular reference detected for schema: {}", refName);
                return UnifiedSchema.builder()
                    .type("object")
                    .description("Reference to: " + refName)
                    .ref(ref)
                    .build();
            }
            
            // Temporarily add to visited references
            Set<String> newVisited = new HashSet<>(visitedRefs);
            newVisited.add(refName);
            
            // If the referenced schema is accessible, process it
            // Otherwise, just save the reference
            return UnifiedSchema.builder()
                .ref(ref)
                .description("Reference to schema: " + refName)
                .build();
        }

        UnifiedSchema.UnifiedSchemaBuilder builder = UnifiedSchema.builder()
            .type(schema.getType())
            .format(schema.getFormat())
            .title(schema.getTitle())
            .description(schema.getDescription())
            .example(schema.getExample())
            .defaultValue(schema.getDefault())
            .pattern(schema.getPattern())
            .minimum(schema.getMinimum())
            .maximum(schema.getMaximum())
            .minLength(schema.getMinLength())
            .maxLength(schema.getMaxLength())
            .nullable(schema.getNullable())
            .readOnly(schema.getReadOnly())
            .writeOnly(schema.getWriteOnly())
            .deprecated(schema.getDeprecated());

        // Handle enumerations
        if (schema.getEnum() != null && !schema.getEnum().isEmpty()) {
            builder.enumValues(new ArrayList<>(schema.getEnum()));
        }

        // Handle properties for objects
        if ("object".equals(schema.getType()) && schema.getProperties() != null) {
            Map<String, UnifiedSchema> properties = new HashMap<>();
            schema.getProperties().forEach((propName, propSchema) -> {
                UnifiedSchema propUnified = convertSwaggerSchema((Schema<?>) propSchema, 
                    schemaName + "." + propName, visitedRefs);
                properties.put(propName, propUnified);
            });
            builder.properties(properties);
        }

        // Handle required fields
        if (schema.getRequired() != null && !schema.getRequired().isEmpty()) {
            builder.required(new ArrayList<>(schema.getRequired()));
        }

        // Handle arrays
        if ("array".equals(schema.getType()) && schema.getItems() != null) {
            builder.items(convertSwaggerSchema(schema.getItems(), schemaName + "[]", visitedRefs));
        }

        // Handle allOf, oneOf, anyOf
        if (schema.getAllOf() != null && !schema.getAllOf().isEmpty()) {
            List<UnifiedSchema> allOf = schema.getAllOf().stream()
                .map(s -> convertSwaggerSchema(s, schemaName + ".allOf", visitedRefs))
                .collect(Collectors.toList());
            builder.allOf(allOf);
        }

        if (schema.getOneOf() != null && !schema.getOneOf().isEmpty()) {
            List<UnifiedSchema> oneOf = schema.getOneOf().stream()
                .map(s -> convertSwaggerSchema(s, schemaName + ".oneOf", visitedRefs))
                .collect(Collectors.toList());
            builder.oneOf(oneOf);
        }

        if (schema.getAnyOf() != null && !schema.getAnyOf().isEmpty()) {
            List<UnifiedSchema> anyOf = schema.getAnyOf().stream()
                .map(s -> convertSwaggerSchema(s, schemaName + ".anyOf", visitedRefs))
                .collect(Collectors.toList());
            builder.anyOf(anyOf);
        }

        // Handle discriminator
        if (schema.getDiscriminator() != null) {
            builder.discriminator(convertDiscriminator(schema.getDiscriminator()));
        }

        // Handle additional properties
        if (schema.getAdditionalProperties() != null) {
            if (schema.getAdditionalProperties() instanceof Boolean) {
                builder.additionalProperties(schema.getAdditionalProperties());
            } else if (schema.getAdditionalProperties() instanceof Schema) {
                builder.additionalPropertiesSchema(
                    convertSwaggerSchema((Schema<?>) schema.getAdditionalProperties(), 
                        schemaName + ".additionalProperties", visitedRefs)
                );
            }
        }

        return builder.build();
    }

    /**
     * Extracts the schema name from a reference.
     */
    private String extractRefName(String ref) {
        if (ref == null) return null;
        // References typically have the format "#/components/schemas/SchemaName"
        String[] parts = ref.split("/");
        return parts.length > 0 ? parts[parts.length - 1] : ref;
    }

    /**
     * Converts the OpenAPI discriminator into a unified discriminator.
     */
    private UnifiedDiscriminator convertDiscriminator(io.swagger.v3.oas.models.media.Discriminator discriminator) {
        if (discriminator == null) return null;
        
        return UnifiedDiscriminator.builder()
            .propertyName(discriminator.getPropertyName())
            .mapping(discriminator.getMapping())
            .build();
    }

    /**
     * Extracts authentication schemes from the OpenAPI components and adds them to the unified API document.
     * 
     * @param builder The builder for the unified API document.
     * @param openApi The OpenAPI object containing authentication configurations.
     */
    @HandleApiUnificationException
    public void extractAuthenticationSchemes(UnifiedApiDocument.UnifiedApiDocumentBuilder builder, OpenAPI openApi) {
        if (openApi.getComponents() == null) {
            log.debug("No components section found in OpenAPI specification");
            return;
        }

        if (openApi.getComponents().getSecuritySchemes() == null
                || openApi.getComponents().getSecuritySchemes().isEmpty()) {
            log.debug("No security schemes defined in OpenAPI components");
            return;
        }

        Map<String, SecurityScheme> securitySchemes = openApi.getComponents().getSecuritySchemes();
        log.debug("Processing {} security schemes", securitySchemes.size());

        UnifiedAuthentication authentication = extractAuthentication(securitySchemes);
        if (authentication != null) {
            builder.authentication(authentication);
            log.debug("Successfully extracted authentication configuration");
        } else {
            log.debug("No valid authentication schemes found");
        }
    }

    /**
     * Converts a list of Swagger servers into a unified format.
     * 
     * @param servers The list of Swagger servers.
     * @return A list of unified API servers.
     */
    @HandleApiUnificationException
    public List<ApiServer> convertServers(List<Server> servers) {
        if (servers == null || servers.isEmpty()) {
            log.debug("No servers to convert, returning empty list");
            return new ArrayList<>();
        }

        return servers.stream()
                .map(server -> ApiServer.builder()
                        .url(server.getUrl())
                        .description(server.getDescription())
                        .variables(convertServerVariables(server.getVariables()))
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Converts Swagger server variables into a unified format.
     * 
     * @param variables The Swagger server variables.
     * @return A map of unified server variables.
     */
    @HandleApiUnificationException
    public Map<String, ServerVariable> convertServerVariables(ServerVariables variables) {
        if (variables == null) {
            log.debug("No server variables to convert, returning null");
            return null;
        }

        Map<String, ServerVariable> result = new HashMap<>();
        variables.forEach((key, var) -> result.put(key, ServerVariable.builder()
                .defaultValue(var.getDefault())
                .enumValues(var.getEnum())
                .description(var.getDescription())
                .build()));
        return result;
    }

    /**
     * Extracts authentication schemes from Swagger security schemes.
     * 
     * @param securitySchemes The security schemes defined in Swagger.
     * @return A unified authentication object.
     */
    @HandleApiUnificationException
    public UnifiedAuthentication extractAuthentication(Map<String, SecurityScheme> securitySchemes) {
        if (securitySchemes == null || securitySchemes.isEmpty()) {
            log.debug("No security schemes available for authentication extraction");
            return null;
        }

        Map.Entry<String, SecurityScheme> entry = securitySchemes.entrySet().iterator().next();
        SecurityScheme scheme = entry.getValue();

        return UnifiedAuthentication.builder()
                .type(scheme.getType() != null ? scheme.getType().toString() : null)
                .scheme(scheme.getScheme())
                .bearerFormat(scheme.getBearerFormat())
                .name(scheme.getName())
                .in(scheme.getIn() != null ? scheme.getIn().toString() : null)
                .build();
    }
 
}
