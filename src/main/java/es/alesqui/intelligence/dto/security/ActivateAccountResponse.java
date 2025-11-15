package es.alesqui.intelligence.dto.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for account activation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivateAccountResponse {
    
    /**
     * Whether the activation was successful.
     */
    private boolean success;
    
    /**
     * Success or error message.
     */
    private String message;
    
    /**
     * The user's email (if successful).
     */
    private String email;
}
