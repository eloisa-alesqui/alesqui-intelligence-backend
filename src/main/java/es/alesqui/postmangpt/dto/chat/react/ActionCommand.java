package es.alesqui.postmangpt.dto.chat.react;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Parsed action command extracted from AI thought process. Contains the action
 * type and associated parameters for execution.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ActionCommand {

	/**
	 * Type of action to execute (e.g., list_apis, call_api, final_answer).
	 */
	private String type;

	/**
	 * Parameters required for action execution as a string.
	 */
	private String parameters;

}