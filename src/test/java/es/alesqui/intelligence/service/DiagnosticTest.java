package es.alesqui.intelligence.service;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import es.alesqui.intelligence.service.chat.AIChatService;
import reactor.test.StepVerifier;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Diagnostic Test")
@Disabled
public class DiagnosticTest {

	@MockitoBean
	private VectorStore vectorStore;

	@Autowired
	private AIChatService aiChatService;

	@Test
	void contextLoads() {
		System.out.println("Context loaded successfully!");
	}

	@Test
	@DisplayName("Should verify AI service connectivity through configured proxy")
	void testAIServiceConnectivity() {

		String systemPrompt = "You are a connectivity test assistant.";
		String userPrompt = "Respond with 'OK' if you can process this request.";

		record ConnectivityResponse(String status) {
		}

		StepVerifier.create(aiChatService.extractStructuredData(systemPrompt, userPrompt,
				ConnectivityResponse.class)).assertNext(response -> {
					assertThat(response).isNotNull();
					assertThat(response.status()).isEqualTo("OK");
				}).verifyComplete();
	}

}
