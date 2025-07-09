package es.alesqui.postmangpt.model.postman;

import org.springframework.data.mongodb.core.mapping.Field;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body options for raw body content
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BodyOptions {

	/**
	 * Raw body options
	 */
	@Field("raw")
	@JsonProperty("raw")
	private RawOptions raw;

	/**
	 * Creates body options for JSON
	 * 
	 * @return BodyOptions for JSON
	 */
	public static BodyOptions json() {
		return BodyOptions.builder().raw(RawOptions.json()).build();
	}

	/**
	 * Creates body options for XML
	 * 
	 * @return BodyOptions for XML
	 */
	public static BodyOptions xml() {
		return BodyOptions.builder().raw(RawOptions.xml()).build();
	}

	/**
	 * Creates body options for text
	 * 
	 * @return BodyOptions for text
	 */
	public static BodyOptions text() {
		return BodyOptions.builder().raw(RawOptions.text()).build();
	}

	/**
	 * Creates body options for HTML
	 * 
	 * @return BodyOptions for HTML
	 */
	public static BodyOptions html() {
		return BodyOptions.builder().raw(RawOptions.html()).build();
	}

	/**
	 * Creates body options for JavaScript
	 * 
	 * @return BodyOptions for JavaScript
	 */
	public static BodyOptions javascript() {
		return BodyOptions.builder().raw(RawOptions.javascript()).build();
	}
	
	/**
	 * Creates a copy of this body options
	 * 
	 * @return Copy of this body options
	 */
	public BodyOptions copy() {
	    return BodyOptions.builder()
	            .raw(this.raw != null ? this.raw.copy() : null)
	            .build();
	}

	/**
	 * Creates a copy with new raw options
	 * 
	 * @param newRaw The new raw options for the copy
	 * @return Copy of this body options with new raw options
	 */
	public BodyOptions copyWithRaw(RawOptions newRaw) {
	    BodyOptions copy = copy();
	    copy.raw = newRaw;
	    return copy;
	}

	/**
	 * Creates a copy without raw options
	 * 
	 * @return Copy of this body options without raw options
	 */
	public BodyOptions copyWithoutRaw() {
	    return copyWithRaw(null);
	}

	/**
	 * Creates a copy with new language
	 * 
	 * @param language The new language for the copy
	 * @return Copy with new language
	 */
	public BodyOptions copyWithLanguage(String language) {
	    RawOptions newRaw = this.raw != null ? this.raw.copyWithLanguage(language) : RawOptions.builder().language(language).build();
	    return copyWithRaw(newRaw);
	}

	/**
	 * Creates a copy as JSON body options
	 * 
	 * @return Copy configured for JSON
	 */
	public BodyOptions copyAsJson() {
	    return copyWithRaw(RawOptions.json());
	}

	/**
	 * Creates a copy as XML body options
	 * 
	 * @return Copy configured for XML
	 */
	public BodyOptions copyAsXml() {
	    return copyWithRaw(RawOptions.xml());
	}

	/**
	 * Creates a copy as text body options
	 * 
	 * @return Copy configured for text
	 */
	public BodyOptions copyAsText() {
	    return copyWithRaw(RawOptions.text());
	}

	/**
	 * Creates a copy as HTML body options
	 * 
	 * @return Copy configured for HTML
	 */
	public BodyOptions copyAsHtml() {
	    return copyWithRaw(RawOptions.html());
	}

	/**
	 * Creates a copy as JavaScript body options
	 * 
	 * @return Copy configured for JavaScript
	 */
	public BodyOptions copyAsJavascript() {
	    return copyWithRaw(RawOptions.javascript());
	}

	/**
	 * Creates a copy as CSS body options
	 * 
	 * @return Copy configured for CSS
	 */
	public BodyOptions copyAsCss() {
	    return copyWithLanguage("css");
	}

	/**
	 * Creates a copy as SQL body options
	 * 
	 * @return Copy configured for SQL
	 */
	public BodyOptions copyAsSql() {
	    return copyWithLanguage("sql");
	}

	/**
	 * Creates a copy as Python body options
	 * 
	 * @return Copy configured for Python
	 */
	public BodyOptions copyAsPython() {
	    return copyWithLanguage("python");
	}

	/**
	 * Creates a copy as Java body options
	 * 
	 * @return Copy configured for Java
	 */
	public BodyOptions copyAsJava() {
	    return copyWithLanguage("java");
	}

	/**
	 * Creates a copy as C# body options
	 * 
	 * @return Copy configured for C#
	 */
	public BodyOptions copyAsCSharp() {
	    return copyWithLanguage("csharp");
	}

	/**
	 * Creates a copy as PHP body options
	 * 
	 * @return Copy configured for PHP
	 */
	public BodyOptions copyAsPhp() {
	    return copyWithLanguage("php");
	}

	/**
	 * Creates a copy as Ruby body options
	 * 
	 * @return Copy configured for Ruby
	 */
	public BodyOptions copyAsRuby() {
	    return copyWithLanguage("ruby");
	}

	/**
	 * Creates a copy as Go body options
	 * 
	 * @return Copy configured for Go
	 */
	public BodyOptions copyAsGo() {
	    return copyWithLanguage("go");
	}

	/**
	 * Creates a copy as Rust body options
	 * 
	 * @return Copy configured for Rust
	 */
	public BodyOptions copyAsRust() {
	    return copyWithLanguage("rust");
	}

	/**
	 * Creates a copy as TypeScript body options
	 * 
	 * @return Copy configured for TypeScript
	 */
	public BodyOptions copyAsTypeScript() {
	    return copyWithLanguage("typescript");
	}

	/**
	 * Creates a copy as Kotlin body options
	 * 
	 * @return Copy configured for Kotlin
	 */
	public BodyOptions copyAsKotlin() {
	    return copyWithLanguage("kotlin");
	}

	/**
	 * Creates a copy as Swift body options
	 * 
	 * @return Copy configured for Swift
	 */
	public BodyOptions copyAsSwift() {
	    return copyWithLanguage("swift");
	}

	/**
	 * Creates a copy as YAML body options
	 * 
	 * @return Copy configured for YAML
	 */
	public BodyOptions copyAsYaml() {
	    return copyWithLanguage("yaml");
	}

	/**
	 * Creates a copy as Markdown body options
	 * 
	 * @return Copy configured for Markdown
	 */
	public BodyOptions copyAsMarkdown() {
	    return copyWithLanguage("markdown");
	}

	/**
	 * Creates a copy as Shell script body options
	 * 
	 * @return Copy configured for Shell
	 */
	public BodyOptions copyAsShell() {
	    return copyWithLanguage("shell");
	}

	/**
	 * Creates a copy as PowerShell body options
	 * 
	 * @return Copy configured for PowerShell
	 */
	public BodyOptions copyAsPowerShell() {
	    return copyWithLanguage("powershell");
	}

	/**
	 * Creates a copy as Batch body options
	 * 
	 * @return Copy configured for Batch
	 */
	public BodyOptions copyAsBatch() {
	    return copyWithLanguage("batch");
	}

	/**
	 * Creates a copy with custom language
	 * 
	 * @param customLanguage Custom language identifier
	 * @return Copy with custom language
	 */
	public BodyOptions copyWithCustomLanguage(String customLanguage) {
	    return copyWithLanguage(customLanguage);
	}

	/**
	 * Checks if this body options has raw options
	 * 
	 * @return true if has raw options
	 */
	public boolean hasRaw() {
	    return raw != null;
	}

	/**
	 * Checks if this body options has a specific language
	 * 
	 * @param language Language to check
	 * @return true if has the specified language
	 */
	public boolean hasLanguage(String language) {
	    return hasRaw() && raw.hasLanguage(language);
	}

	/**
	 * Gets the language or returns default
	 * 
	 * @param defaultLanguage Default language if none set
	 * @return Language or default
	 */
	public String getLanguageOrDefault(String defaultLanguage) {
	    return hasRaw() ? raw.getLanguageOrDefault(defaultLanguage) : defaultLanguage;
	}

	/**
	 * Creates a copy for a specific content type
	 * 
	 * @param contentType Content type (e.g., "application/json", "text/xml")
	 * @return Copy configured for content type
	 */
	public BodyOptions copyForContentType(String contentType) {
	    if (contentType == null) {
	        return copy();
	    }
	    
	    String lowerContentType = contentType.toLowerCase();
	    
	    if (lowerContentType.contains("json")) {
	        return copyAsJson();
	    } else if (lowerContentType.contains("xml")) {
	        return copyAsXml();
	    } else if (lowerContentType.contains("html")) {
	        return copyAsHtml();
	    } else if (lowerContentType.contains("javascript")) {
	        return copyAsJavascript();
	    } else if (lowerContentType.contains("css")) {
	        return copyAsCss();
	    } else if (lowerContentType.contains("yaml")) {
	        return copyAsYaml();
	    } else {
	        return copyAsText();
	    }
	}

	/**
	 * Creates a copy optimized for API responses
	 * 
	 * @return Copy optimized for API responses (JSON by default)
	 */
	public BodyOptions copyForApiResponse() {
	    return copyAsJson();
	}

	/**
	 * Creates a copy optimized for configuration files
	 * 
	 * @return Copy optimized for configuration (YAML by default)
	 */
	public BodyOptions copyForConfig() {
	    return copyAsYaml();
	}

	/**
	 * Creates a copy optimized for documentation
	 * 
	 * @return Copy optimized for documentation (Markdown by default)
	 */
	public BodyOptions copyForDocumentation() {
	    return copyAsMarkdown();
	}

	/**
	 * Creates a copy optimized for scripts
	 * 
	 * @param scriptType Type of script (shell, powershell, batch, etc.)
	 * @return Copy optimized for scripts
	 */
	public BodyOptions copyForScript(String scriptType) {
	    if (scriptType == null) {
	        return copyAsShell();
	    }
	    
	    String lowerScriptType = scriptType.toLowerCase();
	    
	    switch (lowerScriptType) {
	        case "powershell":
	        case "ps1":
	            return copyAsPowerShell();
	        case "batch":
	        case "bat":
	        case "cmd":
	            return copyAsBatch();
	        case "python":
	        case "py":
	            return copyAsPython();
	        case "javascript":
	        case "js":
	            return copyAsJavascript();
	        case "typescript":
	        case "ts":
	            return copyAsTypeScript();
	        default:
	            return copyAsShell();
	    }
	}
	
}
