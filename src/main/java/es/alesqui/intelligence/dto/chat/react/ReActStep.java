package es.alesqui.intelligence.dto.chat.react;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

/**
 * Represents a single complete step in the ReAct process. Contains thought,
 * action, result, and observation for one iteration.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReActStep {

	/**
	 * Sequential step number in the ReAct process.
	 */
	private int stepNumber;

	/**
	 * AI-generated thought for this step.
	 */
	private String thought;

	/**
	 * Action command to be executed.
	 */
	private ActionCommand action;

	/**
	 * Result obtained from executing the action.
	 */
	private String actionResult;

	/**
	 * AI observation analyzing the action result.
	 */
	private String observation;

	/**
	 * Timestamp when this step was created.
	 */
	private Instant timestamp;

	/**
	 * Duration of this step execution in milliseconds.
	 */
	private long durationMs;

	/**
	 * Creates a new ReAct step with the given step number.
	 *
	 * @param stepNumber sequential step identifier
	 * @return new ReActStep instance
	 */
	public static ReActStep create(int stepNumber) {
		ReActStep step = new ReActStep();
		step.stepNumber = stepNumber;
		step.timestamp = Instant.now();
		return step;
	}

	/**
	 * Sets the thought for this step.
	 *
	 * @param thought AI-generated thought
	 * @return this step for method chaining
	 */
	public ReActStep withThought(String thought) {
		this.thought = thought;
		return this;
	}

	/**
	 * Sets the action command for this step.
	 *
	 * @param action parsed action command
	 * @return this step for method chaining
	 */
	public ReActStep withAction(ActionCommand action) {
		this.action = action;
		return this;
	}

	/**
	 * Sets the action execution result.
	 *
	 * @param result action execution outcome
	 * @return this step for method chaining
	 */
	public ReActStep withActionResult(String result) {
		this.actionResult = result;
		return this;
	}

	/**
	 * Sets the observation for this step.
	 *
	 * @param observation AI analysis of the action result
	 * @return this step for method chaining
	 */
	public ReActStep withObservation(String observation) {
		this.observation = observation;
		return this;
	}

	/**
	 * Marks this step as complete and calculates execution duration.
	 *
	 * @return this step for method chaining
	 */
	public ReActStep complete() {
		this.durationMs = Instant.now().toEpochMilli() - timestamp.toEpochMilli();
		return this;
	}
}
