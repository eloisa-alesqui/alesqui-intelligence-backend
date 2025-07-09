package es.alesqui.postmangpt.exception;

/**
 * Custom exception for prompt generation failures. Provides specific error
 * types and detailed messaging for better debugging.
 */
public class PromptGenerationException extends RuntimeException {

	/**
     * Serial version UID for serialization compatibility.
     * Change this value if you modify the class structure in incompatible ways.
     */
	private static final long serialVersionUID = 1L;

	/**
	 * The type of prompt generation error that occurred.
	 */
	public enum ErrorType {
		VALIDATION_FAILED("Prompt validation failed"), LENGTH_EXCEEDED("Prompt length exceeded maximum allowed"),
		INSUFFICIENT_LENGTH("Prompt length below minimum required"), TEMPLATE_PROCESSING("Template processing failed"),
		ENDPOINT_FORMATTING("Endpoint formatting failed");

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

	public PromptGenerationException(String message) {
		super(message);
		this.errorType = ErrorType.VALIDATION_FAILED;
	}

	public PromptGenerationException(String message, Throwable cause) {
		super(message, cause);
		this.errorType = ErrorType.TEMPLATE_PROCESSING;
	}

	public PromptGenerationException(ErrorType errorType, String message) {
		super(message);
		this.errorType = errorType;
	}

	public PromptGenerationException(ErrorType errorType, String message, Throwable cause) {
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
	public static PromptGenerationException validationFailed(String message) {
		return new PromptGenerationException(ErrorType.VALIDATION_FAILED, "Validation failed: " + message);
	}

	/**
	 * Creates an exception for length exceeded errors.
	 * 
	 * @param actualLength The actual length that exceeded the limit
	 * @param maxLength    The maximum allowed length
	 * @return New exception instance
	 */
	public static PromptGenerationException lengthExceeded(int actualLength, int maxLength) {
		return new PromptGenerationException(ErrorType.LENGTH_EXCEEDED,
				"Length exceeded: %d characters (max: %d)".formatted(actualLength, maxLength));
	}

	/**
	 * Creates an exception for insufficient length errors.
	 * 
	 * @param actualLength The actual length that was too short
	 * @param minLength    The minimum required length
	 * @return New exception instance
	 */
	public static PromptGenerationException insufficientLength(int actualLength, int minLength) {
		return new PromptGenerationException(ErrorType.INSUFFICIENT_LENGTH,
				"Insufficient length: %d characters (min: %d)".formatted(actualLength, minLength));
	}

	/**
	 * Creates an exception for template processing errors.
	 * 
	 * @param templateName The name of the template that failed
	 * @param cause        The underlying cause
	 * @return New exception instance
	 */
	public static PromptGenerationException templateFailed(String templateName, Throwable cause) {
		return new PromptGenerationException(ErrorType.TEMPLATE_PROCESSING,
				"Template processing failed: " + templateName, cause);
	}

	/**
	 * Creates an exception for endpoint formatting errors.
	 * 
	 * @param endpointInfo Information about the problematic endpoint
	 * @param cause        The underlying cause
	 * @return New exception instance
	 */
	public static PromptGenerationException endpointFormatFailed(String endpointInfo, Throwable cause) {
		return new PromptGenerationException(ErrorType.ENDPOINT_FORMATTING,
				"Endpoint formatting failed: " + endpointInfo, cause);
	}

	// ================================================================================================
	// GETTERS
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
	 * Checks if this exception represents a length-related error.
	 * 
	 * @return true if this is a length error
	 */
	public boolean isLengthError() {
		return errorType == ErrorType.LENGTH_EXCEEDED || errorType == ErrorType.INSUFFICIENT_LENGTH;
	}
}