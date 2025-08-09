package es.alesqui.postmangpt.service.chat;

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

import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

@Disabled
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("SpringAIService Integration Tests")
class SpringAIServiceIntegrationTest {

	@MockitoBean
    private VectorStore vectorStore;

    @Autowired
    private SpringAIService springAIService;

    private static final String SYSTEM_PROMPT = "You are a helpful assistant. Be concise in your responses.";
    private static final String CONVERSATION_ID = "test-conversation-" + System.currentTimeMillis();

    @BeforeEach
    void setUp() {
        // Clean up any existing conversation
        springAIService.clearConversation(CONVERSATION_ID);
    }
    
    @Test
    @DisplayName("Testing connection")
    void testConnection() {
        Mono<String> result = springAIService.testConnection();

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
        StepVerifier.create(springAIService.chat(SYSTEM_PROMPT, userPrompt, CONVERSATION_ID))
                .assertNext(response -> {
                    assertThat(response).isNotNull();
                    assertThat(response).isNotEmpty();
                    assertThat(response.toLowerCase()).contains("4");
                })
                .verifyComplete();

        // Verify conversation was stored
        assertThat(springAIService.getConversationSize(CONVERSATION_ID)).isEqualTo(2);
    }

    @Test
    @DisplayName("Should maintain conversation context across multiple interactions")
    void testConversationMemory() {
        // First interaction
        StepVerifier.create(springAIService.chat(
                SYSTEM_PROMPT, 
                "My name is TestUser. Remember it.", 
                CONVERSATION_ID))
                .assertNext(response -> {
                    assertThat(response).isNotNull();
                })
                .verifyComplete();

        // Second interaction - should remember the name
        StepVerifier.create(springAIService.chat(
                SYSTEM_PROMPT, 
                "What is my name?", 
                CONVERSATION_ID))
                .assertNext(response -> {
                    assertThat(response).isNotNull();
                    assertThat(response).containsIgnoringCase("TestUser");
                })
                .verifyComplete();

        // Verify conversation history
        assertThat(springAIService.getConversationSize(CONVERSATION_ID)).isEqualTo(4);
    }

    @Test
    @DisplayName("Should handle chat without memory")
    void testChatWithoutMemory() {
        // Given
        String userPrompt = "What is the capital of France? Just the city name.";

        // When & Then
        StepVerifier.create(springAIService.chatWithoutMemory(SYSTEM_PROMPT, userPrompt))
                .assertNext(response -> {
                    assertThat(response).isNotNull();
                    assertThat(response).containsIgnoringCase("Paris");
                })
                .verifyComplete();

        // Verify no conversation history was stored
        Map<String, Integer> stats = springAIService.getConversationStats();
        assertThat(stats).doesNotContainKey(CONVERSATION_ID);
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
      StepVerifier.create(springAIService.extractStructuredData(systemPrompt, userPrompt, CONVERSATION_ID, WeatherInfo.class))
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
        StepVerifier.create(springAIService.chat(
                SYSTEM_PROMPT, 
                "I'm in conversation 1. My favorite color is blue.", 
                convId1))
                .assertNext(response -> {
                    assertThat(response).isNotNull();
                })
                .verifyComplete();

        StepVerifier.create(springAIService.chat(
                SYSTEM_PROMPT, 
                "I'm in conversation 2. My favorite color is red.", 
                convId2))
                .assertNext(response -> {
                    assertThat(response).isNotNull();
                })
                .verifyComplete();

        // Verify each conversation maintains its own context
        StepVerifier.create(springAIService.chat(
                SYSTEM_PROMPT, 
                "What is my favorite color?", 
                convId1))
                .assertNext(response -> {
                    assertThat(response).containsIgnoringCase("blue");
                })
                .verifyComplete();

        StepVerifier.create(springAIService.chat(
                SYSTEM_PROMPT, 
                "What is my favorite color?", 
                convId2))
                .assertNext(response -> {
                    assertThat(response).containsIgnoringCase("red");
                })
                .verifyComplete();

        // Verify stats
        Map<String, Integer> stats = springAIService.getConversationStats();
        assertThat(stats).containsKeys(convId1, convId2);
        assertThat(stats.get(convId1)).isEqualTo(4);
        assertThat(stats.get(convId2)).isEqualTo(4);
    }

    @Test
    @DisplayName("Should respect conversation history limit")
    void testHistoryLimit() {
        // This test might be expensive due to multiple API calls
        String limitTestConvId = CONVERSATION_ID + "-limit";

        // Add 12 interactions (24 messages total, should trim to 20)
        for (int i = 0; i < 12; i++) {
            StepVerifier.create(springAIService.chat(
                    SYSTEM_PROMPT, 
                    "Message number " + i, 
                    limitTestConvId))
                    .assertNext(response -> assertThat(response).isNotNull())
                    .verifyComplete();
            
            int currentSize = springAIService.getConversationSize(limitTestConvId);
            assertThat(currentSize).isLessThanOrEqualTo(20);
        }

        // Verify history was trimmed
        assertThat(springAIService.getConversationSize(limitTestConvId)).isEqualTo(20);
    }

    @Test
    @DisplayName("Should clear conversation properly")
    void testClearConversation() {
        // Create a conversation
        StepVerifier.create(springAIService.chat(
                SYSTEM_PROMPT, 
                "Hello, this is a test", 
                CONVERSATION_ID))
                .assertNext(response -> assertThat(response).isNotNull())
                .verifyComplete();

        assertThat(springAIService.getConversationSize(CONVERSATION_ID)).isGreaterThan(0);

        // Clear it
        springAIService.clearConversation(CONVERSATION_ID);

        // Verify it's cleared
        assertThat(springAIService.getConversationSize(CONVERSATION_ID)).isEqualTo(0);
    }
}
