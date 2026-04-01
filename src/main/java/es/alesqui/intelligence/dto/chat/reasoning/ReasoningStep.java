package es.alesqui.intelligence.dto.chat.reasoning;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Represents a single, distinct step in the AI's reasoning process.
 * It is designed to be sent to the frontend to build an interactive
 * visualization of the AI's 'ReAct' (Reason-Act) loop. A step can be one
 * of several types (see StepType), such as a thought, a tool call,
 * or the final response.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReasoningStep {

    /**
     * A unique identifier for this specific reasoning step instance.
     */
    private String id = UUID.randomUUID().toString();

    /**
     * The nature of this step (e.g., THOUGHT, TOOL_CALL).
     * See {@link StepType} for details.
     */
    private StepType type;

    /**
     * The textual content of the step.
     * Used for THOUGHT and FINAL_RESPONSE types.
     */
    private String textContent;

    /**
     * A list of tool calls made during this step.
     * Used only for the TOOL_CALL type.
     */
    private List<ToolCallData> toolCalls;

    /**
     * Constructor for text-only steps, such as THOUGHT or FINAL_RESPONSE.
     *
     * @param type The step type.
     * @param textContent The text associated with the step.
     */
    public ReasoningStep(StepType type, String textContent) {
        this.type = type;
        this.textContent = textContent;
    }

    /**
     * Constructor for steps that represent one or more tool calls.
     *
     * @param type The step type (should be TOOL_CALL).
     * @param toolCalls The list of tool calls executed in this step.
     */
    public ReasoningStep(StepType type, List<ToolCallData> toolCalls) {
        this.type = type;
        this.toolCalls = toolCalls;
    }
}