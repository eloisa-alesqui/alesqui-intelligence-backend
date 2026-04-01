package es.alesqui.intelligence.dto.security;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request payload for resending an activation email.
 * Used by endpoint: POST /api/auth/resend-activation
 */
@Data
public class ResendActivationRequest {
    
    /**
     * The user's email address.
     */
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;
}
