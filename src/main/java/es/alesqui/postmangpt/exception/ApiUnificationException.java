package es.alesqui.postmangpt.exception;

import lombok.Getter;

/**
 * Custom exception for API unification failures. Provides detailed messaging for better debugging and error handling.
 */
@Getter
public class ApiUnificationException extends RuntimeException {

    /**
     * Serial version UID for serialization compatibility.
     */
    private static final long serialVersionUID = 1L;

    
    private final String documentName;
    private final String endpointPath;

    // ================================================================================================
    // CONSTRUCTORS
    // ================================================================================================

    public ApiUnificationException(String message, Throwable cause) {
        super(message, cause);
        this.documentName = null;
        this.endpointPath = null;
    }


    public ApiUnificationException(String message, String documentName, Throwable cause) {
        super(message, cause);
        this.documentName = documentName;
        this.endpointPath = null;
    }

    public ApiUnificationException(String message, String documentName, String endpointPath, Throwable cause) {
        super(message, cause);
        this.documentName = documentName;
        this.endpointPath = endpointPath;
    }

}