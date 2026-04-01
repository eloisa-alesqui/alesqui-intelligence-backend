package es.alesqui.intelligence.service.chat;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import es.alesqui.intelligence.dto.chat.response.SseEvent;
import reactor.core.publisher.Sinks;

/**
 * Detects when the AI model writes an "Act:" step in text without actually
 * emitting a tool call, and forces re-execution to produce the proper tool call.
 */
@Component
public class HallucinationCorrector {

	/**
	 * If the response has no tool calls but contains text indicating the model
	 * intended to call a tool (e.g. "Act:" or "call `...`"), sends a nudge
	 * message and re-prompts the model.
	 *
	 * @return The corrected response if hallucination was detected, or the original response.
	 */
	public ChatResponse correctIfHallucinated(
			ChatResponse initialResponse,
			List<Message> turnHistory,
			ChatClient chatClient,
			ChatOptions chatOptions,
			ToolContext toolContext,
			Sinks.Many<SseEvent> statusSink) {

		if (!initialResponse.hasToolCalls()) {
			String txt = StringUtils.defaultIfEmpty(initialResponse.getResult().getOutput().getText(), "");
			if (txt.contains("Act:") || txt.matches("(?s).*(?i)call `.+`.*")) {

				statusSink.tryEmitNext(SseEvent.status("Aligning tool execution..."));

				turnHistory.add(new SystemMessage(
						"You indicated an Act step. Emit the tool call now using one of the registered tools. Do not include any other text."));

				Prompt prompt = new Prompt(new ArrayList<>(turnHistory), chatOptions);
				ChatResponse correctedResponse = chatClient.prompt(prompt)
						.toolContext(toolContext.getContext())
						.call()
						.chatResponse();

				turnHistory.add(correctedResponse.getResult().getOutput());

				return correctedResponse;
			}
		}
		return initialResponse;
	}
}
