package es.alesqui.intelligence.dto.security;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request payload for validating an activation token.
 * Used by endpoint: POST /api/auth/validate-token
 */
@Data
public class ValidateTokenRequest {
    
    /**
     * The activation token to validate.
     */
    @NotBlank(message = "Token is required")
    private String token;
}
