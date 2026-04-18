package es.alesqui.intelligence.dto.security.oauth2;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response returned by both OAuth2 endpoints (login and link confirmation).
 *
 * Two distinct outcomes are possible:
 *
 * 1. Successful authentication — accessToken and refreshToken are populated,
 *    linkRequired is false. The frontend should store the tokens and redirect
 *    the user to the dashboard.
 *
 * 2. Account conflict — a LOCAL account already exists for the same email.
 *    accessToken and refreshToken are null, linkRequired is true, and
 *    linkChallenge contains a short-lived token the frontend must include
 *    when calling the /google/link endpoint to confirm the merge.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuth2AuthResponse {

    /**
     * Short-lived JWT for authenticating API requests.
     * Null when linkRequired is true.
     */
    private String accessToken;

    /**
     * Long-lived JWT used to obtain a new access token without re-authenticating.
     * Null when linkRequired is true.
     */
    private String refreshToken;

    /**
     * The authentication provider used for this session.
     * Always "GOOGLE" for responses from these endpoints.
     */
    private String authProvider;

    /**
     * True if a new user account was created as a result of this request.
     * False if the user already had an account and simply logged in.
     */
    private boolean newUser;

    /**
     * True when a LOCAL account already exists for the same email address.
     * When this flag is set, accessToken and refreshToken are not issued.
     * The frontend should show a confirmation dialog and call /google/link
     * with the linkChallenge to complete the account merge.
     */
    private boolean linkRequired;

    /**
     * Short-lived signed JWT that authorizes the account-linking operation.
     * Only present when linkRequired is true.
     * Must be included in the subsequent POST /api/auth/oauth2/google/link request.
     * Expires according to app.oauth2.google.link-challenge-ttl (default 5 minutes).
     */
    private String linkChallenge;
}
