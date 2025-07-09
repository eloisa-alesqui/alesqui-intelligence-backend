package es.alesqui.postmangpt.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Query validation result that analyzes user input for completeness and clarity.
 * 
 * This class represents the outcome of validating a user's natural language query
 * before attempting to convert it into an API request. It helps ensure that queries
 * are actionable, complete, and can be successfully processed by PostmanGPT's
 * AI Service.
 * 
 * Usage Example:
 * QueryValidation validation = QueryValidation.builder()
 *     .valid(false)
 *     .reason("Missing required parameter: user ID or email")
 *     .suggestions(List.of("Try: 'Show me user john@company.com'", "Or: 'Show me user ID 123'"))
 *     .confidence(0.85)
 *     .validationType(ValidationType.PARAMETER_MISSING)
 *     .build();
 * 
 * Flow Context:
 * 1. User enters: "Show me the user"
 * 2. AI Service creates QueryValidation to check completeness
 * 3. Validation fails - too vague, missing user identifier
 * 4. System prompts user for clarification with suggestions
 * 5. User refines: "Show me user john@company.com"
 * 6. Validation passes, proceeds to API request generation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryValidation {
  
  /**
   * Indicates whether the query is valid and actionable.
   * 
   * A query is considered valid when it contains enough information
   * to be converted into a specific API request without ambiguity.
   * Invalid queries require user clarification before processing.
   * 
   * Valid Query Examples:
   * - "Show me user john@company.com"
   * - "List all active employees in Engineering"
   * - "Create a new user with email jane@company.com"
   * - "Update user 123 with department Marketing"
   * - "Delete order #12345"
   * 
   * Invalid Query Examples:
   * - "Show me the user" (which user?)
   * - "List employees" (all employees? active only? which department?)
   * - "Create user" (missing required details)
   * - "Update something" (too vague)
   * - "Delete it" (delete what?)
   * 
   * Validation Criteria:
   * - Specific action is identifiable (GET, POST, PUT, DELETE)
   * - Required parameters are present or can be reasonably inferred
   * - Query scope is appropriately defined
   * - No conflicting or contradictory instructions
   * - Target resource/endpoint can be determined
   * 
   * Usage in Flow:
   * if (validation.isValid()) {
   *     proceedWithAPIRequest();
   * } else {
   *     promptUserForClarification(validation.getSuggestions());
   * }
   */
  private boolean valid;
  
  /**
   * Detailed explanation of why the query is valid or invalid.
   * 
   * Provides specific feedback about what makes the query actionable
   * or what issues prevent it from being processed. This helps users
   * understand the validation decision and guides them toward better queries.
   * 
   * Valid Reasons Examples:
   * - "Query is complete with all required parameters for user lookup"
   * - "Clear action (list users) with specific filter (Engineering department)"
   * - "Well-formed create request with all mandatory fields provided"
   * - "Update operation clearly specifies target user and fields to change"
   * 
   * Invalid Reasons Examples:
   * 
   * **Missing Parameters:**
   * - "Missing required parameter: user ID or email address"
   * - "Department name not specified for employee search"
   * - "Date range required for report generation"
   * - "Order ID missing for order lookup"
   * 
   * **Ambiguous Requests:**
   * - "Multiple possible interpretations: 'user' could mean employee, customer, or admin"
   * - "Unclear scope: 'all employees' might return too many results"
   * - "Ambiguous action: unclear whether to create, update, or retrieve"
   * 
   * **Incomplete Information:**
   * - "Create user request missing required fields: name, email, department"
   * - "Update operation doesn't specify which fields to change"
   * - "Search criteria too broad - please narrow down the scope"
   * 
   * **Technical Issues:**
   * - "Requested endpoint not available in current API configuration"
   * - "Query requires permissions not available to current user"
   * - "Date format not recognized - please use YYYY-MM-DD"
   * 
   * **Context Problems:**
   * - "Previous conversation context needed but not available"
   * - "Reference to 'that user' unclear without prior context"
   * - "Pronoun 'it' refers to unknown object"
   * 
   * Reason Structure:
   * 1. Primary issue identification
   * 2. Specific missing or problematic elements
   * 3. Impact on processing ability
   * 
   * Tone: Clear, helpful, and educational rather than critical.
   */
  private String reason;
  
  /**
   * List of specific suggestions to improve invalid queries.
   * 
   * Actionable recommendations that help users refine their queries
   * to make them valid and processable. Suggestions should be specific,
   * practical, and directly address the identified validation issues.
   * 
   * Suggestion Categories:
   * 
   * **Parameter Completion:**
   * - "Try: 'Show me user john@company.com'"
   * - "Specify department: 'List Engineering employees'"
   * - "Add date range: 'Show orders from 2024-01-01 to 2024-01-31'"
   * - "Include user ID: 'Update user 123 with new department'"
   * 
   * **Scope Clarification:**
   * - "Be more specific: 'Show active employees' or 'Show all employees'"
   * - "Narrow the search: 'List recent orders' or 'List orders over $1000'"
   * - "Specify time period: 'This month's sales' or 'Last quarter's reports'"
   * 
   * **Action Clarification:**
   * - "Specify action: 'Create new user' or 'Find existing user'"
   * - "Be explicit: 'Delete user 123' instead of 'Remove that user'"
   * - "Choose operation: 'Update user email' or 'Replace user record'"
   * 
   * **Format Corrections:**
   * - "Use email format: user@company.com"
   * - "Try date format: YYYY-MM-DD (e.g., 2024-03-15)"
   * - "Use numeric ID: 123 instead of 'one-two-three'"
   * 
   * **Alternative Approaches:**
   * - "Or try: 'Show me the last 10 users'"
   * - "Alternative: 'List users by department'"
   * - "You could also ask: 'What user information is available?'"
   * 
   * **Context Building:**
   * - "First tell me: 'What departments exist?'"
   * - "Start with: 'Show me user types available'"
   * - "Try exploring: 'What can I search for?'"
   * 
   * Suggestion Quality Guidelines:
   * - Provide 2-5 concrete examples
   * - Use exact query formats the user can copy
   * - Address the specific validation failure
   * - Offer multiple approaches when possible
   * - Include both simple and advanced alternatives
   * 
   * Example for missing user identifier:
   * suggestions = List.of(
   *     "Try: 'Show me user john@company.com'",
   *     "Or: 'Show me user ID 123'",
   *     "You can also ask: 'List all users in Engineering'",
   *     "For recent users: 'Show users created this month'"
   * );
   */
  private List<String> suggestions;
  
  /**
   * Confidence level in the validation decision (0.0 to 1.0).
   * 
   * Indicates how certain the AI Service is about its validation assessment.
   * Higher confidence means the validation decision is more reliable,
   * while lower confidence suggests the query might be borderline or
   * require human review.
   * 
   * Confidence Levels:
   * 
   * **Very High (0.9-1.0):**
   * - Clear, unambiguous queries with all parameters
   * - Obviously invalid queries missing critical information
   * - Well-formed requests matching known patterns
   * - Explicit validation failures (wrong format, missing required fields)
   * 
   * **High (0.8-0.9):**
   * - Mostly clear queries with minor ambiguities
   * - Standard patterns with slight variations
   * - Common validation scenarios
   * - Familiar query structures
   * 
   * **Medium (0.6-0.8):**
   * - Queries with some ambiguity but reasonable interpretation possible
   * - Context-dependent validation decisions
   * - Borderline cases between valid/invalid
   * - Novel query patterns
   * 
   * **Low (0.4-0.6):**
   * - Highly ambiguous queries
   * - Multiple possible interpretations
   * - Insufficient context for confident decision
   * - Edge cases not well covered by training
   * 
   * **Very Low (0.0-0.4):**
   * - Completely unclear or nonsensical queries
   * - System uncertainty about validation criteria
   * - Potential parsing errors
   * - Requires human intervention
   * 
   * Usage Examples:
   * 
   * // High confidence - proceed normally
   * if (validation.getConfidence() > 0.8) {
   *     processValidation(validation);
   * }
   * 
   * // Medium confidence - add caveats
   * else if (validation.getConfidence() > 0.6) {
   *     addUncertaintyNote("I think this is what you mean...");
   *     processValidation(validation);
   * }
   * 
   * // Low confidence - ask for clarification
   * else {
   *     requestClarification("I'm not sure I understand...");
   * }
   * 
   * Confidence Factors:
   * - Query clarity and specificity
   * - Presence of required parameters
   * - Match to known patterns
   * - Context availability
   * - Potential for misinterpretation
   */
  private double confidence;
  
  /**
   * Timestamp when the validation was performed.
   * 
   * Records when this validation analysis was completed.
   * Useful for caching validation results, performance monitoring,
   * and debugging validation issues.
   * 
   * Usage Examples:
   * - Cache validation results for identical queries
   * - Track validation processing time
   * - Audit trail for validation decisions
   * - Performance analysis of validation logic
   */
  private LocalDateTime validatedAt;
  
  /**
   * Type of validation performed.
   * 
   * Categorizes the validation approach used, which helps in
   * understanding the validation logic and improving the system.
   * 
   * Validation Types:
   * - SYNTAX: Grammar and structure validation
   * - SEMANTIC: Meaning and intent validation
   * - PARAMETER: Required parameter checking
   * - PERMISSION: Access rights validation
   * - CONTEXT: Context dependency validation
   * - FORMAT: Data format validation
   */
  private ValidationType validationType;
  
  /**
   * Specific validation rules that were applied.
   * 
   * List of validation rules that were checked during the validation process.
   * Helps with debugging and improving validation logic.
   * 
   * Example Rules:
   * - "required_user_identifier"
   * - "valid_email_format"
   * - "department_exists"
   * - "date_range_reasonable"
   * - "action_verb_present"
   */
  private List<String> appliedRules;
  
  /**
   * Additional context that influenced the validation decision.
   * 
   * Information about the validation environment, user context,
   * and other factors that affected the validation outcome.
   * 
   * Context Examples:
   * - User's previous queries
   * - Available API endpoints
   * - User permissions
   * - System configuration
   * - Session state
   */
  private Map<String, Object> validationContext;
  
  /**
   * Severity level of validation issues found.
   * 
   * Indicates how critical the validation problems are:
   * - ERROR: Cannot proceed without fixing
   * - WARNING: Can proceed but might not get expected results
   * - INFO: Suggestions for improvement
   */
  private ValidationSeverity severity;
  
  /**
   * Creates a valid QueryValidation with high confidence.
   * 
   * Convenience method for queries that pass all validation checks.
   * 
   * @param reason Explanation of why the query is valid
   * @return A new QueryValidation instance marked as valid
   */
  public static QueryValidation valid(String reason) {
      return QueryValidation.builder()
              .valid(true)
              .reason(reason)
              .confidence(0.9)
              .severity(ValidationSeverity.INFO)
              .validatedAt(LocalDateTime.now())
              .build();
  }
  
  /**
   * Creates an invalid QueryValidation with suggestions.
   * 
   * Convenience method for queries that fail validation checks.
   * 
   * @param reason Explanation of why the query is invalid
   * @param suggestions List of suggestions to fix the query
   * @return A new QueryValidation instance marked as invalid
   */
  public static QueryValidation invalid(String reason, List<String> suggestions) {
      return QueryValidation.builder()
              .valid(false)
              .reason(reason)
              .suggestions(suggestions)
              .confidence(0.8)
              .severity(ValidationSeverity.ERROR)
              .validatedAt(LocalDateTime.now())
              .build();
  }
  
  /**
   * Creates a QueryValidation with warning level issues.
   * 
   * For queries that can be processed but might not produce
   * the expected results due to ambiguity or missing context.
   * 
   * @param reason Explanation of the potential issues
   * @param suggestions Optional suggestions for improvement
   * @return A new QueryValidation instance with warnings
   */
  public static QueryValidation warning(String reason, List<String> suggestions) {
      return QueryValidation.builder()
              .valid(true)
              .reason(reason)
              .suggestions(suggestions)
              .confidence(0.6)
              .severity(ValidationSeverity.WARNING)
              .validatedAt(LocalDateTime.now())
              .build();
  }
  
  /**
   * Adds a suggestion to the existing list.
   * 
   * Convenience method for dynamically building suggestion lists
   * during validation processing.
   * 
   * @param suggestion The suggestion to add
   */
  public void addSuggestion(String suggestion) {
      if (suggestions == null) {
          suggestions = new java.util.ArrayList<>();
      }
      suggestions.add(suggestion);
  }
  
  /**
   * Checks if the validation has any suggestions.
   * 
   * @return true if suggestions are available, false otherwise
   */
  public boolean hasSuggestions() {
      return suggestions != null && !suggestions.isEmpty();
  }
  
  /**
   * Checks if this is a high-confidence validation result.
   * 
   * @return true if confidence is above 0.8
   */
  public boolean isHighConfidence() {
      return confidence >= 0.8;
  }
  
  /**
   * Gets a user-friendly confidence description.
   * 
   * @return Human-readable confidence level
   */
  public String getConfidenceDescription() {
      if (confidence >= 0.9) return "Very confident";
      if (confidence >= 0.8) return "Confident";
      if (confidence >= 0.6) return "Moderately confident";
      if (confidence >= 0.4) return "Somewhat uncertain";
      return "Very uncertain";
  }
  
  /**
   * Enumeration of validation types.
   */
  public enum ValidationType {
      SYNTAX,      // Grammar and structure
      SEMANTIC,    // Meaning and intent
      PARAMETER,   // Required parameters
      PERMISSION,  // Access rights
      CONTEXT,     // Context dependency
      FORMAT       // Data format
  }
  
  /**
   * Enumeration of validation severity levels.
   */
  public enum ValidationSeverity {
      ERROR,    // Cannot proceed
      WARNING,  // Can proceed with caveats
      INFO      // Informational only
  }
}
