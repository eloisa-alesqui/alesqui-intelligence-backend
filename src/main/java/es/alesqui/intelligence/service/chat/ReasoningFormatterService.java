package es.alesqui.intelligence.service.chat;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/**
 * Service responsible for formatting raw API reasoning data into a clear, 
 * professional, and visually appealing format using AI-powered text processing.
 * 
 * This service takes unstructured reasoning data and transforms it into a 
 * standardized format with proper markdown formatting, emojis, and clear 
 * step-by-step breakdowns that are easy to read and understand.
 * 
 * The formatting follows a consistent template that includes:
 * - User question extraction
 * - Action plan summary
 * - Step-by-step reasoning breakdown
 * - Tool usage documentation
 * - Clear conclusions
 * 
 * All operations are performed asynchronously using reactive programming
 * patterns to ensure non-blocking execution.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReasoningFormatterService {
    
    private final ChatClient chatClient;
    private final Scheduler boundedElasticScheduler;
    private final MeterRegistry meterRegistry;
    
    /**
     * System prompt template that defines the AI assistant's behavior and formatting rules.
     * This prompt ensures consistent output formatting across all reasoning operations.
     */
    private static final String SYSTEM_PROMPT = """
            You are a formatting assistant specialized in presenting API reasoning in a clear, professional manner.
            
            The user's question for this turn is: "%s"
            
            Your task is to:
            1. Clean up the provided reasoning data
            2. Format it with proper markdown and emojis with adequate spacing between sections
            3. Extract key information from tool results
            4. Provide a clear conclusion that answers the user's question
            5. Make it visually appealing and easy to read
                        
            Show your reasoning following this exact format:
                    
            '🎯 **User question:**
              "[Exact quote of the user's question]"
            📋 **Action plan:**
              [Brief description of the steps you will follow]
            ---
            🔧 **Step [number] - [Brief description of the step]**
            
            🛠️ **Tool used:**
              [tool_name]([parameters])
            
            📊 **Result obtained:**
              [Clear and concise summary of the most relevant result]
            ---
            [Repeat the above pattern for each additional step, always including the separator line and blank lines]
            ---
            ✨ **Conclusion:**
              [Final summary of what was achieved and how it answers the original question]'

                        
            Remember to:
            - Use horizontal separators (---) between steps
            - Maintain consistent indentation
            - Keep emojis aligned properly
            
            """;
    
    /**
     * Default timeout duration for AI processing operations.
     */
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(120);
    
    /**
     * Default error message returned when the service encounters technical difficulties.
     */
    private static final String DEFAULT_ERROR_MESSAGE = 
        "I apologize, but I'm experiencing technical difficulties. Please try again in a moment.";
    
    /**
     * Formats raw reasoning data into a structured, professional presentation using AI processing.
     * 
     * This method takes unstructured reasoning data and transforms it into a standardized format
     * that includes proper markdown formatting, emojis, and clear step-by-step breakdowns.
     * The operation is performed asynchronously to ensure non-blocking execution.
     * 
     * The formatted output includes:
     * - Extracted user question
     * - Action plan summary
     * - Detailed step-by-step reasoning
     * - Tool usage documentation
     * - Clear conclusions and results
     * 
     * @param userQuestion User question
     * @param rawReasoningData The unstructured reasoning data to be formatted.
     *                        Must not be null or empty.
     * @return A Mono containing the formatted reasoning as a string, or an error message
     *         if processing fails. The Mono will complete within the configured timeout period.
     * @throws IllegalArgumentException if rawReasoningData is null or empty
     * 
     * @since 1.0
     */
    public Mono<String> formatReasoning(String userQuestion, String rawReasoningData) {
        validateInput(userQuestion, rawReasoningData);
        
        return Mono.defer(() -> Mono.fromCallable(() -> {
            log.debug("Starting reasoning formatting process");
            long startTime = System.currentTimeMillis();
            
            String finalSystemPrompt = String.format(SYSTEM_PROMPT, userQuestion);
            
            try {
                String response = chatClient.prompt()
                    .system(finalSystemPrompt)
                    .user(rawReasoningData)
                    .call()
                    .content();
                
                log.debug("Reasoning formatting completed successfully");
                return response;
                
            } catch (Exception e) {
                log.error("Error during reasoning formatting: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to format reasoning data", e);
            } finally {
                long endTime = System.currentTimeMillis();
                recordExecutionTime(endTime - startTime);
            }
        }).subscribeOn(boundedElasticScheduler))
                .timeout(DEFAULT_TIMEOUT)
                .doOnSuccess(response -> log.info("Reasoning formatting completed successfully"))
                .doOnError(error -> log.error("Reasoning formatting failed: {}", error.getMessage()))
                .onErrorReturn(DEFAULT_ERROR_MESSAGE);
    }
    
    /**
     * Validates the input parameters to ensure they meet the required criteria.
     * 
     * @param userQuestion User question
     * @param rawReasoningData The input data to validate
     * @throws IllegalArgumentException if the input is null or empty
     */
    private void validateInput(String userQuestion, String rawReasoningData) {
        if (!StringUtils.hasText(userQuestion) || !StringUtils.hasText(rawReasoningData)) {
            throw new IllegalArgumentException("User question and raw reasoning data cannot be null or empty");
        }
    }
    
    /**
     * Records the execution time metrics for monitoring and performance analysis.
     * 
     * @param executionTimeMs The execution time in milliseconds
     */
    private void recordExecutionTime(long executionTimeMs) {
        if (meterRegistry != null) {
            meterRegistry.timer("reasoning.formatter.execution.time")
                .record(executionTimeMs, TimeUnit.MILLISECONDS);
        }
    }
}