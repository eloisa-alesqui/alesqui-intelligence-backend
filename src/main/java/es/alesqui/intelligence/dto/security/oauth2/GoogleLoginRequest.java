package es.alesqui.intelligence.dto.security.oauth2;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for the POST /api/auth/oauth2/google endpoint.
 *
 * The frontend obtains the id_token directly from Google Identity Services
 * after the user completes the Google Sign-In popup, then sends it here
 * for server-side validation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoogleLoginRequest {

    /**
     * The id_token issued by Google Identity Services after a successful
     * sign-in. The backend validates this token against Google's tokeninfo
     * endpoint to verify authenticity, audience, issuer, and expiration
     * before issuing application-level JWT tokens.
     */
    @NotBlank
    private String idToken;
}
