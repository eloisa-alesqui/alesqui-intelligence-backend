package es.alesqui.intelligence.model.api_spec.postman;

import org.springframework.data.mongodb.core.mapping.Field;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GraphQL body content
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GraphQLBody {

	/**
	 * GraphQL query string
	 */
	@Field("query")
	@JsonProperty("query")
	private String query;

	/**
	 * GraphQL variables (as JSON string)
	 */
	@Field("variables")
	@JsonProperty("variables")
	private String variables;

	/**
	 * Constructor for GraphQL body with query only
	 * 
	 * @param query GraphQL query
	 */
	public GraphQLBody(String query) {
		this.query = query;
	}

	/**
	 * Creates a GraphQL body with query
	 * 
	 * @param query GraphQL query
	 * @return GraphQLBody instance
	 */
	public static GraphQLBody of(String query) {
		return new GraphQLBody(query);
	}

	/**
	 * Creates a GraphQL body with query and variables
	 * 
	 * @param query     GraphQL query
	 * @param variables GraphQL variables
	 * @return GraphQLBody instance
	 */
	public static GraphQLBody of(String query, String variables) {
		return new GraphQLBody(query, variables);
	}

	/**
	 * Sets the variables
	 * 
	 * @param variables GraphQL variables
	 * @return this instance for method chaining
	 */
	public GraphQLBody withVariables(String variables) {
		this.variables = variables;
		return this;
	}

	/**
	 * Checks if the GraphQL body has a query
	 * 
	 * @return true if has query
	 */
	public boolean hasQuery() {
		return query != null && !query.trim().isEmpty();
	}

	/**
	 * Checks if the GraphQL body has variables
	 * 
	 * @return true if has variables
	 */
	public boolean hasVariables() {
		return variables != null && !variables.trim().isEmpty();
	}

	/**
	 * Validates if this GraphQL body is properly formed
	 * 
	 * @return true if the GraphQL body is valid
	 */
	public boolean isValid() {
		return hasQuery() && isValidGraphQLSyntax() && isValidVariables();
	}

	/**
	 * Basic validation of GraphQL syntax
	 * 
	 * @return true if query has basic GraphQL structure
	 */
	public boolean isValidGraphQLSyntax() {
		if (!hasQuery())
			return false;

		String trimmedQuery = query.trim();
		// Basic checks: has braces and looks like GraphQL
		return trimmedQuery.contains("{") && trimmedQuery.contains("}")
				&& (trimmedQuery.startsWith("query") || trimmedQuery.startsWith("mutation")
						|| trimmedQuery.startsWith("subscription") || trimmedQuery.startsWith("{"));
	}

	/**
	 * Validates if variables are valid JSON
	 * 
	 * @return true if variables are valid JSON or null/empty
	 */
	public boolean isValidVariables() {
		if (!hasVariables())
			return true;

		try {
			// Basic JSON validation - check if it starts and ends correctly
			String trimmed = variables.trim();
			return trimmed.startsWith("{") && trimmed.endsWith("}");
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Creates a copy of this GraphQL body
	 * 
	 * @return Copy of this GraphQL body
	 */
	public GraphQLBody copy() {
		return GraphQLBody.builder().query(this.query).variables(this.variables).build();
	}

	/**
	 * Creates a copy with new query
	 * 
	 * @param newQuery The new query for the copy
	 * @return Copy of this GraphQL body with new query
	 */
	public GraphQLBody copyWithQuery(String newQuery) {
		GraphQLBody copy = copy();
		copy.query = newQuery;
		return copy;
	}

	/**
	 * Creates a copy with new variables
	 * 
	 * @param newVariables The new variables for the copy
	 * @return Copy of this GraphQL body with new variables
	 */
	public GraphQLBody copyWithVariables(String newVariables) {
		GraphQLBody copy = copy();
		copy.variables = newVariables;
		return copy;
	}

	/**
	 * Creates a copy with new query and variables
	 * 
	 * @param newQuery     The new query for the copy
	 * @param newVariables The new variables for the copy
	 * @return Copy of this GraphQL body with new query and variables
	 */
	public GraphQLBody copyWithQueryAndVariables(String newQuery, String newVariables) {
		GraphQLBody copy = copy();
		copy.query = newQuery;
		copy.variables = newVariables;
		return copy;
	}

	/**
	 * Creates a copy without variables
	 * 
	 * @return Copy of this GraphQL body without variables
	 */
	public GraphQLBody copyWithoutVariables() {
		return copyWithVariables(null);
	}

	/**
	 * Creates a copy without query
	 * 
	 * @return Copy of this GraphQL body without query
	 */
	public GraphQLBody copyWithoutQuery() {
		return copyWithQuery(null);
	}

	/**
	 * Creates a copy with empty variables
	 * 
	 * @return Copy of this GraphQL body with empty variables
	 */
	public GraphQLBody copyWithEmptyVariables() {
		return copyWithVariables("{}");
	}

	/**
	 * Creates a copy with query appended
	 * 
	 * @param additionalQuery Query to append
	 * @return Copy with appended query
	 */
	public GraphQLBody copyWithQueryAppended(String additionalQuery) {
		if (additionalQuery == null || additionalQuery.trim().isEmpty()) {
			return copy();
		}

		String newQuery = (this.query != null ? this.query : "") + "\n" + additionalQuery;
		return copyWithQuery(newQuery.trim());
	}

	/**
	 * Creates a copy with query prepended
	 * 
	 * @param prependQuery Query to prepend
	 * @return Copy with prepended query
	 */
	public GraphQLBody copyWithQueryPrepended(String prependQuery) {
		if (prependQuery == null || prependQuery.trim().isEmpty()) {
			return copy();
		}

		String newQuery = prependQuery + "\n" + (this.query != null ? this.query : "");
		return copyWithQuery(newQuery.trim());
	}

	/**
	 * Creates a copy with formatted query (basic formatting)
	 * 
	 * @return Copy with formatted query
	 */
	public GraphQLBody copyWithFormattedQuery() {
		if (this.query == null) {
			return copy();
		}

		// Basic GraphQL formatting - add proper spacing and indentation
		String formattedQuery = this.query.replaceAll("\\{", " {\n  ").replaceAll("\\}", "\n}").replaceAll(",", ",\n  ")
				.replaceAll("\\n\\s*\\n", "\n").trim();

		return copyWithQuery(formattedQuery);
	}

	/**
	 * Creates a copy with minified query (removes extra whitespace)
	 * 
	 * @return Copy with minified query
	 */
	public GraphQLBody copyWithMinifiedQuery() {
		if (this.query == null) {
			return copy();
		}

		String minifiedQuery = this.query.replaceAll("\\s+", " ").replaceAll("\\s*\\{\\s*", "{")
				.replaceAll("\\s*\\}\\s*", "}").replaceAll("\\s*,\\s*", ",").replaceAll("\\s*:\\s*", ":").trim();

		return copyWithQuery(minifiedQuery);
	}

	/**
	 * Creates a copy as a query operation
	 * 
	 * @param queryName Name of the query
	 * @param fields    Fields to select
	 * @return Copy configured as query
	 */
	public GraphQLBody copyAsQuery(String queryName, String fields) {
		String queryString = String.format("query {\n  %s {\n    %s\n  }\n}", queryName, fields);
		return copyWithQuery(queryString);
	}

	/**
	 * Creates a copy as a mutation operation
	 * 
	 * @param mutationName Name of the mutation
	 * @param input        Input parameters
	 * @param fields       Fields to return
	 * @return Copy configured as mutation
	 */
	public GraphQLBody copyAsMutation(String mutationName, String input, String fields) {
		String mutationString = String.format("mutation {\n  %s(%s) {\n    %s\n  }\n}", mutationName, input, fields);
		return copyWithQuery(mutationString);
	}

	/**
	 * Creates a copy as a subscription operation
	 * 
	 * @param subscriptionName Name of the subscription
	 * @param fields           Fields to subscribe to
	 * @return Copy configured as subscription
	 */
	public GraphQLBody copyAsSubscription(String subscriptionName, String fields) {
		String subscriptionString = String.format("subscription {\n  %s {\n    %s\n  }\n}", subscriptionName, fields);
		return copyWithQuery(subscriptionString);
	}

	/**
	 * Creates a copy with a variable added to the variables JSON
	 * 
	 * @param variableName  Name of the variable
	 * @param variableValue Value of the variable (will be JSON-encoded)
	 * @return Copy with added variable
	 */
	public GraphQLBody copyWithVariableAdded(String variableName, Object variableValue) {
		String currentVars = this.variables != null && !this.variables.trim().isEmpty() ? this.variables.trim() : "{}";

		// Simple JSON manipulation - in production you'd want to use a JSON library
		if (currentVars.equals("{}")) {
			String newVars = String.format("{\"%s\": %s}", variableName,
					variableValue instanceof String ? "\"" + variableValue + "\"" : variableValue);
			return copyWithVariables(newVars);
		} else {
			// Insert before the closing brace
			String newVars = currentVars.substring(0, currentVars.length() - 1) + String.format(", \"%s\": %s}",
					variableName, variableValue instanceof String ? "\"" + variableValue + "\"" : variableValue);
			return copyWithVariables(newVars);
		}
	}

	/**
	 * Creates a copy with string variable added
	 * 
	 * @param variableName  Name of the variable
	 * @param variableValue String value of the variable
	 * @return Copy with added string variable
	 */
	public GraphQLBody copyWithStringVariable(String variableName, String variableValue) {
		return copyWithVariableAdded(variableName, variableValue);
	}

	/**
	 * Creates a copy with integer variable added
	 * 
	 * @param variableName  Name of the variable
	 * @param variableValue Integer value of the variable
	 * @return Copy with added integer variable
	 */
	public GraphQLBody copyWithIntVariable(String variableName, int variableValue) {
		return copyWithVariableAdded(variableName, variableValue);
	}

	/**
	 * Creates a copy with boolean variable added
	 * 
	 * @param variableName  Name of the variable
	 * @param variableValue Boolean value of the variable
	 * @return Copy with added boolean variable
	 */
	public GraphQLBody copyWithBooleanVariable(String variableName, boolean variableValue) {
		return copyWithVariableAdded(variableName, variableValue);
	}

	/**
	 * Creates a copy with double variable added
	 * 
	 * @param variableName  Name of the variable
	 * @param variableValue Double value of the variable
	 * @return Copy with added double variable
	 */
	public GraphQLBody copyWithDoubleVariable(String variableName, double variableValue) {
		return copyWithVariableAdded(variableName, variableValue);
	}

	/**
	 * Creates a copy for a different environment with variable substitution
	 * 
	 * @param environment Environment name
	 * @return Copy with environment-specific modifications
	 */
	public GraphQLBody copyForEnvironment(String environment) {
		GraphQLBody copy = copy();

		if (copy.query != null) {
			// Replace environment placeholders in query
			copy.query = copy.query.replace("{{env}}", environment);
		}

		if (copy.variables != null) {
			// Replace environment placeholders in variables
			copy.variables = copy.variables.replace("{{env}}", environment);
		}

		return copy;
	}

	/**
	 * Creates a copy as a test query
	 * 
	 * @return Copy configured for testing
	 */
	public GraphQLBody copyAsTest() {
		return copyForEnvironment("test");
	}

	/**
	 * Creates a copy as a development query
	 * 
	 * @return Copy configured for development
	 */
	public GraphQLBody copyAsDev() {
		return copyForEnvironment("dev");
	}

	/**
	 * Creates a copy as a production query
	 * 
	 * @return Copy configured for production
	 */
	public GraphQLBody copyAsProd() {
		return copyForEnvironment("prod");
	}

	/**
	 * Creates a copy with query operation type changed
	 * 
	 * @param newOperationType New operation type (query, mutation, subscription)
	 * @return Copy with changed operation type
	 */
	public GraphQLBody copyWithOperationType(String newOperationType) {
		if (this.query == null) {
			return copy();
		}

		String newQuery = this.query.replaceFirst("^\\s*(query|mutation|subscription)\\b", newOperationType);

		return copyWithQuery(newQuery);
	}

	/**
	 * Creates a copy converted to query operation
	 * 
	 * @return Copy as query operation
	 */
	public GraphQLBody copyAsQueryOperation() {
		return copyWithOperationType("query");
	}

	/**
	 * Creates a copy converted to mutation operation
	 * 
	 * @return Copy as mutation operation
	 */
	public GraphQLBody copyAsMutationOperation() {
		return copyWithOperationType("mutation");
	}

	/**
	 * Creates a copy converted to subscription operation
	 * 
	 * @return Copy as subscription operation
	 */
	public GraphQLBody copyAsSubscriptionOperation() {
		return copyWithOperationType("subscription");
	}

	/**
	 * Creates a copy with query comments removed
	 * 
	 * @return Copy without comments
	 */
	public GraphQLBody copyWithoutComments() {
		if (this.query == null) {
			return copy();
		}

		String queryWithoutComments = this.query.replaceAll("#.*$", "") // Remove line comments
				.replaceAll("\\n\\s*\\n", "\n") // Remove empty lines
				.trim();

		return copyWithQuery(queryWithoutComments);
	}

	/**
	 * Creates a copy with query validation placeholders replaced
	 * 
	 * @param replacements Map of placeholder -> value replacements
	 * @return Copy with placeholders replaced
	 */
	public GraphQLBody copyWithPlaceholdersReplaced(java.util.Map<String, String> replacements) {
		GraphQLBody copy = copy();

		if (replacements != null) {
			for (java.util.Map.Entry<String, String> entry : replacements.entrySet()) {
				if (copy.query != null) {
					copy.query = copy.query.replace(entry.getKey(), entry.getValue());
				}
				if (copy.variables != null) {
					copy.variables = copy.variables.replace(entry.getKey(), entry.getValue());
				}
			}
		}

		return copy;
	}

	/**
	 * Creates a copy with variables formatted (pretty-printed JSON)
	 * 
	 * @return Copy with formatted variables
	 */
	public GraphQLBody copyWithFormattedVariables() {
		if (this.variables == null || this.variables.trim().isEmpty()) {
			return copy();
		}

		// Basic JSON formatting - in production use a proper JSON library
		String formattedVars = this.variables.replaceAll("\\{", "{\n  ").replaceAll("\\}", "\n}")
				.replaceAll(",", ",\n  ").replaceAll(":\\s*", ": ").replaceAll("\\n\\s*\\n", "\n").trim();

		return copyWithVariables(formattedVars);
	}

	/**
	 * Creates a copy with variables minified
	 * 
	 * @return Copy with minified variables
	 */
	public GraphQLBody copyWithMinifiedVariables() {
		if (this.variables == null || this.variables.trim().isEmpty()) {
			return copy();
		}

		String minifiedVars = this.variables.replaceAll("\\s+", "").replaceAll("\\s*:\\s*", ":")
				.replaceAll("\\s*,\\s*", ",").trim();

		return copyWithVariables(minifiedVars);
	}

	/**
	 * Creates a minimal copy with only essential data
	 * 
	 * @return Copy with only query (no variables if empty)
	 */
	public GraphQLBody copyMinimal() {
		return GraphQLBody.builder().query(this.query).variables(hasVariables() ? this.variables : null).build();
	}

}