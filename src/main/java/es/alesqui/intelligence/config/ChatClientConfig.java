package es.alesqui.intelligence.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.extern.slf4j.Slf4j;

/**
 * Configuration class for AI chat client components.
 *
 * Supports multiple AI providers selectable via {@code alesqui.ai.provider}:
 * - {@code openai} (default) — uses OpenAI API with proxy-aware HTTP clients
 * - {@code ollama} — uses a self-hosted Ollama instance (auto-configured by Spring AI starter)
 *
 * The {@link ChatClient} bean is provider-agnostic: it depends on {@link ChatModel},
 * so the rest of the application is decoupled from the active provider.
 *
 * It depends on HTTP clients configured in HttpClientConfig for network communication.
 */
@Configuration
@Slf4j
public class ChatClientConfig {
    
    /**
     * OpenAI API key injected from application properties.
     * Expected property: spring.ai.openai.api-key
     */
    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    /**
     * Creates and configures an OpenAiApi bean for interacting with OpenAI services.
     * * This bean now consumes the pre-configured RestClient and WebClient.Builder beans
     * to ensure that all network settings, including the proxy, are applied correctly.
     *
     * @param restClient the configured RestClient for synchronous HTTP requests
     * @param webClientBuilder the pre-configured WebClient.Builder for reactive HTTP requests
     * @return a configured OpenAiApi instance with custom HTTP clients and API key
     */
    @Bean
    @ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "openai", matchIfMissing = true)
    public OpenAiApi openAiApi(RestClient restClient, WebClient.Builder webClientBuilder) {
        log.info("Configuring OpenAiApi with proxy-aware RestClient and WebClient.Builder");
        
        OpenAiApi openAiApi = OpenAiApi.builder()
            .apiKey(apiKey)
            .restClientBuilder(restClient.mutate())
            // Use the injected builder directly, which already contains the proxy configuration
            .webClientBuilder(webClientBuilder)
            .build();
            
        return openAiApi;
    }
    
    /**
     * Creates and configures an OpenAiChatModel bean for chat completions.
     * * This model provides the interface for interacting with OpenAI's chat completion
     * endpoints using the configured OpenAiApi.
     *
     * @param openAiApi the configured OpenAiApi for API communication
     * @return a configured OpenAiChatModel instance
     */
    @Bean
    @ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "openai", matchIfMissing = true)
    public OpenAiChatModel openAiChatModel(OpenAiApi openAiApi) {
        OpenAiChatModel openAiChatModel = OpenAiChatModel.builder()
            .openAiApi(openAiApi)
            .build();
            
        return openAiChatModel;
    }

    /**
     * Creates the main ChatClient bean for the application.
     * Provider-agnostic: receives whichever {@link ChatModel} is active.
     *
     * @param chatModel the active ChatModel (OpenAI or Ollama)
     * @return a configured ChatClient instance ready for use in the application
     */
    @Bean
    @Primary
    public ChatClient chatClient(ChatModel chatModel) {
        ChatClient client = ChatClient
        		.builder(chatModel)
        		.build();
        log.info("✅ Basic ChatClient created");
        return client;
    }
}
