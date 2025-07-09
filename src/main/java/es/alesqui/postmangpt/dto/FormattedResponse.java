package es.alesqui.postmangpt.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * User-friendly formatted response that presents API results in natural language.
 * 
 * This class represents the final output that PostmanGPT presents to users after
 * processing raw API responses. It transforms technical API data into conversational,
 * easy-to-understand responses while maintaining accuracy and providing actionable
 * follow-up suggestions.
 * 
 * Usage Example:
 * FormattedResponse response = FormattedResponse.builder()
 *     .summary("Found 3 active users in Engineering department")
 *     .detailedResponse("Here are the users I found:\n1. John Doe (john@company.com)...")
 *     .suggestedFollowUps(List.of("Show me their recent projects", "Who joined this month?"))
 *     .executionTime(245L)
 *     .build();
 * 
 * Flow Context:
 * 1. User asks: "Show me all engineers who joined this year"
 * 2. AI Service converts to APIRequest
 * 3. API Execution Engine gets APIResponse
 * 4. AI Service processes APIResponse into this FormattedResponse
 * 5. User receives this natural language response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormattedResponse {
  
  /**
   * Brief summary of the API response results.
   * 
   * A concise, one-sentence overview of what was found or accomplished.
   * This gives users immediate context about the response before diving
   * into details. Should be clear, specific, and actionable.
   * 
   * Summary Examples:
   * - "Found 15 active users in the Engineering department"
   * - "Successfully created new user account for Jane Smith"
   * - "No orders found for the specified date range"
   * - "Updated 3 employee records with new department assignments"
   * - "Retrieved customer details for account #12345"
   * - "Found 8 pending support tickets requiring attention"
   * 
   * Error Summaries:
   * - "Unable to access user data - authentication required"
   * - "The requested user ID does not exist"
   * - "API rate limit exceeded - please try again in 5 minutes"
   * - "Server temporarily unavailable - please retry later"
   * 
   * Best Practices:
   * - Start with action result: "Found", "Created", "Updated", "Unable to"
   * - Include specific numbers when relevant: "15 users", "3 records"
   * - Mention key context: department, date range, status
   * - Keep under 100 characters for quick scanning
   * - Use present tense and active voice
   * 
   * Usage: Displayed prominently in the UI as the main response headline.
   */
  private String summary;
  
  /**
   * Comprehensive formatted response with full details.
   * 
   * The complete, human-readable explanation of the API results. This field
   * contains the detailed information users need, formatted in a conversational
   * and easy-to-scan manner. Uses natural language, bullet points, tables,
   * and structured formatting as appropriate.
   * 
   * Formatting Guidelines:
   * - Use conversational tone: "Here's what I found..." instead of "Results:"
   * - Structure with headers and bullet points for readability
   * - Include relevant details but avoid overwhelming users
   * - Format data in tables or lists when appropriate
   * - Explain technical terms in user-friendly language
   * 
   * Success Response Examples:
   * 
   * // List of users
   * detailedResponse = """
   * Here are the 5 engineers who joined this year:
   * 
   * 1. **John Doe** (john@company.com)
   *    - Joined: March 15, 2024
   *    - Team: Backend Development
   *    - Status: Active
   * 
   * 2. **Jane Smith** (jane@company.com)
   *    - Joined: July 8, 2024
   *    - Team: Frontend Development
   *    - Status: Active
   * 
   * All new hires have completed their onboarding process.
   * """;
   * 
   * // Single record details
   * detailedResponse = """
   * Here are the details for Customer #12345:
   * 
   * **Contact Information:**
   * - Name: Acme Corporation
   * - Email: contact@acme.com
   * - Phone: (555) 123-4567
   * 
   * **Account Status:**
   * - Status: Active Premium
   * - Since: January 2023
   * - Next Billing: December 1, 2024
   * 
   * **Recent Activity:**
   * - Last login: 2 hours ago
   * - Support tickets: 0 open
   * """;
   * 
   * Error Response Examples:
   * detailedResponse = """
   * I couldn't retrieve the user information because the API key has expired.
   * 
   * **What happened:**
   * The authentication token used to access the user management API is no longer valid.
   * 
   * **What you can do:**
   * 1. Contact your system administrator to refresh the API credentials
   * 2. Try again once the credentials have been updated
   * 3. Check if you have access to an alternative user lookup method
   * """;
   * 
   * Content Structure:
   * - Opening statement explaining what was found/done
   * - Main content (lists, tables, details)
   * - Additional context or explanations
   * - Next steps or recommendations when relevant
   */
  private String detailedResponse;
  
  /**
   * List of suggested follow-up questions or actions.
   * 
   * Intelligent suggestions for what the user might want to do next,
   * based on the current results and common usage patterns. These help
   * users discover additional capabilities and continue their workflow
   * naturally.
   * 
   * Suggestion Categories:
   * 
   * **Drill-down questions:**
   * - "Show me John Doe's recent projects"
   * - "What are the details for order #12345?"
   * - "Who are the managers in this department?"
   * 
   * **Related queries:**
   * - "Show me users who joined last month"
   * - "What about the Marketing department?"
   * - "Are there any inactive users?"
   * 
   * **Action suggestions:**
   * - "Export this list to CSV"
   * - "Send welcome emails to new hires"
   * - "Update their department assignments"
   * 
   * **Comparative questions:**
   * - "How does this compare to last year?"
   * - "What's the trend over the past 6 months?"
   * - "Show me the same data for other departments"
   * 
   * **Filtering suggestions:**
   * - "Filter by hire date"
   * - "Show only active employees"
   * - "Exclude contractors"
   * 
   * Generation Strategy:
   * - Analyze current results to suggest logical next steps
   * - Consider user's apparent intent and workflow
   * - Offer both specific and general follow-up options
   * - Limit to 3-5 suggestions to avoid overwhelming users
   * - Phrase as natural questions the user might ask
   * 
   * Example for user search results:
   * suggestedFollowUps = List.of(
   *     "Show me their current projects",
   *     "Who are their direct managers?",
   *     "What about part-time employees?",
   *     "Export this list to spreadsheet"
   * );
   */
  private List<String> suggestedFollowUps;
  
  /**
   * Indicates whether an error occurred during processing.
   * 
   * Simple boolean flag that determines if this response represents
   * a successful operation or an error condition. Used by the UI
   * to determine how to display the response and what actions to enable.
   * 
   * Error Conditions:
   * - API authentication failures
   * - Network connectivity issues
   * - Invalid request parameters
   * - Server errors or timeouts
   * - Data not found scenarios
   * - Permission/authorization problems
   * 
   * Usage in UI:
   * if (response.hasError()) {
   *     showErrorIcon();
   *     disableFollowUpSuggestions();
   *     showRetryButton();
   * } else {
   *     showSuccessIcon();
   *     enableFollowUpSuggestions();
   *     showExportOptions();
   * }
   */
  private boolean hasError;
  
  /**
   * Detailed error message when an error occurs.
   * 
   * User-friendly explanation of what went wrong and, when possible,
   * what the user can do about it. Should avoid technical jargon
   * and provide actionable guidance.
   * 
   * Error Message Examples:
   * 
   * **Authentication Issues:**
   * "Your session has expired. Please log in again to continue accessing the user data."
   * 
   * **Permission Problems:**
   * "You don't have permission to view salary information. Contact your HR administrator for access."
   * 
   * **Data Not Found:**
   * "No employees found matching your criteria. Try adjusting your search terms or date range."
   * 
   * **Server Issues:**
   * "The user management system is temporarily unavailable. Please try again in a few minutes."
   * 
   * **Rate Limiting:**
   * "Too many requests in a short time. Please wait 60 seconds before trying again."
   * 
   * **Invalid Parameters:**
   * "The date format you entered isn't recognized. Please use YYYY-MM-DD format (e.g., 2024-03-15)."
   * 
   * Message Structure:
   * 1. What happened (brief explanation)
   * 2. Why it happened (when helpful and not technical)
   * 3. What to do next (actionable steps)
   * 
   * Tone Guidelines:
   * - Empathetic and helpful, not accusatory
   * - Clear and specific, not vague
   * - Solution-oriented when possible
   * - Appropriate urgency level
   */
  private String errorMessage;
  
  /**
   * Timestamp when this formatted response was generated.
   * 
   * Records when the AI Service completed processing the raw API response
   * into this user-friendly format. Useful for caching, logging, and
   * tracking response processing times.
   * 
   * Usage Examples:
   * - Cache invalidation: Determine if formatted response is stale
   * - Performance monitoring: Track formatting processing time
   * - Audit logging: Record when responses were generated
   * - User feedback: Show "as of" timestamps for data freshness
   */
  private LocalDateTime generatedAt;
  
  /**
   * Total execution time including API call and formatting.
   * 
   * Complete time from receiving the user's question to generating
   * this formatted response. Includes API request time, response
   * processing time, and AI formatting time.
   * 
   * Time Breakdown:
   * - Query parsing: 10-50ms
   * - API request: 100-3000ms (varies by API)
   * - Response processing: 50-200ms
   * - AI formatting: 100-500ms
   * - Total typical range: 300-4000ms
   * 
   * Performance Categories:
   * - Fast: < 1000ms (simple queries, cached data)
   * - Normal: 1000-3000ms (typical database queries)
   * - Slow: 3000-10000ms (complex reports, multiple API calls)
   * - Very Slow: > 10000ms (heavy processing, external integrations)
   * 
   * Usage: Display to users for transparency about response times.
   */
  private Long totalExecutionTimeMs;
  
  /**
   * Number of records returned in the API response.
   * 
   * Count of individual items/records found by the API query.
   * Helps users understand the scope of results and enables
   * better follow-up suggestions.
   * 
   * Count Examples:
   * - 0: No results found
   * - 1: Single record (show detailed view)
   * - 2-20: Small result set (show all details)
   * - 21-100: Medium result set (show summary + details)
   * - 100+: Large result set (show summary + pagination)
   * 
   * Usage in Response Generation:
   * - 0 records: Focus on alternative suggestions
   * - 1 record: Provide detailed information
   * - Multiple records: Summarize with key highlights
   * - Large sets: Offer filtering and export options
   */
  private Integer recordCount;
  
  /**
   * Confidence level of the AI's interpretation (0.0 to 1.0).
   * 
   * Indicates how confident the AI Service is that it correctly
   * understood the user's question and provided the right data.
   * Used to adjust response tone and suggest clarifications.
   * 
   * Confidence Levels:
   * - 0.9-1.0: Very confident - direct, assertive responses
   * - 0.7-0.9: Confident - normal responses with minor caveats
   * - 0.5-0.7: Moderate - include clarifying questions
   * - 0.3-0.5: Low - suggest alternative interpretations
   * - 0.0-0.3: Very low - ask for clarification
   * 
   * Example Usage:
   * if (confidence < 0.7) {
   *     addToSuggestions("Did you mean something else?");
   *     prefixResponse("I think you're looking for...");
   * }
   */
  private Double confidenceScore;
  
  /**
   * Additional metadata about the response.
   * 
   * Flexible field for storing extra information that might be
   * useful for specific use cases, debugging, or future enhancements.
   * 
   * Common Metadata:
   * - "api_endpoint": "/users/search"
   * - "query_type": "user_lookup"
   * - "data_source": "hr_database"
   * - "cache_hit": "true"
   * - "processing_model": "gpt-4"
   * - "response_language": "en"
   * - "user_department": "engineering"
   * 
   * Usage: Analytics, debugging, and feature enhancement.
   */
  private Map<String, Object> metadata;
  
  /**
   * Creates a successful FormattedResponse with basic information.
   * 
   * Convenience method for creating responses from successful operations.
   * Sets the timestamp automatically and marks as successful.
   * 
   * @param summary Brief summary of the results
   * @param detailedResponse Complete formatted response
   * @return A new FormattedResponse instance marked as successful
   * 
   * @see #error(String, String) for error responses
   * @see #builder() for full customization
   */
  public static FormattedResponse success(String summary, String detailedResponse) {
      return FormattedResponse.builder()
              .summary(summary)
              .detailedResponse(detailedResponse)
              .hasError(false)
              .generatedAt(LocalDateTime.now())
              .build();
  }
  
  /**
   * Creates an error FormattedResponse with summary and error details.
   * 
   * Convenience method for creating responses from failed operations.
   * Sets the timestamp automatically and marks as error.
   * 
   * @param summary Brief summary of what went wrong
   * @param errorMessage Detailed error explanation
   * @return A new FormattedResponse instance marked as error
   * 
   * @see #success(String, String) for successful responses
   * @see #builder() for full customization
   */
  public static FormattedResponse error(String summary, String errorMessage) {
      return FormattedResponse.builder()
              .summary(summary)
              .errorMessage(errorMessage)
              .hasError(true)
              .generatedAt(LocalDateTime.now())
              .build();
  }
  
  /**
   * Creates a "no results found" response with helpful suggestions.
   * 
   * Special case for when queries return no data but aren't errors.
   * Provides constructive suggestions for alternative searches.
   * 
   * @param query The original user query
   * @param suggestions Alternative search suggestions
   * @return A new FormattedResponse for empty results
   */
  public static FormattedResponse noResults(String query, List<String> suggestions) {
      return FormattedResponse.builder()
              .summary("No results found for your query")
              .detailedResponse("I couldn't find any data matching \"" + query + 
                              "\". This might be because:\n\n" +
                              "- The search criteria are too specific\n" +
                              "- The data doesn't exist in the system\n" +
                              "- There might be a typo in the search terms\n\n" +
                              "Try adjusting your search or ask me something else.")
              .suggestedFollowUps(suggestions)
              .hasError(false)
              .recordCount(0)
              .generatedAt(LocalDateTime.now())
              .build();
  }
  
  /**
   * Adds a follow-up suggestion to the existing list.
   * 
   * Convenience method for dynamically adding suggestions
   * during response processing.
   * 
   * @param suggestion The follow-up suggestion to add
   */
  public void addSuggestion(String suggestion) {
      if (suggestedFollowUps == null) {
          suggestedFollowUps = new java.util.ArrayList<>();
      }
      suggestedFollowUps.add(suggestion);
  }
  
  /**
   * Checks if this response contains any data.
   * 
   * @return true if there are results to display, false for empty responses
   */
  public boolean hasResults() {
      return !hasError && recordCount != null && recordCount > 0;
  }
  
  /**
   * Gets a display-friendly execution time string.
   * 
   * @return Formatted execution time (e.g., "1.2s", "450ms")
   */
  public String getFormattedExecutionTime() {
      if (totalExecutionTimeMs == null) {
          return "unknown";
      }
      
      if (totalExecutionTimeMs < 1000) {
          return totalExecutionTimeMs + "ms";
      } else {
          return String.format("%.1fs", totalExecutionTimeMs / 1000.0);
      }
  }
}
