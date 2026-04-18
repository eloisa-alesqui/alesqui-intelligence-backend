package es.alesqui.intelligence.dto.security.oauth2;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Maps the JSON response returned by Google's tokeninfo endpoint
 * (https://oauth2.googleapis.com/tokeninfo?id_token=...).
 *
 * This class is used internally by GoogleTokenVerificationService to
 * deserialize and validate the claims inside a Google id_token. It is
 * never exposed directly to API callers.
 *
 * All numeric fields (exp) are represented as strings because Google's
 * tokeninfo endpoint returns them as JSON strings, not numbers.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GoogleTokenInfo {

    /**
     * Issuer of the token. Must be one of the values in
     * app.oauth2.google.allowed-issuers (e.g. "accounts.google.com").
     */
    @JsonProperty("iss")
    private String iss;

    /**
     * Audience the token was issued for. Must exactly match
     * app.oauth2.google.client-id to confirm the token was issued
     * for this application and not another.
     */
    @JsonProperty("aud")
    private String aud;

    /**
     * Google's stable, unique identifier for the user ("subject").
     * This value never changes even if the user updates their email,
     * making it the reliable key for identifying a Google account.
     * Stored as providerId on the User document.
     */
    @JsonProperty("sub")
    private String sub;

    /**
     * The user's email address as registered in their Google account.
     * Used as the username when creating a new account or looking up
     * an existing one.
     */
    @JsonProperty("email")
    private String email;

    /**
     * Whether Google has verified ownership of the email address.
     * Google returns this as the string "true" or "false", not a boolean.
     * Login is rejected if this value is not "true".
     */
    @JsonProperty("email_verified")
    private String emailVerified;

    /**
     * The user's full display name as set in their Google account.
     * Informational only; not stored on the User document at this time.
     */
    @JsonProperty("name")
    private String name;

    /**
     * URL of the user's Google profile picture.
     * Informational only; not stored on the User document at this time.
     */
    @JsonProperty("picture")
    private String picture;

    /**
     * Token expiration time as a Unix epoch timestamp (seconds), returned
     * as a string by Google. Validated to ensure the token has not expired
     * before any account operations are performed.
     */
    @JsonProperty("exp")
    private String exp;

    /**
     * Authorized party: the client ID of the application that requested
     * the token. May differ from aud in some delegation scenarios.
     * Captured for completeness but not validated in the current flow.
     */
    @JsonProperty("azp")
    private String azp;
}
