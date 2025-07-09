package es.alesqui.postmangpt.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.function.Function;

/**
 * Utility class for safe logging operations that won't interrupt reactive
 * streams.
 * 
 * This class provides defensive logging methods that handle potential null
 * values, logging framework errors, and other edge cases to ensure that logging
 * issues never affect the main reactive stream processing.
 * 
 * Enhanced with HTTP-specific logging methods for REST API operations.
 */
@Slf4j
@Component
public class SafeLogger {

	// =============================================================================
	// CORE SAFE LOGGING INFRASTRUCTURE
	// =============================================================================

	/**
	 * Executes logging operation safely, preventing any logging failures from
	 * affecting the main application flow.
	 * 
	 * @param loggingOperation The logging operation to execute safely
	 */
	private void logSafely(Runnable loggingOperation) {
		try {
			loggingOperation.run();
		} catch (Exception e) {
			// Fallback to System.err if logging framework fails
			System.err.println("Logging operation failed: " + e.getMessage());
		}
	}

	/**
	 * Executes logging operation safely and returns empty Mono for reactive
	 * chaining.
	 * 
	 * @param loggingOperation The logging operation to execute safely
	 * @return Empty Mono for reactive chaining
	 */
	private Mono<Void> logSafelyReactive(Runnable loggingOperation) {
		logSafely(loggingOperation);
		return Mono.empty();
	}

	// =============================================================================
	// GENERIC DOCUMENT LOGGING WITH EXTRACTORS
	// =============================================================================

	/**
	 * Logs document information using custom extractors for maximum flexibility.
	 * 
	 * This generic method allows logging of any document type by providing custom
	 * extraction functions for name, ID, and team fields. It handles null safety
	 * and provides meaningful fallbacks for missing data.
	 * 
	 * @param <T>           The type of document being logged
	 * @param operation     The operation being performed (e.g., "Retrieved by ID")
	 * @param document      The document instance to log (can be null)
	 * @param nameExtractor Function to extract name from document
	 * @param idExtractor   Function to extract ID from document
	 * @param teamExtractor Function to extract team from document
	 */
	public <T> void logDocumentInfo(String operation, T document, Function<T, String> nameExtractor,
			Function<T, String> idExtractor, Function<T, String> teamExtractor) {
		if (document == null) {
			logSafely(() -> log.debug("{}: Document is null", operation));
			return;
		}

		String name = safeExtract(document, nameExtractor, "Unnamed Document");
		String id = safeExtract(document, idExtractor, "No ID");
		String team = safeExtract(document, teamExtractor, "No team");

		logSafely(() -> log.debug("{}: {} (Team: {}, ID: {})", operation, name, team, id));
	}

	/**
	 * Safely extracts value using provided function with comprehensive error
	 * handling.
	 * 
	 * This method provides defense against: - Null pointer exceptions from
	 * extractor functions - Runtime exceptions during extraction - Null or empty
	 * extracted values
	 * 
	 * @param <T>       The type of object being processed
	 * @param object    The object to extract from
	 * @param extractor The extraction function
	 * @param fallback  The fallback value if extraction fails
	 * @return The extracted value or fallback
	 */
	private <T> String safeExtract(T object, Function<T, String> extractor, String fallback) {
		try {
			if (extractor == null) {
				return fallback;
			}

			String result = extractor.apply(object);
			return (result != null && !result.trim().isEmpty()) ? result.trim() : fallback;
		} catch (Exception e) {
			// Log extraction failure for debugging but don't fail the operation
			logSafely(() -> log.trace("Failed to extract value: {}", e.getMessage()));
			return fallback;
		}
	}

	// =============================================================================
	// CONVENIENCE METHODS FOR SPECIFIC DOCUMENT TYPES
	// =============================================================================

	/**
	 * Convenience method for logging PostmanDocument with standard
	 * extractors.
	 */
	public void logPostmanDocumentInfo(String operation, Object document) {
		// Using reflection-safe approach to handle PostmanDocument
		logDocumentInfo(operation, document, doc -> getFieldSafely(doc, "getName"), doc -> getFieldSafely(doc, "getId"),
				doc -> getFieldSafely(doc, "getTeam"));
	}

	/**
	 * Convenience method for logging SwaggerDocument with standard extractors.
	 */
	public void logSwaggerDocumentInfo(String operation, Object document) {
		// Using reflection-safe approach to handle SwaggerDocument
		logDocumentInfo(operation, document, doc -> getFieldSafely(doc, "getName"), doc -> getFieldSafely(doc, "getId"),
				doc -> getFieldSafely(doc, "getTeam"));
	}

	// =============================================================================
	// REFLECTION-SAFE HELPER METHODS
	// =============================================================================

	/**
	 * Safely extracts field value using method name with reflection fallback.
	 */
	private String getFieldSafely(Object object, String methodName) {
		if (object == null)
			return null;

		try {
			// Try to call the method
			var method = object.getClass().getMethod(methodName);
			Object result = method.invoke(object);
			return result != null ? result.toString() : null;
		} catch (Exception e) {
			// Fallback for any reflection errors
			return null;
		}
	}

	// =============================================================================
	// OPERATION LOGGING METHODS
	// =============================================================================

	/**
	 * Logs operation start with identifier.
	 */
	public void logOperationStart(String operation, String identifier) {
		logSafely(() -> log.debug("Starting {}: {}", operation, identifier));
	}

	/**
	 * Logs operation start with multiple parameters.
	 */
	public void logOperationStart(String operation, Object... params) {
		logSafely(() -> {
			if (params == null || params.length == 0) {
				log.debug("Starting {}", operation);
			} else {
				log.debug("Starting {} with params: {}", operation, java.util.Arrays.toString(params));
			}
		});
	}

	/**
	 * Reactive version of operation start logging with multiple parameters.
	 */
	public Mono<Void> logOperationStartReactive(String operation, Object... params) {
		return logSafelyReactive(() -> {
			if (params == null || params.length == 0) {
				log.debug("Starting {}", operation);
			} else {
				log.debug("Starting {} with params: {}", operation, java.util.Arrays.toString(params));
			}
		});
	}

	/**
	 * Logs operation success.
	 */
	public void logOperationSuccess(String operation, String message) {
		logSafely(() -> log.debug("{} completed: {}", operation, message));
	}

	/**
	 * Logs operation error.
	 */
	public void logOperationError(String operation, Throwable error) {
		logSafely(() -> log.error("{} failed: {}", operation, error.getMessage(), error));
	}

	/**
	 * Safely logs HTTP request information.
	 */
	public void logHttpRequest(String method, String path, String description) {
		try {
			log.info("HTTP {} {} - {}", method, path, description);
		} catch (Exception e) {
			log.warn("Failed to log HTTP request: {} {}", method, path, e);
		}
	}

	/**
	 * Safely logs HTTP response information.
	 */
	public void logHttpResponse(String method, String path, String status, String description) {
		try {
			log.info("HTTP {} {} -> {} - {}", method, path, status, description);
		} catch (Exception e) {
			log.warn("Failed to log HTTP response: {} {}", method, path, e);
		}
	}

	/**
	 * Safely logs HTTP error responses.
	 */
	public void logHttpError(String method, String path, Throwable error) {
		try {
			String errorMessage = Optional.ofNullable(error).map(Throwable::getMessage).orElse("Unknown error");

			log.error("HTTP {} {} - Error: {}", method, path, errorMessage);

			// Log error type for debugging
			if (error != null) {
				log.debug("HTTP {} {} - Error type: {}", method, path, error.getClass().getSimpleName());
			}
		} catch (Exception e) {
			log.warn("Failed to log HTTP error: {} {}", method, path, e);
		}
	}

	/**
	 * Safely logs file operation information.
	 */
	public void logFileOperation(String operation, MultipartFile file) {
		try {
			String filename = extractFilename(file);
			long fileSize = extractFileSize(file);

			log.info("{} - File: {} (size: {} bytes)", operation, filename, fileSize);

			if (file == null) {
				log.debug("{} initiated with null file - will result in validation error", operation);
			}
		} catch (Exception e) {
			log.warn("Failed to log file operation for: {}", operation, e);
		}
	}

	/**
	 * Safely logs JSON operation information.
	 */
	public void logJsonOperation(String operation, String jsonContent) {
		try {
			int contentSize = Optional.ofNullable(jsonContent).map(String::length).orElse(0);

			boolean isValid = jsonContent != null && !jsonContent.trim().isEmpty();

			log.info("{} - JSON (size: {} characters, valid: {})", operation, contentSize, isValid);

			if (jsonContent == null) {
				log.debug("{} initiated with null JSON content - will result in validation error", operation);
			} else if (jsonContent.trim().isEmpty()) {
				log.debug("{} initiated with empty JSON content - will result in validation error", operation);
			}
		} catch (Exception e) {
			log.warn("Failed to log JSON operation for: {}", operation, e);
		}
	}

	/**
	 * Safely logs lookup results (found/not found scenarios).
	 */
	public void logLookupResult(String operation, String identifier, Object result) {
		try {
			if (result != null) {
				log.debug("{} successful for identifier: {}", operation, identifier);
			} else {
				log.debug("{} - No result found for identifier: {}", operation, identifier);
			}
		} catch (Exception e) {
			log.warn("Failed to log lookup result for operation: {}", operation, e);
		}
	}

	/**
	 * Safely logs duplicate found scenarios.
	 */
	public void logDuplicateFound(String itemType, String identifier) {
		try {
			log.warn("Duplicate {} found with identifier: {}", itemType, identifier);
		} catch (Exception e) {
			log.warn("Failed to log duplicate found for: {}", itemType, e);
		}
	}

	// Private helper methods

	private String extractFilename(MultipartFile file) {
		return Optional.ofNullable(file).map(MultipartFile::getOriginalFilename)
				.filter(name -> name != null && !name.trim().isEmpty()).orElse("unknown-file");
	}

	private long extractFileSize(MultipartFile file) {
		return Optional.ofNullable(file).map(MultipartFile::getSize).orElse(0L);
	}

	
}