package es.alesqui.intelligence.service.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import es.alesqui.intelligence.dto.chat.reasoning.ToolCallData;
import es.alesqui.intelligence.dto.chat.reasoning.ToolCallStatus;
import es.alesqui.intelligence.dto.chat.response.ApiCallResponse;
import es.alesqui.intelligence.dto.chat.response.ChartData;
import es.alesqui.intelligence.dto.chat.response.ToolResponseProcessingResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Processes tool execution results within the ReAct loop:
 * <ul>
 *   <li>Parses responses (ApiCallResponse, ChartData, plain String)</li>
 *   <li>Enriches ToolCallData with status and timing for the frontend</li>
 *   <li>Cleans payloads for the AI's next turn (removing wrapper metadata)</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ToolResponseProcessor {

	private final ObjectMapper objectMapper;

	/**
	 * Processes tool execution results, enriching pending ToolCallData and building
	 * a cleaned message for the AI's next turn.
	 *
	 * @param originalToolResponseMessage The raw message from tool execution.
	 * @param pendingToolCalls            The pending ToolCallData entries to enrich with status/timing.
	 * @param totalTimeTool               Approximate total tool execution time in ms (fallback).
	 * @return A result containing the cleaned message and any captured chart data.
	 */
	public ToolResponseProcessingResult processToolResponses(
			Message originalToolResponseMessage,
			List<ToolCallData> pendingToolCalls,
			long totalTimeTool) {

		ChartData capturedChartData = null;

		if (!(originalToolResponseMessage instanceof ToolResponseMessage toolResponseMsg)
				|| pendingToolCalls.isEmpty()) {
			log.warn("Unexpected state: Tool response message type mismatch or no pending calls.");
			return new ToolResponseProcessingResult(originalToolResponseMessage, null);
		}

		List<ToolResponseMessage.ToolResponse> cleanedResponsesForAI = new ArrayList<>();

		for (int i = 0; i < toolResponseMsg.getResponses().size(); i++) {
			ToolResponseMessage.ToolResponse toolResponse = toolResponseMsg.getResponses().get(i);
			ToolCallData callToUpdate = pendingToolCalls.get(i);
			String toolName = callToUpdate.getToolName();

			String fullResponseJson = toolResponse.responseData();
			Object payloadObjectForAI = fullResponseJson;

			// Store the full JSON in ToolCallData for the frontend modal
			callToUpdate.setResponseDataJson(fullResponseJson);

			// Try to parse and extract metadata + determine clean payload for AI
			try {
				ApiCallResponse apiResponse = objectMapper.readValue(fullResponseJson, ApiCallResponse.class);

				callToUpdate.setStatus(apiResponse.isSuccess() ? ToolCallStatus.SUCCESS : ToolCallStatus.ERROR);
				callToUpdate.setExecutionTimeMs(apiResponse.getExecutionTimeMs());

				if (apiResponse.isSuccess()) {
					payloadObjectForAI = extractSuccessPayload(apiResponse);
				} else {
					payloadObjectForAI = apiResponse.getResponseData() != null
							? apiResponse.getResponseData()
							: apiResponse.getErrorMessage();
				}

			} catch (Exception e) {
				// Not an ApiCallResponse — try simpler formats
				callToUpdate.setStatus(ToolCallStatus.SUCCESS);
				callToUpdate.setExecutionTimeMs(totalTimeTool);

				try {
					String stringValue = objectMapper.readValue(fullResponseJson, String.class);
					if (stringValue.startsWith("Error:")) {
						callToUpdate.setStatus(ToolCallStatus.ERROR);
					}
					payloadObjectForAI = stringValue;
				} catch (Exception e2) {
					// Not a plain String — try ChartData
					try {
						if ("create_chart".equals(toolName)) {
							capturedChartData = objectMapper.readValue(fullResponseJson, ChartData.class);
							payloadObjectForAI = "Chart data generated successfully.";
							log.info("ChartData object captured successfully!");
						}
					} catch (Exception e3) {
						log.warn("Could not parse non-ApiCallResponse tool output: {}", fullResponseJson, e2);
						payloadObjectForAI = fullResponseJson;
					}
				}
			}

			// Serialize the clean payload for the AI's next turn
			try {
				String payloadJsonForAI = objectMapper.writeValueAsString(payloadObjectForAI);
				cleanedResponsesForAI.add(new ToolResponseMessage.ToolResponse(
						toolResponse.id(), toolName, payloadJsonForAI));
			} catch (Exception e) {
				log.warn("Failed to serialize cleaned payload for tool {}: {}", toolName, e.getMessage());
				cleanedResponsesForAI.add(new ToolResponseMessage.ToolResponse(
						toolResponse.id(), toolName, fullResponseJson));
			}
		}

		Message cleanedMessage = new ToolResponseMessage(cleanedResponsesForAI);
		return new ToolResponseProcessingResult(cleanedMessage, capturedChartData);
	}

	private Object extractSuccessPayload(ApiCallResponse apiResponse) {
		if (apiResponse.getResponseData() instanceof Map<?, ?> responseMap) {
			if (responseMap.containsKey("rawResponse")) {
				return responseMap.get("rawResponse");
			} else if (responseMap.containsKey("response")) {
				return responseMap.get("response");
			}
		}
		return apiResponse.getResponseData();
	}
}
