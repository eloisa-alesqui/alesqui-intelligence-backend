package es.alesqui.intelligence.dto.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for forgot password operation.
 * Confirms that the password reset email has been sent.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ForgotPasswordResponse {

    private boolean success;
    private String message;
    private String email;
}
