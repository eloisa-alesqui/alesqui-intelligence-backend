package es.alesqui.postmangpt.exception;

import lombok.Getter;

@Getter
public class ApiExecutionException extends RuntimeException {

    /**
     * Serial version UID for serialization compatibility.
     */
    private static final long serialVersionUID = 1L;

    // ================================================================================================
    // CONSTRUCTORS
    // ================================================================================================

    public ApiExecutionException(String message) {
        super(message);
    }
    
    public ApiExecutionException(String message, Throwable cause) {
        super(message, cause);
    }

}