package es.alesqui.intelligence.dto.security;

import es.alesqui.intelligence.validation.PasswordValidator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Request payload for activating an account with a new password.
 * Used by endpoint: POST /api/auth/activate-account
 */
@Data
public class ActivateAccountRequest {
    
    /**
     * The activation token received via email.
     */
    @NotBlank(message = "Token is required")
    private String token;
    
    /**
     * The new password for the account.
     * Must meet security requirements: min 8 characters, uppercase, lowercase, number, special char.
     */
    @NotBlank(message = "Password is required")
    @Pattern(regexp = PasswordValidator.PASSWORD_PATTERN, 
         message = PasswordValidator.PASSWORD_REQUIREMENTS_MESSAGE)
    private String password;
}
