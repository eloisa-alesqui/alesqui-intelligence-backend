package es.alesqui.postmangpt.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import es.alesqui.postmangpt.model.postman.Collection;
import reactor.core.publisher.Mono;

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
        // List of reactive validation rules to execute
        private final List<Mono<Void>> validations = new ArrayList<>();

        /**
         * Validates that the given object is not null.
         *
         * @param obj     the object to validate
         * @param message the error message to throw if the validation fails
         * @return the current `ValidationStep` instance for method chaining
         */
        public ValidationStep validateNotNull(Object obj, String message) {
            validations.add(Mono.defer(() -> {
                if (obj == null) {
                    return Mono.error(new IllegalArgumentException(message));
                }
                return Mono.empty();
            }));
            return this;
        }

        /**
         * Validates that the given string is not null, empty, or only whitespace.
         *
         * @param str     the string to validate
         * @param message the error message to throw if the validation fails
         * @return the current `ValidationStep` instance for method chaining
         */
        public ValidationStep validateNotEmpty(String str, String message) {
            validations.add(Mono.defer(() -> {
                if (str == null || str.trim().isEmpty()) {
                    return Mono.error(new IllegalArgumentException(message));
                }
                return Mono.empty();
            }));
            return this;
        }

        /**
         * Validates that the given file is not empty.
         *
         * @param file    the file to validate
         * @param message the error message to throw if the validation fails
         * @return the current `ValidationStep` instance for method chaining
         */
        public ValidationStep validateFileNotEmpty(FilePart file, String message) {
            validations.add(file.content()
                .hasElements()
                .flatMap(hasContent -> {
                    if (!hasContent) {
                        return Mono.error(new IllegalArgumentException(message));
                    }
                    return Mono.empty();
                }));
            return this;
        }

        /**
         * Validates that the size of the given file does not exceed the specified maximum size.
         *
         * @param file    the file to validate
         * @param maxSize the maximum allowed size in bytes
         * @return the current `ValidationStep` instance for method chaining
         */
        public ValidationStep validateFileSize(FilePart file, long maxSize) {
            validations.add(file.content()
                .map(dataBuffer -> dataBuffer.readableByteCount()) // Get the size of each chunk
                .reduce(0, Integer::sum) // Sum up the sizes of all chunks
                .flatMap(totalSize -> {
                    if (totalSize > maxSize) {
                        return Mono.error(new IllegalArgumentException("File too large (max " + (maxSize / 1024 / 1024) + "MB)"));
                    }
                    return Mono.empty();
                }));
            return this;
        }

        /**
         * Validates that the size of the given JSON string does not exceed the specified maximum size.
         *
         * @param json    the JSON string to validate
         * @param maxSize the maximum allowed size in bytes
         * @return the current `ValidationStep` instance for method chaining
         */
        public ValidationStep validateJsonSize(String json, long maxSize) {
            validations.add(Mono.defer(() -> {
                if (json.length() > maxSize) {
                    return Mono.error(new IllegalArgumentException("JSON content too large: " + json.length() + " bytes"));
                }
                return Mono.empty();
            }));
            return this;
        }

        /**
         * Validates that the given file has one of the specified file extensions.
         *
         * @param file       the file to validate
         * @param extensions the required file extensions (e.g., `.json`, `.yaml`, `.yml`)
         * @return the current `ValidationStep` instance for method chaining
         */
        public ValidationStep validateFileExtension(FilePart file, String... extensions) {
            validations.add(Mono.defer(() -> {
                String filename = file.filename();
                if (filename == null) {
                    return Mono.error(new IllegalArgumentException("File must have a valid name"));
                }

                boolean hasValidExtension = Arrays.stream(extensions)
                    .anyMatch(ext -> filename.toLowerCase().endsWith(ext.toLowerCase()));

                if (!hasValidExtension) {
                    String validExtensions = String.join(", ", extensions);
                    return Mono.error(new IllegalArgumentException("File must have one of these extensions: " + validExtensions));
                }
                return Mono.empty();
            }));
            return this;
        }

        /**
         * Validates that the given Postman `Collection` object has a valid structure.
         *
         * @param collection the `Collection` object to validate
         * @return the current `ValidationStep` instance for method chaining
         */
        public ValidationStep validateCollectionStructure(Collection collection) {
            validations.add(Mono.defer(() -> {
                if (collection.getItem() == null || collection.getItem().isEmpty()) {
                    return Mono.error(new IllegalArgumentException("Collection must have at least one item/request"));
                }
                return Mono.empty();
            }));
            return this;
        }

        /**
         * Executes all validation rules reactively. Throws on first failure.
         *
         * @return a `Mono<Void>` that completes successfully if all validations pass,
         *         or emits an error if any validation fails
         */
        public Mono<Void> execute() {
            return Mono.when(validations); // Combine all validation rules into a single reactive chain
        }
    }
}
