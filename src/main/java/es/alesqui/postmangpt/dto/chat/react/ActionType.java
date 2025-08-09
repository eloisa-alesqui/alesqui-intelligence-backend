package es.alesqui.postmangpt.dto.chat.react;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Enumeration of available ReAct actions with metadata. Defines all possible
 * actions the AI agent can execute.
 */
@Getter
@RequiredArgsConstructor
public enum ActionType {

	/**
	 * Lists all available APIs in the system.
	 */
	LIST_APIS("list_apis", "List all available APIs", false),

	/**
	 * Retrieves detailed information about a specific API.
	 */
	GET_API_DETAILS("get_api_details", "Get detailed information about a specific API", true),

	/**
	 * Lists all endpoints available for a specific API.
	 */
	LIST_ENDPOINTS("list_endpoints", "List all endpoints for a specific API", true),

	/**
	 * Retrieves detailed information about a specific endpoint.
	 */
	GET_ENDPOINT_DETAILS("get_endpoint_details", "Get details about a specific endpoint", true),

	/**
	 * Executes an API call with the provided parameters.
	 */
	CALL_API("call_api", "Call an API endpoint with parameters", true),

	/**
	 * Provides the final answer to the user query.
	 */
	FINAL_ANSWER("final_answer", "Provide the final answer to the user", false);

	/**
	 * String representation of the action name.
	 */
	private final String actionName;

	/**
	 * Human-readable description of what this action does.
	 */
	private final String description;

	/**
	 * Whether this action requires additional parameters to execute.
	 */
	private final boolean requiresParameters;

	/**
	 * Converts a string action name to its corresponding ActionType.
	 *
	 * @param actionName string representation of the action
	 * @return matching ActionType enum value
	 * @throws IllegalArgumentException if action name is not recognized
	 */
	public static ActionType fromString(String actionName) {
		for (ActionType type : values()) {
			if (type.actionName.equalsIgnoreCase(actionName)) {
				return type;
			}
		}
		throw new IllegalArgumentException("Unknown action type: " + actionName);
	}
}
