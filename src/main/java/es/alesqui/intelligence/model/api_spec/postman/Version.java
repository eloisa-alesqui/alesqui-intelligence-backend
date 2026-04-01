package es.alesqui.intelligence.model.api_spec.postman;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Version information for Postman collections
 * 
 * You can version your collections as they grow, and this field holds the
 * version number. While optional, it's recommended that you use this field to
 * its fullest extent.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Version {

	/**
	 * Increment this number if you make changes to the collection that change its
	 * behavior. For example, removing or adding new test scripts.
	 */
	@Field("major")
	@JsonProperty("major")
	private Integer major;

	/**
	 * Increment this number if you make changes that don't break anything that uses
	 * the collection. For example, removing a folder.
	 */
	@Field("minor")
	@JsonProperty("minor")
	private Integer minor;

	/**
	 * Increment this number if you make minor changes to a collection.
	 */
	@Field("patch")
	@JsonProperty("patch")
	private Integer patch;

	/**
	 * A human-friendly identifier to make sense of the version numbers. For
	 * example, "beta-3".
	 */
	@Field("identifier")
	@JsonProperty("identifier")
	private String identifier;

	/**
	 * Any additional info relating to this version.
	 */
	@Field("meta")
	@JsonProperty("meta")
	private Map<String, Object> meta;

	/**
	 * Creates a copy of this Version
	 * 
	 * @return A new Version instance with the same values
	 */
	public Version copy() {
		return Version.builder().major(this.major).minor(this.minor).patch(this.patch).identifier(this.identifier)
				.meta(this.meta != null ? Map.copyOf(this.meta) : null).build();
	}

	/**
	 * Gets the version as a semantic version string (major.minor.patch)
	 * 
	 * @return Version string or null if major version is not set
	 */
	public String getSemanticVersion() {
		if (major == null) {
			return null;
		}

		StringBuilder sb = new StringBuilder();
		sb.append(major);

		if (minor != null) {
			sb.append(".").append(minor);

			if (patch != null) {
				sb.append(".").append(patch);
			}
		}

		return sb.toString();
	}

	/**
	 * Gets the full version string including identifier if present
	 * 
	 * @return Full version string
	 */
	public String getFullVersion() {
		String semantic = getSemanticVersion();
		if (semantic == null) {
			return identifier;
		}

		if (identifier != null && !identifier.trim().isEmpty()) {
			return semantic + "-" + identifier;
		}

		return semantic;
	}

	/**
	 * Checks if this version is valid (has at least major version)
	 * 
	 * @return true if version has major number
	 */
	public boolean isValid() {
		return major != null;
	}

	/**
	 * Creates a new version with incremented major number
	 * 
	 * @return New Version with major incremented
	 */
	public Version incrementMajor() {
		return Version.builder().major(major != null ? major + 1 : 1).minor(0).patch(0).identifier(this.identifier)
				.meta(this.meta).build();
	}

	/**
	 * Creates a new version with incremented minor number
	 * 
	 * @return New Version with minor incremented
	 */
	public Version incrementMinor() {
		return Version.builder().major(major != null ? major : 1).minor(minor != null ? minor + 1 : 1).patch(0)
				.identifier(this.identifier).meta(this.meta).build();
	}

	/**
	 * Creates a new version with incremented patch number
	 * 
	 * @return New Version with patch incremented
	 */
	public Version incrementPatch() {
		return Version.builder().major(major != null ? major : 1).minor(minor != null ? minor : 0)
				.patch(patch != null ? patch + 1 : 1).identifier(this.identifier).meta(this.meta).build();
	}

	/**
	 * Creates a version from string (e.g., "1.0.0")
	 * 
	 * @param versionString Version string
	 * @return Version instance
	 */
	public static Version fromString(String versionString) {
		if (versionString == null || versionString.trim().isEmpty()) {
			return Version.builder().major(1).minor(0).patch(0).build();
		}

		String[] parts = versionString.split("\\.");
		try {
			Integer major = parts.length > 0 ? Integer.parseInt(parts[0]) : 1;
			Integer minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
			Integer patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;

			return Version.builder().major(major).minor(minor).patch(patch).build();
		} catch (NumberFormatException e) {
			return Version.builder().major(1).minor(0).patch(0).build();
		}
	}

	@Override
	public String toString() {
		return getFullVersion();
	}
}
