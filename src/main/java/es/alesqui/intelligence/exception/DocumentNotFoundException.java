package es.alesqui.intelligence.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Custom exception thrown when a requested document (e.g., from MongoDB) cannot be found.
 * This exception is typically used in service layers to indicate that an entity
 * requested by its ID or another unique identifier does not exist.
 * The @ResponseStatus annotation ensures that when this exception is thrown from a controller,
 * it automatically results in a 404 Not Found HTTP response.
 */
@Getter
@ResponseStatus(HttpStatus.NOT_FOUND) // Automatically returns a 404 HTTP status
public class DocumentNotFoundException extends RuntimeException {

    /**
     * Serial version UID for serialization compatibility.
     */
    private static final long serialVersionUID = 1L;

    // ================================================================================================
    // CONSTRUCTORS
    // ================================================================================================

    /**
     * Constructs a new DocumentNotFoundException with the specified detail message.
     *
     * @param message The detail message, which is saved for later retrieval by the getMessage() method.
     */
    public DocumentNotFoundException(String message) {
        super(message);
    }
    
    /**
     * Constructs a new DocumentNotFoundException with the specified detail message and cause.
     *
     * @param message The detail message.
     * @param cause The cause (which is saved for later retrieval by the getCause() method).
     */
    public DocumentNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}