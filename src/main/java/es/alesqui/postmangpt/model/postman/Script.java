package es.alesqui.postmangpt.model.postman;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

import org.springframework.data.mongodb.core.mapping.Field;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * A script is a snippet of Javascript code that can be used to to perform setup
 * or teardown operations on a collection.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Script {

	/**
	 * A unique, user defined identifier that can be used to refer to this script
	 * from requests.
	 */
	@Field("id")
	@JsonProperty("id")
	private String id;

	/**
	 * Type of the script. E.g: 'text/javascript'
	 */
	@Field("type")
	@JsonProperty("type")
	private String type;

	/**
	 * This is an array of strings, where each line represents a single line of
	 * code. Having lines separate makes it possible to easily track changes made to
	 * scripts.
	 */
	@Field("exec")
	@JsonProperty("exec")
	@Builder.Default
	private List<String> exec = new ArrayList<>();

	/**
	 * The language associated with the script.
	 */
	@Field("src")
	@JsonProperty("src")
	private String src;

	/**
	 * Script name
	 */
	@Field("name")
	@JsonProperty("name")
	private String name;

	/**
	 * Constructor for script with single line of code
	 * 
	 * @param code Single line of script code
	 */
	public Script(String code) {
		this.type = "text/javascript";
		this.exec = new ArrayList<>();
		if (code != null && !code.trim().isEmpty()) {
			// Split by lines to maintain the array structure
			String[] lines = code.split("\\r?\\n");
			this.exec.addAll(Arrays.asList(lines));
		}
	}

	/**
	 * Constructor for script with multiple lines of code
	 * 
	 * @param lines Multiple lines of script code
	 */
	public Script(List<String> lines) {
		this.type = "text/javascript";
		this.exec = lines != null ? new ArrayList<>(lines) : new ArrayList<>();
	}

	/**
	 * Constructor for script with lines array
	 * 
	 * @param lines Array of script lines
	 */
	public Script(String... lines) {
		this.type = "text/javascript";
		this.exec = new ArrayList<>(Arrays.asList(lines));
	}

	/**
	 * Creates a script with single line of code
	 * 
	 * @param code Script code
	 * @return Script instance
	 */
	public static Script of(String code) {
		return new Script(code);
	}

	/**
	 * Creates a script with multiple lines
	 * 
	 * @param lines Script lines
	 * @return Script instance
	 */
	public static Script of(String... lines) {
		return new Script(lines);
	}

	/**
	 * Creates a script with list of lines
	 * 
	 * @param lines List of script lines
	 * @return Script instance
	 */
	public static Script of(List<String> lines) {
		return new Script(lines);
	}

	/**
	 * Creates an empty JavaScript script
	 * 
	 * @return Empty script instance
	 */
	public static Script empty() {
		return new Script(new ArrayList<>());
	}

	/**
	 * Creates a JavaScript script
	 * 
	 * @param code JavaScript code
	 * @return Script instance
	 */
	public static Script javascript(String code) {
		Script script = new Script(code);
		script.setType("text/javascript");
		return script;
	}

	// Common Postman script templates

	/**
	 * Creates a status check test script
	 * 
	 * @param expectedStatus Expected HTTP status code
	 * @return Script instance
	 */
	public static Script statusTest(int expectedStatus) {
		return Script.of("pm.test('Status code is " + expectedStatus + "', function () {",
				"    pm.response.to.have.status(" + expectedStatus + ");", "});");
	}

	/**
	 * Creates a response time test script
	 * 
	 * @param maxTime Maximum response time in milliseconds
	 * @return Script instance
	 */
	public static Script responseTimeTest(int maxTime) {
		return Script.of("pm.test('Response time is less than " + maxTime + "ms', function () {",
				"    pm.expect(pm.response.responseTime).to.be.below(" + maxTime + ");", "});");
	}

	/**
	 * Creates a JSON response test script
	 * 
	 * @return Script instance
	 */
	public static Script jsonResponseTest() {
		return Script.of("pm.test('Response is JSON', function () {", "    pm.response.to.be.json;", "});");
	}

	/**
	 * Creates a script to set environment variable
	 * 
	 * @param varName  Variable name
	 * @param varValue Variable value
	 * @return Script instance
	 */
	public static Script setEnvironmentVariable(String varName, String varValue) {
		return Script.of("pm.environment.set('" + varName + "', '" + varValue + "');");
	}

	/**
	 * Creates a script to set global variable
	 * 
	 * @param varName  Variable name
	 * @param varValue Variable value
	 * @return Script instance
	 */
	public static Script setGlobalVariable(String varName, String varValue) {
		return Script.of("pm.globals.set('" + varName + "', '" + varValue + "');");
	}

	/**
	 * Creates a console log script
	 * 
	 * @param message Message to log
	 * @return Script instance
	 */
	public static Script consoleLog(String message) {
		return Script.of("console.log('" + message + "');");
	}

	// Fluent API methods

	/**
	 * Sets the script type
	 * 
	 * @param type Script type
	 * @return this instance for method chaining
	 */
	public Script withType(String type) {
		this.type = type;
		return this;
	}

	/**
	 * Sets the script ID
	 * 
	 * @param id Script ID
	 * @return this instance for method chaining
	 */
	public Script withId(String id) {
		this.id = id;
		return this;
	}

	/**
	 * Sets the script name
	 * 
	 * @param name Script name
	 * @return this instance for method chaining
	 */
	public Script withName(String name) {
		this.name = name;
		return this;
	}

	/**
	 * Adds a line of code
	 * 
	 * @param line Line of code to add
	 * @return this instance for method chaining
	 */
	public Script addLine(String line) {
		if (this.exec == null) {
			this.exec = new ArrayList<>();
		}
		this.exec.add(line);
		return this;
	}

	/**
	 * Adds multiple lines of code
	 * 
	 * @param lines Lines of code to add
	 * @return this instance for method chaining
	 */
	public Script addLines(String... lines) {
		if (this.exec == null) {
			this.exec = new ArrayList<>();
		}
		this.exec.addAll(Arrays.asList(lines));
		return this;
	}

	/**
	 * Appends code to the script
	 * 
	 * @param code Code to append
	 * @return this instance for method chaining
	 */
	public Script appendExec(String code) {
		if (code != null && !code.trim().isEmpty()) {
			String[] lines = code.split("\\r?\\n");
			addLines(lines);
		}
		return this;
	}

	/**
	 * Prepends code to the script
	 * 
	 * @param code Code to prepend
	 * @return this instance for method chaining
	 */
	public Script prependExec(String code) {
		if (code != null && !code.trim().isEmpty()) {
			String[] lines = code.split("\\r?\\n");
			List<String> newExec = new ArrayList<>(Arrays.asList(lines));
			if (this.exec != null) {
				newExec.addAll(this.exec);
			}
			this.exec = newExec;
		}
		return this;
	}

	// Utility methods

	/**
	 * Gets the script as a single string
	 * 
	 * @return Complete script as string
	 */
	public String getExecAsString() {
		if (exec == null || exec.isEmpty()) {
			return "";
		}
		return String.join("\n", exec);
	}

	/**
	 * Checks if the script has executable code
	 * 
	 * @return true if has code
	 */
	public boolean hasCode() {
		return exec != null && !exec.isEmpty()
				&& exec.stream().anyMatch(line -> line != null && !line.trim().isEmpty());
	}

	/**
	 * Gets the number of lines in the script
	 * 
	 * @return Number of lines
	 */
	public int getLineCount() {
		return exec != null ? exec.size() : 0;
	}

	/**
	 * Clears all script lines
	 * 
	 * @return this instance for method chaining
	 */
	public Script clear() {
		if (this.exec != null) {
			this.exec.clear();
		}
		return this;
	}

	/**
	 * Checks if this is a JavaScript script
	 * 
	 * @return true if JavaScript
	 */
	public boolean isJavaScript() {
		return "text/javascript".equals(type) || type == null;
	}

	/**
	 * Creates a copy of this script
	 * 
	 * @return Copy of this script
	 */
	public Script copy() {
		return Script.builder().id(this.id).type(this.type)
				.exec(this.exec != null ? new ArrayList<>(this.exec) : new ArrayList<>()).src(this.src).name(this.name)
				.build();
	}

	/**
	 * Creates a copy with new executable code
	 * 
	 * @param newExec The new executable code lines
	 * @return Copy of this script with new executable code
	 */
	public Script copyWithExec(List<String> newExec) {
		Script copy = copy();
		copy.exec = newExec != null ? new ArrayList<>(newExec) : new ArrayList<>();
		return copy;
	}

	/**
	 * Creates a copy with new executable code from string
	 * 
	 * @param newCode The new code as string
	 * @return Copy of this script with new executable code
	 */
	public Script copyWithExec(String newCode) {
		Script copy = copy();
		copy.exec = new ArrayList<>();
		if (newCode != null && !newCode.trim().isEmpty()) {
			String[] lines = newCode.split("\\r?\\n");
			copy.exec.addAll(Arrays.asList(lines));
		}
		return copy;
	}

	/**
	 * Creates a copy with new executable code from array
	 * 
	 * @param newLines The new code lines
	 * @return Copy of this script with new executable code
	 */
	public Script copyWithExec(String... newLines) {
		Script copy = copy();
		copy.exec = new ArrayList<>(Arrays.asList(newLines));
		return copy;
	}

	/**
	 * Creates a copy with a new ID
	 * 
	 * @param newId The new ID for the copy
	 * @return Copy of this script with new ID
	 */
	public Script copyWithId(String newId) {
		Script copy = copy();
		copy.id = newId;
		return copy;
	}

	/**
	 * Creates a copy with a new type
	 * 
	 * @param newType The new type for the copy
	 * @return Copy of this script with new type
	 */
	public Script copyWithType(String newType) {
		Script copy = copy();
		copy.type = newType;
		return copy;
	}

	/**
	 * Creates a copy with a new name
	 * 
	 * @param newName The new name for the copy
	 * @return Copy of this script with new name
	 */
	public Script copyWithName(String newName) {
		Script copy = copy();
		copy.name = newName;
		return copy;
	}

	/**
	 * Creates a copy with a new src
	 * 
	 * @param newSrc The new src for the copy
	 * @return Copy of this script with new src
	 */
	public Script copyWithSrc(String newSrc) {
		Script copy = copy();
		copy.src = newSrc;
		return copy;
	}

	/**
	 * Creates a JavaScript copy of this script
	 * 
	 * @return Copy of this script as JavaScript
	 */
	public Script copyAsJavaScript() {
		return copyWithType("text/javascript");
	}

	/**
	 * Creates a copy with additional code appended
	 * 
	 * @param additionalCode Code to append
	 * @return Copy of this script with appended code
	 */
	public Script copyWithAppendedCode(String additionalCode) {
		Script copy = copy();
		copy.appendExec(additionalCode);
		return copy;
	}

	/**
	 * Creates a copy with code prepended
	 * 
	 * @param prependCode Code to prepend
	 * @return Copy of this script with prepended code
	 */
	public Script copyWithPrependedCode(String prependCode) {
		Script copy = copy();
		copy.prependExec(prependCode);
		return copy;
	}

	/**
	 * Creates a copy with additional lines appended
	 * 
	 * @param additionalLines Lines to append
	 * @return Copy of this script with appended lines
	 */
	public Script copyWithAppendedLines(String... additionalLines) {
		Script copy = copy();
		copy.addLines(additionalLines);
		return copy;
	}

	/**
	 * Creates a copy with additional lines appended
	 * 
	 * @param additionalLines Lines to append
	 * @return Copy of this script with appended lines
	 */
	public Script copyWithAppendedLines(List<String> additionalLines) {
		Script copy = copy();
		if (additionalLines != null) {
			copy.exec.addAll(additionalLines);
		}
		return copy;
	}

	/**
	 * Creates an empty copy (no executable code)
	 * 
	 * @return Copy of this script without executable code
	 */
	public Script copyEmpty() {
		Script copy = copy();
		copy.exec = new ArrayList<>();
		return copy;
	}

	/**
	 * Creates a copy with a single line of code
	 * 
	 * @param singleLine The single line of code
	 * @return Copy of this script with single line
	 */
	public Script copyWithSingleLine(String singleLine) {
		Script copy = copy();
		copy.exec = new ArrayList<>();
		if (singleLine != null) {
			copy.exec.add(singleLine);
		}
		return copy;
	}

	/**
	 * Creates a copy with code replaced by template
	 * 
	 * @param expectedStatus Expected status code for status test
	 * @return Copy with status test code
	 */
	public Script copyAsStatusTest(int expectedStatus) {
		Script copy = copy();
		copy.exec = new ArrayList<>(Arrays.asList("pm.test('Status code is " + expectedStatus + "', function () {",
				"    pm.response.to.have.status(" + expectedStatus + ");", "});"));
		return copy;
	}

	/**
	 * Creates a copy with code replaced by response time test template
	 * 
	 * @param maxTime Maximum response time in milliseconds
	 * @return Copy with response time test code
	 */
	public Script copyAsResponseTimeTest(int maxTime) {
		Script copy = copy();
		copy.exec = new ArrayList<>(
				Arrays.asList("pm.test('Response time is less than " + maxTime + "ms', function () {",
						"    pm.expect(pm.response.responseTime).to.be.below(" + maxTime + ");", "});"));
		return copy;
	}

	/**
	 * Creates a copy with code replaced by JSON response test template
	 * 
	 * @return Copy with JSON response test code
	 */
	public Script copyAsJsonResponseTest() {
		Script copy = copy();
		copy.exec = new ArrayList<>(
				Arrays.asList("pm.test('Response is JSON', function () {", "    pm.response.to.be.json;", "});"));
		return copy;
	}

	/**
	 * Creates a copy with environment variable setter code
	 * 
	 * @param varName  Variable name
	 * @param varValue Variable value
	 * @return Copy with environment variable setter code
	 */
	public Script copyAsEnvironmentVariableSetter(String varName, String varValue) {
		return copyWithSingleLine("pm.environment.set('" + varName + "', '" + varValue + "');");
	}

	/**
	 * Creates a copy with global variable setter code
	 * 
	 * @param varName  Variable name
	 * @param varValue Variable value
	 * @return Copy with global variable setter code
	 */
	public Script copyAsGlobalVariableSetter(String varName, String varValue) {
		return copyWithSingleLine("pm.globals.set('" + varName + "', '" + varValue + "');");
	}

	/**
	 * Creates a copy with console log code
	 * 
	 * @param message Message to log
	 * @return Copy with console log code
	 */
	public Script copyAsConsoleLog(String message) {
		return copyWithSingleLine("console.log('" + message + "');");
	}

}