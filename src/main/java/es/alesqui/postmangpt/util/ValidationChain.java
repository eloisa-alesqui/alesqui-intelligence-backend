package es.alesqui.postmangpt.util;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import es.alesqui.postmangpt.model.Collection;

/**
 * Fluent validation utility for chaining multiple validation rules. Provides a
 * builder pattern for composing and executing validation chains.
 */
@Component
public class ValidationChain {

	/**
	 * Creates a new validation chain builder.
	 */
	public static ValidationStep start() {
		return new ValidationStep();
	}

	/**
	 * Builder for creating and executing validation chains.
	 */
	public static class ValidationStep {
		// List of validation rules to execute
		private final List<Runnable> validations = new ArrayList<>();

		/**
		 * Validates that the given object is not null.
		 * Adds this validation rule to the chain for execution later.
		 *
		 * @param obj the object to validate
		 * @param message the error message to throw if the validation fails
		 * @return the current `ValidationStep` instance for method chaining
		 * @throws IllegalArgumentException if the object is null
		 */
		public ValidationStep validateNotNull(Object obj, String message) {
		    validations.add(() -> {
		        if (obj == null)
		            throw new IllegalArgumentException(message);
		    });
		    return this;
		}

		/**
		 * Validates that the given string is not null, empty, or only whitespace.
		 * Adds this validation rule to the chain for execution later.
		 *
		 * @param str the string to validate
		 * @param message the error message to throw if the validation fails
		 * @return the current `ValidationStep` instance for method chaining
		 * @throws IllegalArgumentException if the string is null, empty, or contains only whitespace
		 */
		public ValidationStep validateNotEmpty(String str, String message) {
		    validations.add(() -> {
		        if (str == null || str.trim().isEmpty()) {
		            throw new IllegalArgumentException(message);
		        }
		    });
		    return this;
		}

		/**
		 * Validates that the given file is not empty.
		 * Adds this validation rule to the chain for execution later.
		 *
		 * @param file the file to validate
		 * @param message the error message to throw if the validation fails
		 * @return the current `ValidationStep` instance for method chaining
		 * @throws IllegalArgumentException if the file is empty
		 */
		public ValidationStep validateFileNotEmpty(MultipartFile file, String message) {
		    validations.add(() -> {
		        if (file.isEmpty()) {
		            throw new IllegalArgumentException(message);
		        }
		    });
		    return this;
		}

		/**
		 * Validates that the size of the given file does not exceed the specified maximum size.
		 * Adds this validation rule to the chain for execution later.
		 *
		 * @param file the file to validate
		 * @param maxSize the maximum allowed size in bytes
		 * @return the current `ValidationStep` instance for method chaining
		 * @throws IllegalArgumentException if the file size exceeds the maximum size
		 */
		public ValidationStep validateFileSize(MultipartFile file, long maxSize) {
		    validations.add(() -> {
		        if (file.getSize() > maxSize) {
		            throw new IllegalArgumentException("File too large (max " + (maxSize / 1024 / 1024) + "MB)");
		        }
		    });
		    return this;
		}

		/**
		 * Validates that the size of the given JSON string does not exceed the specified maximum size.
		 * Adds this validation rule to the chain for execution later.
		 *
		 * @param json the JSON string to validate
		 * @param maxSize the maximum allowed size in bytes
		 * @return the current `ValidationStep` instance for method chaining
		 * @throws IllegalArgumentException if the JSON string size exceeds the maximum size
		 */
		public ValidationStep validateJsonSize(String json, long maxSize) {
		    validations.add(() -> {
		        if (json.length() > maxSize) {
		            throw new IllegalArgumentException("JSON content too large: " + json.length() + " bytes");
		        }
		    });
		    return this;
		}

		/**
		 * Validates that the given file has the specified file extension.
		 * Adds this validation rule to the chain for execution later.
		 *
		 * @param file the file to validate
		 * @param extension the required file extension (e.g., `.json`, `.txt`)
		 * @return the current `ValidationStep` instance for method chaining
		 * @throws IllegalArgumentException if the file does not have the specified extension
		 */
		public ValidationStep validateFileExtension(MultipartFile file, String extension) {
		    validations.add(() -> {
		        String filename = file.getOriginalFilename();
		        if (filename == null || !filename.endsWith(extension)) {
		            throw new IllegalArgumentException("File must be a valid " + extension.toUpperCase());
		        }
		    });
		    return this;
		}

		/**
		 * Validates that the given Postman `Collection` object has a valid structure.
		 * Specifically, it checks that the `item` field is not null or empty.
		 * Adds this validation rule to the chain for execution later.
		 *
		 * @param collection the `Collection` object to validate
		 * @return the current `ValidationStep` instance for method chaining
		 * @throws IllegalArgumentException if the `item` field is null or empty
		 */
		public ValidationStep validateCollectionStructure(Collection collection) {
		    validations.add(() -> {
		        if (collection.getItem() == null || collection.getItem().isEmpty()) {
		            throw new IllegalArgumentException("Collection must have at least one item/request");
		        }
		    });
		    return this;
		}
		
		/**
		 * Validates that the `info` field of the given `Collection` object is not null.
		 * Adds this validation rule to the chain for execution later.
		 *
		 * @param collection the `Collection` object to validate
		 * @param message the error message to throw if the validation fails
		 * @return the current `ValidationStep` instance for method chaining
		 * @throws IllegalArgumentException if the `info` field is null
		 */
		public ValidationStep validateCollectionInfo(Collection collection, String message) {
		    validations.add(() -> {
		        if (collection.getInfo() == null) {
		            throw new IllegalArgumentException(message);
		        }
		    });
		    return this;
		}

		/**
		 * Validates that the `name` field of the `info` object in the given `Collection` 
		 * is not null, empty, or only whitespace. Adds this validation rule to the chain 
		 * for execution later.
		 *
		 * @param collection the `Collection` object to validate
		 * @param message the error message to throw if the validation fails
		 * @return the current `ValidationStep` instance for method chaining
		 * @throws IllegalArgumentException if the `info` field is null, or if the `name` 
		 *         field is null, empty, or only whitespace
		 */
		public ValidationStep validateCollectionName(Collection collection, String message) {
		    validations.add(() -> {
		        if (collection.getInfo() == null || 
		            collection.getInfo().getName() == null || 
		            collection.getInfo().getName().trim().isEmpty()) {
		            throw new IllegalArgumentException(message);
		        }
		    });
		    return this;
		}

		/**
		 * Validates that the `item` field of the given `Collection` object is not null 
		 * or empty. Adds this validation rule to the chain for execution later.
		 *
		 * @param collection the `Collection` object to validate
		 * @param message the error message to throw if the validation fails
		 * @return the current `ValidationStep` instance for method chaining
		 * @throws IllegalArgumentException if the `item` field is null or empty
		 */
		public ValidationStep validateCollectionItems(Collection collection, String message) {
		    validations.add(() -> {
		        if (collection.getItem() == null || collection.getItem().isEmpty()) {
		            throw new IllegalArgumentException(message);
		        }
		    });
		    return this;
		}

		/**
		 * Executes all validation rules in order. Throws on first failure.
		 */
		public void execute() {
			validations.forEach(Runnable::run);
		}
	}
}
