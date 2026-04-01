package es.alesqui.intelligence.service.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.model.ChatResponse;

import es.alesqui.intelligence.dto.chat.reasoning.ReasoningStep;
import es.alesqui.intelligence.dto.chat.reasoning.StepType;
import es.alesqui.intelligence.dto.chat.reasoning.ToolCallData;

/**
 * Stateful collector that captures reasoning steps (THOUGHT, TOOL_CALL,
 * FINAL_RESPONSE) during a single ReAct loop execution.
 * <p>
 * Create a new instance per chatWithTools() invocation — not a Spring bean.
 */
public class ReasoningStepCollector {

	private final List<ReasoningStep> steps = new ArrayList<>();
	private List<ToolCallData> lastPendingToolCalls = List.of();

	/**
	 * Captures the AI's thought/plan from a ChatResponse that contains tool calls.
	 * If the model omitted text, synthesizes a plan from the tool names.
	 */
	public void captureThought(ChatResponse response) {
		AssistantMessage aiMsg = response.getResult().getOutput();
		String planText = StringUtils.trimToEmpty(aiMsg.getText());

		if (StringUtils.isBlank(planText) && aiMsg.getToolCalls() != null
				&& !aiMsg.getToolCalls().isEmpty()) {
			String toolNames = aiMsg.getToolCalls().stream()
					.map(ToolCall::name)
					.filter(Objects::nonNull)
					.distinct()
					.collect(Collectors.joining(", "));
			planText = "Plan: I'll call " + toolNames
					+ " to gather the necessary data, then answer concisely.";
		}

		if (StringUtils.isNotBlank(planText)) {
			steps.add(new ReasoningStep(StepType.THOUGHT, planText));
		}
	}

	/**
	 * Captures the tool call requests from the assistant message and stores
	 * pending ToolCallData entries for later enrichment by ToolResponseProcessor.
	 */
	public void captureToolCalls(AssistantMessage aiMsg) {
		lastPendingToolCalls = new ArrayList<>();
		if (aiMsg.getToolCalls() != null && !aiMsg.getToolCalls().isEmpty()) {
			for (ToolCall toolCall : aiMsg.getToolCalls()) {
				lastPendingToolCalls.add(new ToolCallData(toolCall.name(), toolCall.arguments()));
			}
			steps.add(new ReasoningStep(StepType.TOOL_CALL, lastPendingToolCalls));
		}
	}

	/**
	 * Captures the final natural language response after the ReAct loop exits.
	 */
	public void captureFinalResponse(ChatResponse response) {
		String text = response.getResult().getOutput().getText();
		if (StringUtils.isNotBlank(text)) {
			steps.add(new ReasoningStep(StepType.FINAL_RESPONSE, text));
		}
	}

	/**
	 * Captures an error message as a FINAL_RESPONSE step (used in exception handler).
	 */
	public void captureError(String errorMessage) {
		steps.add(new ReasoningStep(StepType.FINAL_RESPONSE, errorMessage));
	}

	/**
	 * Returns the pending tool calls from the most recent captureToolCalls() invocation.
	 * Used by ToolResponseProcessor to enrich with status and timing.
	 */
	public List<ToolCallData> getPendingToolCalls() {
		return lastPendingToolCalls;
	}

	/**
	 * Returns all accumulated reasoning steps.
	 */
	public List<ReasoningStep> getSteps() {
		return steps;
	}
}
