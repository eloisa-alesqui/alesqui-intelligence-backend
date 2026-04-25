package es.alesqui.intelligence.service.chat;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("ollama-test")
@EnabledIfEnvironmentVariable(named = "OLLAMA_BASE_URL", matches = ".+")
class AIChatServiceOllamaIT extends AIChatServiceIntegrationTest {
}
