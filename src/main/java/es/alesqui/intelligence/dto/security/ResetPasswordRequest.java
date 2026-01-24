package es.alesqui.intelligence.dto.security;

import es.alesqui.intelligence.validation.PasswordValidator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for resetting password with a token.
 * Contains the reset token and the new password.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ResetPasswordRequest {

    @NotBlank(message = "Token cannot be blank")
    private String token;

    @NotBlank(message = "Password cannot be blank")
    @Pattern(regexp = PasswordValidator.PASSWORD_PATTERN, 
        message = PasswordValidator.PASSWORD_REQUIREMENTS_MESSAGE)
    private String newPassword;
}
