package es.alesqui.intelligence.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;

/**
 * Centralized configuration for the chat system.
 * All configurations related to timeouts, limits and cache.
 */
@Configuration
@ConfigurationProperties(prefix = "chat")
@Data
@Validated
public class ChatConfig {
  
  /**
   * General timeout for chat processing
   */
  @NotNull
  private Duration processingTimeout = Duration.ofSeconds(120);
  
  /**
   * Timeout for operations with tools/APIs
   */
  @NotNull
  private Duration toolsTimeout = Duration.ofSeconds(120);
  
  /**
   * Time threshold to show warnings (in milliseconds)
   */
  @Min(1000)
  private long timeoutWarning = 10000;
  
  /**
   * Token threshold to show warnings
   */
  @Min(1000)
  private long tokensWarning = 4000;
  
  /**
   * Maximum number of messages in conversation history
   */
  @Min(5)
  private int maxConversationHistory = 20;
  
  /**
   * Specific configurations for the reasoning formatter
   */
  private ReasoningConfig reasoning = new ReasoningConfig();
  
  /**
   * Configurations for automatic cleanup
   */
  private CleanupConfig cleanup = new CleanupConfig();
  
  /**
   * Configurations for stateless, utility AI calls (e.g., summarization).
   */
  private UtilityAiConfig utilityAi = new UtilityAiConfig();

  
  @Data
  public static class ReasoningConfig {
      /**
       * Timeout for reasoning formatting
       */
      private Duration formattingTimeout = Duration.ofSeconds(30);
      
      /**
       * Enable detailed reasoning formatting
       */
      private boolean enableDetailedFormatting = true;
  }
  
  @Data
  public static class CleanupConfig {
      /**
       * Interval for cleaning up inactive conversations
       */
      private Duration cleanupInterval = Duration.ofMinutes(5);
      
      /**
       * Inactivity time before cleaning up a conversation
       */
      private Duration inactivityThreshold = Duration.ofHours(1);
      
      /**
       * Enable automatic cleanup
       */
      private boolean enableAutoCleanup = true;
  }
  
  @Data
  public static class UtilityAiConfig {
      /**
       * Timeout for stateless AI operations that run in the background,
       * like generating summaries
       */
      @NotNull
      private Duration timeout = Duration.ofSeconds(20);
  }
}
