package es.alesqui.intelligence.dto.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
    @Size(min = 8, message = "Password must be at least 8 characters long")
    private String newPassword;
}
