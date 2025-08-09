package es.alesqui.postmangpt.dto.chat.react;

import lombok.Data;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Context object that maintains state throughout a ReAct conversation cycle.
 * Tracks the complete reasoning process including thoughts, actions, and
 * observations.
 */
@Data
public class ReActContext {

	/**
	 * Original user query that initiated the ReAct process.
	 */
	private final String userQuery;

	/**
	 * Unique identifier for the conversation session.
	 */
	private final String conversationId;

	/**
	 * List of AI thoughts generated during the reasoning process.
	 */
	private final List<String> thoughts = new ArrayList<>();

	/**
	 * List of actions executed by the AI agent.
	 */
	private final List<String> actions = new ArrayList<>();

	/**
	 * Results obtained from executing each action.
	 */
	private final List<String> actionResults = new ArrayList<>();

	/**
	 * AI observations analyzing the action results.
	 */
	private final List<String> observations = new ArrayList<>();
	
	/**
	 * Metadata map for storing additional context information during processing.
	 */
	private final Map<String, Boolean> metadata = new HashMap<>();

	/**
	 * Number of ReAct iterations completed.
	 */
	private int iterations = 0;

	/**
	 * Flag indicating if the ReAct process has reached completion.
	 */
	private boolean complete = false;

	/**
	 * Final answer provided to the user after completing the ReAct cycle.
	 */
	private String finalAnswer;

	/**
	 * Creates a new ReAct context for the given query and conversation.
	 *
	 * @param userQuery      original user question
	 * @param conversationId unique conversation identifier
	 */
	public ReActContext(String userQuery, String conversationId) {
		this.userQuery = userQuery;
		this.conversationId = conversationId;
	}

	/**
	 * Adds a new thought to the reasoning process.
	 *
	 * @param thought AI-generated thought content
	 * @return this context for method chaining
	 */
	public ReActContext addThought(String thought) {
		thoughts.add(thought);
		return this;
	}

	/**
	 * Adds the result of an executed action.
	 *
	 * @param result action execution result
	 * @return this context for method chaining
	 */
	public ReActContext addActionResult(String result) {
		actionResults.add(result);
		return this;
	}

	/**
	 * Adds an observation analyzing the action results.
	 *
	 * @param observation AI-generated observation
	 * @return this context for method chaining
	 */
	public ReActContext addObservation(String observation) {
		observations.add(observation);
		return this;
	}
	
	/**
	 * Adds metadata to the context.
	 *
	 * @param key   metadata key
	 * @param value metadata value
	 * @return this context for method chaining
	 */
	public ReActContext addMetadata(String key, Boolean value) {
	    metadata.put(key, value);
	    return this;
	}

	/**
	 * Increments the iteration counter.
	 *
	 * @return this context for method chaining
	 */
	public ReActContext incrementIteration() {
		this.iterations++;
		return this;
	}

	/**
	 * Marks the ReAct process as complete.
	 *
	 * @return this context for method chaining
	 */
	public ReActContext markComplete() {
		this.complete = true;
		return this;
	}

	/**
	 * Sets the final answer and marks the process as complete.
	 *
	 * @param answer final answer to the user query
	 * @return this context for method chaining
	 */
	public ReActContext setFinalAnswer(String answer) {
		this.finalAnswer = answer;
		return this;
	}

	/**
	 * Gets the most recent thought generated.
	 *
	 * @return last thought or empty string if none exists
	 */
	public String getLastThought() {
		return thoughts.isEmpty() ? "" : thoughts.get(thoughts.size() - 1);
	}

	/**
	 * Gets the most recent action executed.
	 *
	 * @return last action or empty string if none exists
	 */
	public String getLastAction() {
		return actions.isEmpty() ? "" : actions.get(actions.size() - 1);
	}

	/**
	 * Gets the most recent action result.
	 *
	 * @return last action result or empty string if none exists
	 */
	public String getLastActionResult() {
		return actionResults.isEmpty() ? "" : actionResults.get(actionResults.size() - 1);
	}

	/**
	 * Returns chronological history of all ReAct steps with emoji prefixes.
	 * Interleaves thoughts, actions, and observations in execution order.
	 *
	 * @return ordered list of all ReAct steps
	 */
	public List<String> getStepHistory() {
		List<String> history = new ArrayList<>();
		int maxSteps = Math.max(Math.max(thoughts.size(), actionResults.size()), observations.size());

		for (int i = 0; i < maxSteps; i++) {
			if (i < thoughts.size()) {
				history.add("💭 " + thoughts.get(i));
			}
			if (i < actionResults.size()) {
				history.add("🎬 " + actionResults.get(i));
			}
			if (i < observations.size()) {
				history.add("👁️ " + observations.get(i));
			}
		}
		return history;
	}
}