package es.alesqui.intelligence.model.core.enums;

/**
 * Identifies the authentication provider used to create or last authenticate a user account.
 *
 * Stored on the User document to distinguish accounts created through the
 * classic email and password flow from those created via social login.
 * Users with a LOCAL account may later link a Google identity, at which point
 * their authProvider is updated to GOOGLE while their password is preserved.
 */
public enum AuthProvider {

    /**
     * The user authenticates with an email address and a password managed
     * by this application. This is the default for accounts created by an
     * administrator or through the standard registration flow.
     */
    LOCAL,

    /**
     * The user authenticates via Google Sign-In. The account was either
     * created directly through Google OAuth2 or was originally a LOCAL
     * account that the user chose to link to their Google identity.
     * Users in this state have a non-null providerId containing Google's
     * stable "sub" identifier for their account.
     */
    GOOGLE
}
