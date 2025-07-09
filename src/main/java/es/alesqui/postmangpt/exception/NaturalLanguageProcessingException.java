package es.alesqui.postmangpt.exception;

/**
 * Custom exception for natural language processing failures. Provides specific
 * error types and detailed messaging for better debugging and error handling.
 */
public class NaturalLanguageProcessingException extends RuntimeException {

	/**
	 * Serial version UID for serialization compatibility. Change this value if you
	 * modify the class structure in incompatible ways.
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * The type of natural language processing error that occurred.
	 */
	public enum ErrorType {
		VALIDATION_FAILED("Request validation failed"), AI_MODEL_ERROR("AI model processing error"),
		PROMPT_GENERATION_ERROR("Prompt generation failed"), RESPONSE_PARSING_ERROR("AI response parsing failed"),
		TIMEOUT_ERROR("Processing timeout exceeded"), CONFIGURATION_ERROR("Configuration error"),
		ENDPOINT_ANALYSIS_ERROR("Endpoint analysis failed"), ASYNC_PROCESSING_ERROR("Asynchronous processing failed");

		private final String description;

		ErrorType(String description) {
			this.description = description;
		}

		public String getDescription() {
			return description;
		}
	}

	private final ErrorType errorType;

	// ================================================================================================
	// CONSTRUCTORS
	// ================================================================================================

	public NaturalLanguageProcessingException(String message) {
		super(message);
		this.errorType = ErrorType.AI_MODEL_ERROR;
	}

	public NaturalLanguageProcessingException(String message, Throwable cause) {
		super(message, cause);
		this.errorType = ErrorType.AI_MODEL_ERROR;
	}

	public NaturalLanguageProcessingException(ErrorType errorType, String message) {
		super(message);
		this.errorType = errorType;
	}

	public NaturalLanguageProcessingException(ErrorType errorType, String message, Throwable cause) {
		super(message, cause);
		this.errorType = errorType;
	}

	// ================================================================================================
	// FACTORY METHODS FOR SPECIFIC ERROR TYPES
	// ================================================================================================

	/**
	 * Creates an exception for validation failures.
	 * 
	 * @param message Specific validation error message
	 * @return New exception instance
	 */
	public static NaturalLanguageProcessingException validationFailed(String message) {
		return new NaturalLanguageProcessingException(ErrorType.VALIDATION_FAILED, "Validation failed: " + message);
	}

	/**
	 * Creates an exception for AI model processing errors.
	 * 
	 * @param message Error message from AI model
	 * @param cause   The underlying cause
	 * @return New exception instance
	 */
	public static NaturalLanguageProcessingException aiModelError(String message, Throwable cause) {
		return new NaturalLanguageProcessingException(ErrorType.AI_MODEL_ERROR, "AI model error: " + message, cause);
	}

	/**
	 * Creates an exception for prompt generation errors.
	 * 
	 * @param message Prompt generation error details
	 * @param cause   The underlying cause
	 * @return New exception instance
	 */
	public static NaturalLanguageProcessingException promptGenerationError(String message, Throwable cause) {
		return new NaturalLanguageProcessingException(ErrorType.PROMPT_GENERATION_ERROR,
				"Prompt generation failed: " + message, cause);
	}

	/**
	 * Creates an exception for response parsing errors.
	 * 
	 * @param responseLength Length of the response that failed to parse
	 * @param cause          The underlying parsing error
	 * @return New exception instance
	 */
	public static NaturalLanguageProcessingException responseParsingError(int responseLength, Throwable cause) {
		return new NaturalLanguageProcessingException(ErrorType.RESPONSE_PARSING_ERROR,
				"Failed to parse AI response (%d characters)".formatted(responseLength), cause);
	}

	/**
	 * Creates an exception for timeout errors.
	 * 
	 * @param timeoutMs The timeout that was exceeded
	 * @return New exception instance
	 */
	public static NaturalLanguageProcessingException timeoutError(long timeoutMs) {
		return new NaturalLanguageProcessingException(ErrorType.TIMEOUT_ERROR,
				"Processing timeout exceeded: %d ms".formatted(timeoutMs));
	}

	/**
	 * Creates an exception for configuration errors.
	 * 
	 * @param configProperty The configuration property that caused the error
	 * @param message        Error details
	 * @return New exception instance
	 */
	public static NaturalLanguageProcessingException configurationError(String configProperty, String message) {
		return new NaturalLanguageProcessingException(ErrorType.CONFIGURATION_ERROR,
				"Configuration error for '%s': %s".formatted(configProperty, message));
	}

	/**
	 * Creates an exception for endpoint analysis errors.
	 * 
	 * @param endpointCount Number of endpoints being analyzed
	 * @param cause         The underlying cause
	 * @return New exception instance
	 */
	public static NaturalLanguageProcessingException endpointAnalysisError(int endpointCount, Throwable cause) {
		return new NaturalLanguageProcessingException(ErrorType.ENDPOINT_ANALYSIS_ERROR,
				"Failed to analyze %d endpoints".formatted(endpointCount), cause);
	}

	/**
	 * Creates an exception for asynchronous processing errors.
	 * 
	 * @param message Error details
	 * @param cause   The underlying cause
	 * @return New exception instance
	 */
	public static NaturalLanguageProcessingException asyncProcessingError(String message, Throwable cause) {
		return new NaturalLanguageProcessingException(ErrorType.ASYNC_PROCESSING_ERROR,
				"Async processing failed: " + message, cause);
	}

	// ================================================================================================
	// GETTERS AND UTILITY METHODS
	// ================================================================================================

	/**
	 * Gets the specific type of error that occurred.
	 * 
	 * @return The error type
	 */
	public ErrorType getErrorType() {
		return errorType;
	}

	/**
	 * Gets a user-friendly description of the error type.
	 * 
	 * @return Error type description
	 */
	public String getErrorDescription() {
		return errorType.getDescription();
	}

	/**
	 * Checks if this exception represents a validation error.
	 * 
	 * @return true if this is a validation error
	 */
	public boolean isValidationError() {
		return errorType == ErrorType.VALIDATION_FAILED;
	}

	/**
	 * Checks if this exception represents an AI model error.
	 * 
	 * @return true if this is an AI model error
	 */
	public boolean isAiModelError() {
		return errorType == ErrorType.AI_MODEL_ERROR;
	}

	/**
	 * Checks if this exception represents a timeout error.
	 * 
	 * @return true if this is a timeout error
	 */
	public boolean isTimeoutError() {
		return errorType == ErrorType.TIMEOUT_ERROR;
	}

	/**
	 * Checks if this exception represents a configuration error.
	 * 
	 * @return true if this is a configuration error
	 */
	public boolean isConfigurationError() {
		return errorType == ErrorType.CONFIGURATION_ERROR;
	}

	/**
	 * Checks if this exception is retryable (temporary errors that might succeed on
	 * retry).
	 * 
	 * @return true if the operation should be retried
	 */
	public boolean isRetryable() {
		return errorType == ErrorType.AI_MODEL_ERROR || errorType == ErrorType.TIMEOUT_ERROR
				|| errorType == ErrorType.ENDPOINT_ANALYSIS_ERROR;
	}

	/**
	 * Gets a detailed error summary including type and message.
	 * 
	 * @return Formatted error summary
	 */
	public String getDetailedMessage() {
		return "%s: %s".formatted(errorType.getDescription(), getMessage());
	}
}