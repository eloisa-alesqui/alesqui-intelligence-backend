package es.alesqui.intelligence.service.chat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("AIChatService Integration Tests")
class AIChatServiceIntegrationTest {

	@MockitoBean
    private VectorStore vectorStore;

    @Autowired
    private AIChatService aiChatService;

    private static final String SYSTEM_PROMPT = "You are a helpful assistant. Be concise in your responses.";
    private static final String CONVERSATION_ID = "test-conversation-" + System.currentTimeMillis();

    @BeforeEach
    void setUp() {
        // Clean up any existing conversation
        //aiChatService.clearConversation(CONVERSATION_ID);
    }
    
    @Test
    @DisplayName("Testing connection")
    void testConnection() {
        Mono<String> result = aiChatService.testConnection();

        StepVerifier.create(result)
        .expectNextMatches(response -> response.contains("Connection test successful"))
        .verifyComplete();
    }

    @Test
    @DisplayName("Should successfully complete a real chat interaction")
    void testRealChat() {
        // Given
        String userPrompt = "What is 2 + 2? Just give me the number.";

        // When & Then
        StepVerifier.create(aiChatService.chat(SYSTEM_PROMPT, userPrompt, CONVERSATION_ID))
                .assertNext(response -> {
                    assertThat(response).isNotNull();
                    assertThat(response).isNotEmpty();
                    assertThat(response.toLowerCase()).contains("4");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should maintain conversation context across multiple interactions")
    void testConversationMemory() {
        // First interaction
        StepVerifier.create(aiChatService.chat(
                SYSTEM_PROMPT, 
                "My name is TestUser. Remember it.", 
                CONVERSATION_ID))
                .assertNext(response -> {
                    assertThat(response).isNotNull();
                })
                .verifyComplete();

        // Second interaction - should remember the name
        StepVerifier.create(aiChatService.chat(
                SYSTEM_PROMPT, 
                "What is my name?", 
                CONVERSATION_ID))
                .assertNext(response -> {
                    assertThat(response).isNotNull();
                    assertThat(response).containsIgnoringCase("TestUser");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle chat without memory")
    void testChatWithoutMemory() {
        // Given
        String userPrompt = "What is the capital of France? Just the city name.";

        // When & Then
        StepVerifier.create(aiChatService.chatWithoutMemory(SYSTEM_PROMPT, userPrompt))
                .assertNext(response -> {
                    assertThat(response).isNotNull();
                    assertThat(response).containsIgnoringCase("Paris");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should extract structured data from response with system prompt and conversation ID")
    void testExtractStructuredData() {
      // Define a record for structured data
      record WeatherInfo(String city, int temperature, String condition) {}

      // Given
      String systemPrompt = "You are a weather information assistant. " +
                           "Extract weather data and return it in the exact format requested. " +
                           "Be precise with the data extraction.";
      
      String userPrompt = "Give me weather information for Madrid, Spain. " +
                         "Temperature should be 22 degrees and condition sunny.";
      
      // When & Then
      StepVerifier.create(aiChatService.extractStructuredData(systemPrompt, userPrompt, WeatherInfo.class))
              .assertNext(weather -> {
                  assertThat(weather).isNotNull();
                  assertThat(weather.city()).containsIgnoringCase("Madrid");
                  assertThat(weather.temperature()).isGreaterThan(0);
                  assertThat(weather.condition()).isNotEmpty();
              })
              .verifyComplete();
    }

    @Test
    @DisplayName("Should handle multiple concurrent conversations")
    void testConcurrentConversations() {
        String convId1 = CONVERSATION_ID + "-1";
        String convId2 = CONVERSATION_ID + "-2";

        // Start two different conversations
        StepVerifier.create(aiChatService.chat(
                SYSTEM_PROMPT, 
                "I'm in conversation 1. My favorite color is blue.", 
                convId1))
                .assertNext(response -> {
                    assertThat(response).isNotNull();
                })
                .verifyComplete();

        StepVerifier.create(aiChatService.chat(
                SYSTEM_PROMPT, 
                "I'm in conversation 2. My favorite color is red.", 
                convId2))
                .assertNext(response -> {
                    assertThat(response).isNotNull();
                })
                .verifyComplete();

        // Verify each conversation maintains its own context
        StepVerifier.create(aiChatService.chat(
                SYSTEM_PROMPT, 
                "What is my favorite color?", 
                convId1))
                .assertNext(response -> {
                    assertThat(response).containsIgnoringCase("blue");
                })
                .verifyComplete();

        StepVerifier.create(aiChatService.chat(
                SYSTEM_PROMPT, 
                "What is my favorite color?", 
                convId2))
                .assertNext(response -> {
                    assertThat(response).containsIgnoringCase("red");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should respect conversation history limit")
    void testHistoryLimit() {
        // This test might be expensive due to multiple API calls
        String limitTestConvId = CONVERSATION_ID + "-limit";

        // Add 12 interactions (24 messages total, should trim to 20)
        for (int i = 0; i < 12; i++) {
            StepVerifier.create(aiChatService.chat(
                    SYSTEM_PROMPT, 
                    "Message number " + i, 
                    limitTestConvId))
                    .assertNext(response -> assertThat(response).isNotNull())
                    .verifyComplete();
        }
    }

}
