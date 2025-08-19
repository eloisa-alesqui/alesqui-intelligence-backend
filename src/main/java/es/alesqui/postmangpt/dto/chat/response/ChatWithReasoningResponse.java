package es.alesqui.postmangpt.dto.chat.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChatWithReasoningResponse {

	private org.springframework.ai.chat.model.ChatResponse chatResponse;
    private String formattedReasoning;

}
