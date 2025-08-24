package es.alesqui.intelligence.model.unified;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Represents a unified API document that consolidates information from a SwaggerDocument
 * and a PostmanDocument into a single structure.
 * This class is used for managing API specifications in a unified format.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "unified_api_specs")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UnifiedApiDocument {

    /**
     * The unique identifier of the unified API document.
     */
    @Id
    private ObjectId id;

    /**
     * The name of the API specification.
     * This field is indexed and must be unique.
     */
    @Field("name")
    @JsonProperty("name")
    @Indexed(unique = true)
    private String name;

    /**
     * A brief description of the API.
     */
    @Field("description")
    @JsonProperty("description")
    private String description;

    /**
     * The version of the API specification.
     */
    @Field("version")
    @JsonProperty("version")
    private String version;

    /**
     * The base URL of the API.
     */
    @Field("baseUrl")
    @JsonProperty("baseUrl")
    private String baseUrl;

    /**
     * A list of servers that host the API.
     */
    @Field("servers")
    @JsonProperty("servers")
    private List<ApiServer> servers;

    /**
     * A list of endpoints defined in the API.
     */
    @Field("endpoints")
    @JsonProperty("endpoints")
    private List<UnifiedEndpoint> endpoints;

    /**
     * A map of schema definitions used in the API, where the key is the schema name
     * and the value is the corresponding schema object.
     */
    @Field("schemas")
    @JsonProperty("schemas")
    private Map<String, UnifiedSchema> schemas;

    /**
     * Authentication information for the API, such as API keys or OAuth configurations.
     */
    @Field("authentication")
    @JsonProperty("authentication")
    private UnifiedAuthentication authentication;

    /**
     * A map of global headers that are applied to all requests in the API.
     */
    @Field("globalHeaders")
    @JsonProperty("globalHeaders")
    private Map<String, String> globalHeaders;

    /**
     * A map of global variables that can be used throughout the API specification.
     */
    @Field("globalVariables")
    @JsonProperty("globalVariables")
    private Map<String, Object> globalVariables;

    /**
     * A list of tags associated with the API for categorization or organization.
     */
    @Field("tags")
    @JsonProperty("tags")
    private List<String> tags;

    /**
     * The name of the team or group responsible for maintaining the API.
     */
    @Field("team")
    @JsonProperty("team")
    private String team;

    /**
     * The unique identifier of the source Postman collection that this document is based on.
     */
    @Field("sourcePostmanId")
    @JsonProperty("sourcePostmanId")
    private String sourcePostmanId;

    /**
     * The unique identifier of the source Swagger/OpenAPI document that this document is based on.
     */
    @Field("sourceSwaggerId")
    @JsonProperty("sourceSwaggerId")
    private String sourceSwaggerId;

    /**
     * The timestamp when this document was created.
     * Defaults to the current time when the document is created.
     */
    @Field("createdAt")
    @JsonProperty("createdAt")
    @Builder.Default
    private Instant createdAt = Instant.now();

    /**
     * The timestamp when this document was last updated.
     * Defaults to the current time when the document is modified.
     */
    @Field("updatedAt")
    @JsonProperty("updatedAt")
    @Builder.Default
    private Instant updatedAt = Instant.now();

    /**
     * The user or entity that created this document.
     */
    @Field("createdBy")
    @JsonProperty("createdBy")
    private String createdBy;

    /**
     * The user or entity that last modified this document.
     */
    @Field("lastModifiedBy")
    @JsonProperty("lastModifiedBy")
    private String lastModifiedBy;

    /**
     * Indicates whether this document is currently active.
     * Defaults to true.
     */
    @Field("active")
    @JsonProperty("active")
    @Builder.Default
    private boolean active = true;
    
    /**
     * Execution configuration for API calls
     */
    @Field("apiConfiguration")
    @JsonProperty("apiConfiguration")
    private ApiConfiguration apiConfiguration;
    
    /**
     * A summary of the API's main capabilities, generated by an AI model.
     * This field is pre-calculated during the document unification process to provide
     * a quick, high-level overview of what the API can do.
     * It is used by AI tools to quickly determine if an API is suitable for a user's query.
     */
    @Field("capabilitiesSummary")
    @JsonProperty("capabilitiesSummary")
    private String capabilitiesSummary;
}
