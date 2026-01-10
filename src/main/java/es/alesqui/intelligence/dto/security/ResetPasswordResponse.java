package es.alesqui.intelligence.dto.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for reset password operation.
 * Confirms that the password has been successfully reset.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ResetPasswordResponse {

    private boolean success;
    private String message;
    private String email;
}
