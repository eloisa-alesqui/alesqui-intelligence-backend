package es.alesqui.postmangpt.model.postman;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Field;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Postman Collection Info - Contains collection metadata
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Info {

	/**
	 * Collection name
	 */
	@Field("name")
	@JsonProperty("name")
	@Indexed(unique = true)
	private String name;

	/**
	 * Collection description
	 */
	@Field("description")
	@JsonProperty("description")
	private Description description;

	/**
	 * Collection version
	 */
	@Field("version")
	@JsonProperty("version")
	private Version version;

	/**
	 * Collection schema URL
	 */
	@Field("schema")
	@JsonProperty("schema")
	private String schema;

	/**
	 * Postman collection ID
	 */
	@Field("_postman_id")
	@JsonProperty("_postman_id")
	private String postmanId;

	// Factory methods

	/**
	 * Creates basic info with name
	 * 
	 * @param name Collection name
	 * @return Info instance
	 */
	public static Info create(String name) {
		return Info.builder().name(name).schema("https://schema.getpostman.com/json/collection/v2.1.0/collection.json")
				.version(Version.builder().major(1).minor(0).patch(0).build()).build();
	}

	/**
	 * Creates info with name and description
	 * 
	 * @param name        Collection name
	 * @param description Collection description
	 * @return Info instance
	 */
	public static Info create(String name, String description) {
		return Info.builder().name(name).description(Description.create(description))
				.schema("https://schema.getpostman.com/json/collection/v2.1.0/collection.json")
				.version(Version.builder().major(1).minor(0).patch(0).build()).build();
	}

	/**
	 * Creates info with name, description and version
	 * 
	 * @param name        Collection name
	 * @param description Collection description
	 * @param version     Collection version
	 * @return Info instance
	 */
	public static Info create(String name, String description, Version version) {
		return Info.builder().name(name).description(Description.create(description))
				.schema("https://schema.getpostman.com/json/collection/v2.1.0/collection.json").version(version)
				.build();
	}

	// Fluent API methods

	/**
	 * Sets the collection name
	 * 
	 * @param name Collection name
	 * @return this instance for method chaining
	 */
	public Info withName(String name) {
		this.name = name;
		return this;
	}

	/**
	 * Sets the collection description
	 * 
	 * @param description Collection description
	 * @return this instance for method chaining
	 */
	public Info withDescription(String description) {
		this.description = Description.create(description);
		return this;
	}

	/**
	 * Sets the collection description
	 * 
	 * @param description Description object
	 * @return this instance for method chaining
	 */
	public Info withDescription(Description description) {
		this.description = description;
		return this;
	}

	/**
	 * Sets the collection version
	 * 
	 * @param version Version object
	 * @return this instance for method chaining
	 */
	public Info withVersion(Version version) {
		this.version = version;
		return this;
	}

	/**
	 * Sets the collection version from string
	 * 
	 * @param versionString Version string (e.g., "1.0.0")
	 * @return this instance for method chaining
	 */
	public Info withVersion(String versionString) {
		this.version = Version.fromString(versionString);
		return this;
	}

	/**
	 * Sets the collection version with major, minor, patch
	 * 
	 * @param major Major version
	 * @param minor Minor version
	 * @param patch Patch version
	 * @return this instance for method chaining
	 */
	public Info withVersion(int major, int minor, int patch) {
		this.version = Version.builder().major(major).minor(minor).patch(patch).build();
		return this;
	}

	/**
	 * Sets the collection schema
	 * 
	 * @param schema Schema URL
	 * @return this instance for method chaining
	 */
	public Info withSchema(String schema) {
		this.schema = schema;
		return this;
	}

	/**
	 * Sets the Postman collection ID
	 * 
	 * @param postmanId Postman collection ID
	 * @return this instance for method chaining
	 */
	public Info withPostmanId(String postmanId) {
		this.postmanId = postmanId;
		return this;
	}

	// Query methods

	/**
	 * Gets the collection name
	 * 
	 * @return Collection name
	 */
	public String getName() {
		return name;
	}

	/**
	 * Gets the collection description content
	 * 
	 * @return Description content
	 */
	public String getDescriptionContent() {
		return description != null ? description.getContent() : null;
	}

	/**
	 * Gets the collection version as string
	 * 
	 * @return Version string
	 */
	public String getVersionString() {
		return version != null ? version.toString() : "1.0.0";
	}

	/**
	 * Checks if the info has a description
	 * 
	 * @return true if description exists
	 */
	public boolean hasDescription() {
		return description != null && description.getContent() != null && !description.getContent().trim().isEmpty();
	}

	/**
	 * Checks if the info has a version
	 * 
	 * @return true if version exists
	 */
	public boolean hasVersion() {
		return version != null;
	}

	/**
	 * Checks if the info has a Postman ID
	 * 
	 * @return true if Postman ID exists
	 */
	public boolean hasPostmanId() {
		return postmanId != null && !postmanId.trim().isEmpty();
	}

	/**
	 * Checks if the info has a schema
	 * 
	 * @return true if schema exists
	 */
	public boolean hasSchema() {
		return schema != null && !schema.trim().isEmpty();
	}

	// Utility methods

	/**
	 * Validates the info structure
	 * 
	 * @return true if valid
	 */
	public boolean isValid() {
		return name != null && !name.trim().isEmpty() && schema != null && !schema.trim().isEmpty();
	}

	/**
	 * Creates a copy of this info
	 * 
	 * @return Copy of this info
	 */
	public Info copy() {
		return Info.builder().name(this.name).description(this.description != null ? this.description.copy() : null)
				.version(this.version != null ? this.version.copy() : null).schema(this.schema)
				.postmanId(this.postmanId).build();
	}

	/**
	 * Gets info summary
	 * 
	 * @return Info summary as string
	 */
	public String getSummary() {
		return String.format("Info: %s (v%s)\n" + "Description: %s\n" + "Schema: %s\n" + "Postman ID: %s", name,
				getVersionString(), hasDescription() ? getDescriptionContent() : "None", schema,
				hasPostmanId() ? postmanId : "None");
	}

	/**
	 * Sets default values for missing fields
	 * 
	 * @return this instance for method chaining
	 */
	public Info withDefaults() {
		if (schema == null || schema.trim().isEmpty()) {
			this.schema = "https://schema.getpostman.com/json/collection/v2.1.0/collection.json";
		}
		if (version == null) {
			this.version = Version.builder().major(1).minor(0).patch(0).build();
		}
		return this;
	}
}