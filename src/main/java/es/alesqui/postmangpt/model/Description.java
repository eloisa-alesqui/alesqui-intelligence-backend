package es.alesqui.postmangpt.model;

import org.springframework.data.mongodb.core.mapping.Field;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Descriptions can be raw text or an object that holds the description content
 * along with its format. This class represents a description object in a
 * Postman collection.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Description {

	/**
     * The content of the description as a raw string. Optional field.
     */
    @Field("content")
    @JsonProperty("content")
    private String content;

    /**
     * The MIME type of the raw description content. For example, text/markdown or
     * text/html. The type is used to correctly render the description when
     * generating documentation in the Postman app. Optional field.
     */
    @Field("type")
    @JsonProperty("type")
    private String type;

    /**
     * Versions associated with this description. Optional field.
     */
    @Field("version")
    @JsonProperty("version")
    private String version;

	/**
	 * Constructor for simple text description
	 * 
	 * @param content The description content
	 */
	public Description(String content) {
		this.content = content;
		this.type = "text/plain"; // Default type
	}

	/**
	 * Constructor for typed description
	 * 
	 * @param content The description content
	 * @param type    The MIME type (e.g., "text/markdown", "text/html")
	 */
	public Description(String content, String type) {
		this.content = content;
		this.type = type;
	}

	// Factory methods

	/**
	 * Creates a description with the given content (default method)
	 * 
	 * @param content The description content
	 * @return Description instance with plain text type
	 */
	public static Description create(String content) {
		return new Description(content, "text/plain");
	}

	/**
	 * Creates a description with content and type
	 * 
	 * @param content The description content
	 * @param type    The MIME type
	 * @return Description instance
	 */
	public static Description create(String content, String type) {
		return new Description(content, type);
	}

	/**
	 * Creates a description with content, type and version
	 * 
	 * @param content The description content
	 * @param type    The MIME type
	 * @param version The version
	 * @return Description instance
	 */
	public static Description create(String content, String type, String version) {
		return Description.builder().content(content).type(type).version(version).build();
	}

	/**
	 * Creates a plain text description
	 * 
	 * @param content The description text
	 * @return Description instance with plain text
	 */
	public static Description text(String content) {
		return new Description(content, "text/plain");
	}

	/**
	 * Creates a markdown description
	 * 
	 * @param content The markdown content
	 * @return Description instance with markdown type
	 */
	public static Description markdown(String content) {
		return new Description(content, "text/markdown");
	}

	/**
	 * Creates an HTML description
	 * 
	 * @param content The HTML content
	 * @return Description instance with HTML type
	 */
	public static Description html(String content) {
		return new Description(content, "text/html");
	}

	/**
	 * Creates a JSON description
	 * 
	 * @param content The JSON content
	 * @return Description instance with JSON type
	 */
	public static Description json(String content) {
		return new Description(content, "application/json");
	}

	/**
	 * Creates an XML description
	 * 
	 * @param content The XML content
	 * @return Description instance with XML type
	 */
	public static Description xml(String content) {
		return new Description(content, "application/xml");
	}

	/**
	 * Creates an empty description
	 * 
	 * @return Empty description instance
	 */
	public static Description empty() {
		return new Description("", "text/plain");
	}

	/**
	 * Sets the content of the description
	 * 
	 * @param content The content
	 * @return this instance for method chaining
	 */
	public Description withContent(String content) {
		this.content = content;
		return this;
	}

	/**
	 * Sets the type of the description
	 * 
	 * @param type The MIME type
	 * @return this instance for method chaining
	 */
	public Description withType(String type) {
		this.type = type;
		return this;
	}

	/**
	 * Sets the version of the description
	 * 
	 * @param version The version
	 * @return this instance for method chaining
	 */
	public Description withVersion(String version) {
		this.version = version;
		return this;
	}

	/**
	 * Checks if the description has content
	 * 
	 * @return true if content is not null and not empty
	 */
	public boolean hasContent() {
		return content != null && !content.trim().isEmpty();
	}

	/**
	 * Checks if the description has a type
	 * 
	 * @return true if type is not null and not empty
	 */
	public boolean hasType() {
		return type != null && !type.trim().isEmpty();
	}

	/**
	 * Checks if the description has a version
	 * 
	 * @return true if version is not null and not empty
	 */
	public boolean hasVersion() {
		return version != null && !version.trim().isEmpty();
	}

	/**
	 * Checks if the description is empty
	 * 
	 * @return true if content is null or empty
	 */
	public boolean isEmpty() {
		return !hasContent();
	}

	/**
	 * Checks if the description is plain text
	 * 
	 * @return true if type is text/plain or null
	 */
	public boolean isPlainText() {
		return type == null || "text/plain".equals(type);
	}

	/**
	 * Checks if the description is markdown
	 * 
	 * @return true if type is text/markdown
	 */
	public boolean isMarkdown() {
		return "text/markdown".equals(type);
	}

	/**
	 * Checks if the description is HTML
	 * 
	 * @return true if type is text/html
	 */
	public boolean isHtml() {
		return "text/html".equals(type);
	}

	/**
	 * Checks if the description is JSON
	 * 
	 * @return true if type is application/json
	 */
	public boolean isJson() {
		return "application/json".equals(type);
	}

	/**
	 * Checks if the description is XML
	 * 
	 * @return true if type is application/xml
	 */
	public boolean isXml() {
		return "application/xml".equals(type);
	}

	// Utility methods

	/**
	 * Gets the content length
	 * 
	 * @return Content length or 0 if content is null
	 */
	public int getContentLength() {
		return content != null ? content.length() : 0;
	}

	/**
	 * Gets a truncated version of the content
	 * 
	 * @param maxLength Maximum length
	 * @return Truncated content
	 */
	public String getTruncatedContent(int maxLength) {
		if (content == null)
			return "";
		if (content.length() <= maxLength)
			return content;
		return content.substring(0, maxLength) + "...";
	}

	/**
	 * Gets the content or a default value if empty
	 * 
	 * @param defaultValue Default value
	 * @return Content or default value
	 */
	public String getContentOrDefault(String defaultValue) {
		return hasContent() ? content : defaultValue;
	}

	/**
	 * Gets the type or a default value if empty
	 * 
	 * @param defaultType Default type
	 * @return Type or default type
	 */
	public String getTypeOrDefault(String defaultType) {
		return hasType() ? type : defaultType;
	}

	/**
	 * Converts the description to a simple string representation
	 * 
	 * @return String representation
	 */
	public String toSimpleString() {
		return content != null ? content : "";
	}

	/**
	 * Creates a copy of this description
	 * 
	 * @return Copy of this description
	 */
	public Description copy() {
		return Description.builder().content(this.content).type(this.type).version(this.version).build();
	}

	/**
	 * Validates the description
	 * 
	 * @return true if description is valid (has content)
	 */
	public boolean isValid() {
		return hasContent();
	}

	/**
	 * Gets description summary
	 * 
	 * @return Description summary
	 */
	public String getSummary() {
		return String.format("Description: %s (Type: %s, Length: %d)", getTruncatedContent(50),
				getTypeOrDefault("text/plain"), getContentLength());
	}

	/**
	 * Converts to string representation
	 */
	@Override
	public String toString() {
		if (content == null)
			return "Description: [empty]";

		StringBuilder sb = new StringBuilder();
		sb.append("Description: ");
		sb.append(getTruncatedContent(100));

		if (hasType()) {
			sb.append(" (").append(type).append(")");
		}

		if (hasVersion()) {
			sb.append(" [v").append(version).append("]");
		}

		return sb.toString();
	}

	/**
	 * Merges multiple descriptions into one
	 * 
	 * @param descriptions Descriptions to merge
	 * @param separator    Separator between descriptions
	 * @return Merged description
	 */
	public static Description merge(java.util.List<Description> descriptions, String separator) {
		if (descriptions == null || descriptions.isEmpty()) {
			return Description.empty();
		}

		StringBuilder merged = new StringBuilder();
		String sep = separator != null ? separator : "\n\n";

		for (int i = 0; i < descriptions.size(); i++) {
			Description desc = descriptions.get(i);
			if (desc != null && desc.hasContent()) {
				if (i > 0) {
					merged.append(sep);
				}
				merged.append(desc.getContent());
			}
		}

		return Description.create(merged.toString());
	}

	/**
	 * Creates a description with automatic type detection
	 * 
	 * @param content Content to analyze
	 * @return Description with detected type
	 */
	public static Description autoDetect(String content) {
		if (content == null || content.trim().isEmpty()) {
			return Description.empty();
		}

		String trimmed = content.trim();

		// HTML detection
		if (trimmed.startsWith("<") && trimmed.endsWith(">")) {
			return Description.html(content);
		}

		// JSON detection
		if ((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
			return Description.json(content);
		}

		// XML detection
		if (trimmed.startsWith("<?xml") || (trimmed.startsWith("<") && !trimmed.contains("</"))) {
			return Description.xml(content);
		}

		// Markdown detection (basic)
		if (content.contains("# ") || content.contains("## ") || content.contains("**") || content.contains("*")
				|| content.contains("`") || content.contains("```")) {
			return Description.markdown(content);
		}

		// Default to plain text
		return Description.text(content);
	}
}