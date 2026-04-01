package es.alesqui.intelligence.dto.chat.response;

import org.springframework.ai.chat.messages.Message;

/**
 * Result of processing tool responses within a ReAct loop iteration.
 *
 * @param cleanedMessageForHistory The tool response message with payloads cleaned for the AI's next turn.
 * @param chartData                Chart data captured from a create_chart tool call, or null.
 */
public record ToolResponseProcessingResult(
		Message cleanedMessageForHistory,
		ChartData chartData) {
}
