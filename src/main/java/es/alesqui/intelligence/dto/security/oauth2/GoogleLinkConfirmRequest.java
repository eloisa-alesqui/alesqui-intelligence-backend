package es.alesqui.intelligence.dto.security.oauth2;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for the POST /api/auth/oauth2/google/link endpoint.
 *
 * Sent by the frontend when the user confirms they want to link their existing
 * LOCAL account to their Google identity. Both fields are required: the
 * id_token proves the user still controls the Google account, and the
 * linkChallenge proves the backend previously authorized this specific
 * linking operation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoogleLinkConfirmRequest {

    /**
     * The Google id_token obtained during the sign-in flow.
     * Re-validated here to confirm the user still controls the Google account
     * at the moment of linking confirmation.
     */
    @NotBlank
    private String idToken;

    /**
     * The short-lived signed JWT previously returned by the login endpoint
     * when a LOCAL account conflict was detected.
     * Binds this confirmation request to a specific email and Google identity,
     * preventing replay or cross-account linking attacks.
     * Expires according to app.oauth2.google.link-challenge-ttl (default 5 minutes).
     */
    @NotBlank
    private String linkChallenge;
}
