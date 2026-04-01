package es.alesqui.intelligence.dto.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for token validation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidateTokenResponse {
    
    /**
     * Whether the token is valid.
     */
    private boolean valid;
    
    /**
     * The user's email address (if token is valid).
     */
    private String email;
    
    /**
     * The user's role (if token is valid).
     */
    private String role;
    
    /**
     * Error message (if token is invalid).
     */
    private String message;
}
