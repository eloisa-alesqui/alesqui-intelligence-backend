package es.alesqui.intelligence.model.postman;

import org.springframework.data.mongodb.core.mapping.Field;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Raw body options
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RawOptions {

	/**
	 * Language for syntax highlighting
	 */
	@Field("language")
	@JsonProperty("language")
	private String language;

	/**
	 * Creates raw options for JSON
	 * 
	 * @return RawOptions for JSON
	 */
	public static RawOptions json() {
		return RawOptions.builder().language("json").build();
	}

	/**
	 * Creates raw options for XML
	 * 
	 * @return RawOptions for XML
	 */
	public static RawOptions xml() {
		return RawOptions.builder().language("xml").build();
	}

	/**
	 * Creates raw options for text
	 * 
	 * @return RawOptions for text
	 */
	public static RawOptions text() {
		return RawOptions.builder().language("text").build();
	}

	/**
	 * Creates raw options for HTML
	 * 
	 * @return RawOptions for HTML
	 */
	public static RawOptions html() {
		return RawOptions.builder().language("html").build();
	}

	/**
	 * Creates raw options for JavaScript
	 * 
	 * @return RawOptions for JavaScript
	 */
	public static RawOptions javascript() {
		return RawOptions.builder().language("javascript").build();
	}

	/**
	 * Creates a copy of this raw options
	 * 
	 * @return Copy of this raw options
	 */
	public RawOptions copy() {
		return RawOptions.builder().language(this.language).build();
	}

	/**
	 * Creates a copy with new language
	 * 
	 * @param newLanguage The new language for the copy
	 * @return Copy of this raw options with new language
	 */
	public RawOptions copyWithLanguage(String newLanguage) {
		RawOptions copy = copy();
		copy.language = newLanguage;
		return copy;
	}

	/**
	 * Creates a copy without language
	 * 
	 * @return Copy of this raw options without language
	 */
	public RawOptions copyWithoutLanguage() {
		return copyWithLanguage(null);
	}

	/**
	 * Creates a copy as JSON raw options
	 * 
	 * @return Copy configured for JSON
	 */
	public RawOptions copyAsJson() {
		return copyWithLanguage("json");
	}

	/**
	 * Creates a copy as XML raw options
	 * 
	 * @return Copy configured for XML
	 */
	public RawOptions copyAsXml() {
		return copyWithLanguage("xml");
	}

	/**
	 * Creates a copy as text raw options
	 * 
	 * @return Copy configured for text
	 */
	public RawOptions copyAsText() {
		return copyWithLanguage("text");
	}

	/**
	 * Creates a copy as HTML raw options
	 * 
	 * @return Copy configured for HTML
	 */
	public RawOptions copyAsHtml() {
		return copyWithLanguage("html");
	}

	/**
	 * Creates a copy as JavaScript raw options
	 * 
	 * @return Copy configured for JavaScript
	 */
	public RawOptions copyAsJavascript() {
		return copyWithLanguage("javascript");
	}

	/**
	 * Creates a copy as CSS raw options
	 * 
	 * @return Copy configured for CSS
	 */
	public RawOptions copyAsCss() {
		return copyWithLanguage("css");
	}

	/**
	 * Creates a copy as SQL raw options
	 * 
	 * @return Copy configured for SQL
	 */
	public RawOptions copyAsSql() {
		return copyWithLanguage("sql");
	}

	/**
	 * Creates a copy as Python raw options
	 * 
	 * @return Copy configured for Python
	 */
	public RawOptions copyAsPython() {
		return copyWithLanguage("python");
	}

	/**
	 * Creates a copy as Java raw options
	 * 
	 * @return Copy configured for Java
	 */
	public RawOptions copyAsJava() {
		return copyWithLanguage("java");
	}

	/**
	 * Creates a copy as C# raw options
	 * 
	 * @return Copy configured for C#
	 */
	public RawOptions copyAsCSharp() {
		return copyWithLanguage("csharp");
	}

	/**
	 * Creates a copy as PHP raw options
	 * 
	 * @return Copy configured for PHP
	 */
	public RawOptions copyAsPhp() {
		return copyWithLanguage("php");
	}

	/**
	 * Creates a copy as Ruby raw options
	 * 
	 * @return Copy configured for Ruby
	 */
	public RawOptions copyAsRuby() {
		return copyWithLanguage("ruby");
	}

	/**
	 * Creates a copy as Go raw options
	 * 
	 * @return Copy configured for Go
	 */
	public RawOptions copyAsGo() {
		return copyWithLanguage("go");
	}

	/**
	 * Creates a copy as Rust raw options
	 * 
	 * @return Copy configured for Rust
	 */
	public RawOptions copyAsRust() {
		return copyWithLanguage("rust");
	}

	/**
	 * Creates a copy as TypeScript raw options
	 * 
	 * @return Copy configured for TypeScript
	 */
	public RawOptions copyAsTypeScript() {
		return copyWithLanguage("typescript");
	}

	/**
	 * Creates a copy as Kotlin raw options
	 * 
	 * @return Copy configured for Kotlin
	 */
	public RawOptions copyAsKotlin() {
		return copyWithLanguage("kotlin");
	}

	/**
	 * Creates a copy as Swift raw options
	 * 
	 * @return Copy configured for Swift
	 */
	public RawOptions copyAsSwift() {
		return copyWithLanguage("swift");
	}

	/**
	 * Creates a copy as YAML raw options
	 * 
	 * @return Copy configured for YAML
	 */
	public RawOptions copyAsYaml() {
		return copyWithLanguage("yaml");
	}

	/**
	 * Creates a copy as Markdown raw options
	 * 
	 * @return Copy configured for Markdown
	 */
	public RawOptions copyAsMarkdown() {
		return copyWithLanguage("markdown");
	}

	/**
	 * Creates a copy as Shell script raw options
	 * 
	 * @return Copy configured for Shell
	 */
	public RawOptions copyAsShell() {
		return copyWithLanguage("shell");
	}

	/**
	 * Creates a copy as PowerShell raw options
	 * 
	 * @return Copy configured for PowerShell
	 */
	public RawOptions copyAsPowerShell() {
		return copyWithLanguage("powershell");
	}

	/**
	 * Creates a copy as Batch raw options
	 * 
	 * @return Copy configured for Batch
	 */
	public RawOptions copyAsBatch() {
		return copyWithLanguage("batch");
	}

	/**
	 * Checks if this raw options has a language
	 * 
	 * @return true if has language
	 */
	public boolean hasLanguage() {
		return language != null && !language.trim().isEmpty();
	}

	/**
	 * Checks if this raw options has a specific language
	 * 
	 * @param targetLanguage Language to check
	 * @return true if has the specified language
	 */
	public boolean hasLanguage(String targetLanguage) {
		return hasLanguage() && language.equalsIgnoreCase(targetLanguage);
	}

	/**
	 * Gets the language or returns default
	 * 
	 * @param defaultLanguage Default language if none set
	 * @return Language or default
	 */
	public String getLanguageOrDefault(String defaultLanguage) {
		return hasLanguage() ? language : defaultLanguage;
	}

	/**
	 * Checks if this is a programming language
	 * 
	 * @return true if it's a programming language
	 */
	public boolean isProgrammingLanguage() {
		if (!hasLanguage()) {
			return false;
		}

		String lang = language.toLowerCase();
		return lang.equals("java") || lang.equals("python") || lang.equals("javascript") || lang.equals("typescript")
				|| lang.equals("csharp") || lang.equals("php") || lang.equals("ruby") || lang.equals("go")
				|| lang.equals("rust") || lang.equals("kotlin") || lang.equals("swift");
	}

	/**
	 * Checks if this is a markup language
	 * 
	 * @return true if it's a markup language
	 */
	public boolean isMarkupLanguage() {
		if (!hasLanguage()) {
			return false;
		}

		String lang = language.toLowerCase();
		return lang.equals("html") || lang.equals("xml") || lang.equals("markdown") || lang.equals("yaml");
	}

	/**
	 * Checks if this is a data format
	 * 
	 * @return true if it's a data format
	 */
	public boolean isDataFormat() {
		if (!hasLanguage()) {
			return false;
		}

		String lang = language.toLowerCase();
		return lang.equals("json") || lang.equals("xml") || lang.equals("yaml") || lang.equals("csv");
	}

	/**
	 * Checks if this is a script language
	 * 
	 * @return true if it's a script language
	 */
	public boolean isScriptLanguage() {
		if (!hasLanguage()) {
			return false;
		}

		String lang = language.toLowerCase();
		return lang.equals("shell") || lang.equals("powershell") || lang.equals("batch") || lang.equals("python")
				|| lang.equals("javascript");
	}

}
