package es.alesqui.intelligence.service.chat;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class AIChatServiceOpenAiIT extends AIChatServiceIntegrationTest {
}
