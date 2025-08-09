package es.alesqui.postmangpt.service;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import es.alesqui.postmangpt.service.chat.SpringAIService;
import reactor.test.StepVerifier;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Diagnostic Test")
@Disabled
public class DiagnosticTest {

	@MockitoBean
	private VectorStore vectorStore;

	@Autowired
	private SpringAIService springAIService;

	@Test
	void contextLoads() {
		System.out.println("Context loaded successfully!");
	}

	@Test
	@DisplayName("Should verify AI service connectivity through configured proxy")
	void testAIServiceConnectivity() {

		String systemPrompt = "You are a connectivity test assistant.";
		String userPrompt = "Respond with 'OK' if you can process this request.";
		String testConversationId = "connectivity-test-" + System.currentTimeMillis();

		record ConnectivityResponse(String status) {
		}

		StepVerifier.create(springAIService.extractStructuredData(systemPrompt, userPrompt, testConversationId,
				ConnectivityResponse.class)).assertNext(response -> {
					assertThat(response).isNotNull();
					assertThat(response.status()).isEqualTo("OK");
				}).verifyComplete();
	}

}
