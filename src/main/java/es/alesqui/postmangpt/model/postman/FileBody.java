package es.alesqui.postmangpt.model.postman;

import org.springframework.data.mongodb.core.mapping.Field;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * File body content for binary file uploads
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FileBody {

	/**
	 * File source path or identifier
	 */
	@Field("src")
	@JsonProperty("src")
	private String src;

	/**
	 * File content (base64 encoded for binary data)
	 */
	@Field("content")
	@JsonProperty("content")
	private String content;

	/**
	 * File name
	 */
	@Field("name")
	@JsonProperty("name")
	private String name;

	/**
	 * File content type/MIME type
	 */
	@Field("type")
	@JsonProperty("type")
	private String type;

	/**
	 * File size in bytes
	 */
	@Field("size")
	@JsonProperty("size")
	private Long size;

    /**
     * Constructor for file body with source
     * 
     * @param src File source path
     */
    public FileBody(String src) {
        this.src = src;
    }

    /**
     * Constructor for file body with source and name
     * 
     * @param src  File source path
     * @param name File name
     */
    public FileBody(String src, String name) {
        this.src = src;
        this.name = name;
    }

    /**
     * Creates a file body with source
     * 
     * @param src File source path
     * @return FileBody instance
     */
    public static FileBody of(String src) {
        return new FileBody(src);
    }

    /**
     * Creates a file body with source and name
     * 
     * @param src  File source path
     * @param name File name
     * @return FileBody instance
     */
    public static FileBody of(String src, String name) {
        return new FileBody(src, name);
    }

    /**
     * Creates a file body with source, name and type
     * 
     * @param src  File source path
     * @param name File name
     * @param type Content type
     * @return FileBody instance
     */
    public static FileBody of(String src, String name, String type) {
        return FileBody.builder().src(src).name(name).type(type).build();
    }

    /**
     * Creates a file body from local file path
     * 
     * @param filePath Local file path
     * @return FileBody instance
     */
    public static FileBody fromPath(String filePath) {
        String fileName = extractFileName(filePath);
        return FileBody.builder().src(filePath).name(fileName).build();
    }

    /**
     * Creates a file body with base64 content
     * 
     * @param name    File name
     * @param content Base64 encoded content
     * @param type    Content type
     * @return FileBody instance
     */
    public static FileBody withContent(String name, String content, String type) {
        return FileBody.builder().name(name).content(content).type(type).build();
    }

    /**
     * Sets the file name
     * 
     * @param name File name
     * @return this instance for method chaining
     */
    public FileBody withName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Sets the content type
     * 
     * @param type Content type
     * @return this instance for method chaining
     */
    public FileBody withType(String type) {
        this.type = type;
        return this;
    }

    /**
     * Sets the file size
     * 
     * @param size File size in bytes
     * @return this instance for method chaining
     */
    public FileBody withSize(Long size) {
        this.size = size;
        return this;
    }

    /**
     * Sets the file content (base64 encoded)
     * 
     * @param content Base64 encoded content
     * @return this instance for method chaining
     */
    public FileBody withContent(String content) {
        this.content = content;
        return this;
    }

    /**
     * Creates a copy of this file body
     * 
     * @return Copy of this file body
     */
    public FileBody copy() {
        return FileBody.builder()
                .src(this.src)
                .content(this.content)
                .name(this.name)
                .type(this.type)
                .size(this.size)
                .build();
    }

    /**
     * Creates a copy with new source
     * 
     * @param newSrc The new source for the copy
     * @return Copy of this file body with new source
     */
    public FileBody copyWithSrc(String newSrc) {
        FileBody copy = copy();
        copy.src = newSrc;
        return copy;
    }

    /**
     * Creates a copy with new content
     * 
     * @param newContent The new content for the copy
     * @return Copy of this file body with new content
     */
    public FileBody copyWithContent(String newContent) {
        FileBody copy = copy();
        copy.content = newContent;
        return copy;
    }

    /**
     * Creates a copy with new name
     * 
     * @param newName The new name for the copy
     * @return Copy of this file body with new name
     */
    public FileBody copyWithName(String newName) {
        FileBody copy = copy();
        copy.name = newName;
        return copy;
    }

    /**
     * Creates a copy with new type
     * 
     * @param newType The new type for the copy
     * @return Copy of this file body with new type
     */
    public FileBody copyWithType(String newType) {
        FileBody copy = copy();
        copy.type = newType;
        return copy;
    }

    /**
     * Creates a copy with new size
     * 
     * @param newSize The new size for the copy
     * @return Copy of this file body with new size
     */
    public FileBody copyWithSize(Long newSize) {
        FileBody copy = copy();
        copy.size = newSize;
        return copy;
    }

    /**
     * Checks if the file body has a source
     * 
     * @return true if has source
     */
    public boolean hasSrc() {
        return src != null && !src.trim().isEmpty();
    }

    /**
     * Checks if the file body has content
     * 
     * @return true if has content
     */
    public boolean hasContent() {
        return content != null && !content.trim().isEmpty();
    }

    /**
     * Checks if the file body has a name
     * 
     * @return true if has name
     */
    public boolean hasName() {
        return name != null && !name.trim().isEmpty();
    }

    /**
     * Checks if the file body has a type
     * 
     * @return true if has type
     */
    public boolean hasType() {
        return type != null && !type.trim().isEmpty();
    }

    /**
     * Checks if this represents a valid file
     * 
     * @return true if has source or content
     */
    public boolean isValid() {
        return hasSrc() || hasContent();
    }

    /**
     * Gets the file extension from the name or source
     * 
     * @return File extension or null if not found
     */
    public String getExtension() {
        String fileName = hasName() ? name : src;
        if (fileName == null) return null;

        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < fileName.length() - 1) {
            return fileName.substring(lastDot + 1).toLowerCase();
        }
        return null;
    }

    /**
     * Gets the effective file name (name if available, otherwise extracted from src)
     * 
     * @return Effective file name
     */
    public String getEffectiveName() {
        if (hasName()) {
            return name;
        }
        if (hasSrc()) {
            return extractFileName(src);
        }
        return null;
    }

    /**
     * Extracts file name from a file path
     * 
     * @param filePath File path
     * @return File name or null
     */
    private static String extractFileName(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return null;
        }

        // Handle both Windows and Unix path separators
        String normalizedPath = filePath.replace('\\', '/');
        int lastSlash = normalizedPath.lastIndexOf('/');

        if (lastSlash >= 0 && lastSlash < normalizedPath.length() - 1) {
            return normalizedPath.substring(lastSlash + 1);
        }

        return normalizedPath;
    }
    
}