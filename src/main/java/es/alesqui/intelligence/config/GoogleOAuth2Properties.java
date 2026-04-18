package es.alesqui.intelligence.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * Typed configuration properties for Google OAuth2 social login.
 *
 * All properties are bound from the "app.oauth2.google" prefix in
 * application.properties.
 *
 * Required environment variables for production:
 *   GOOGLE_CLIENT_ID     - OAuth2 client ID from Google Cloud Console
 *   GOOGLE_CLIENT_SECRET - OAuth2 client secret (reserved for future code flow)
 *
 * Optional overrides:
 *   GOOGLE_TOKENINFO_URL  - Override the tokeninfo endpoint (useful for WireMock in tests)
 *   GOOGLE_ALLOWED_ISSUERS - Comma-separated list of accepted JWT issuers
 *   GOOGLE_OAUTH2_ENABLED  - Set to false to disable the feature at runtime (default: true)
 */
@Configuration
@ConfigurationProperties(prefix = "app.oauth2.google")
@Data
public class GoogleOAuth2Properties {

    /**
     * OAuth2 client ID issued by Google Cloud Console.
     * Must match the "aud" claim in the Google id_token.
     * Maps to the GOOGLE_CLIENT_ID environment variable.
     */
    private String clientId;

    /**
     * OAuth2 client secret issued by Google Cloud Console.
     * The current login flow works entirely with the id_token that the browser
     * receives from Google, so the backend never needs this secret.
     * It is stored here so it is available if a server-side authorization code
     * flow is added in the future.
     * Maps to the GOOGLE_CLIENT_SECRET environment variable.
     */
    private String clientSecret;

    /**
     * URL of Google's tokeninfo endpoint used to validate id_tokens.
     * Defaults to the official Google endpoint.
     * Override in tests via GOOGLE_TOKENINFO_URL to point at a WireMock stub.
     */
    private String tokeninfoUrl = "https://oauth2.googleapis.com/tokeninfo";

    /**
     * Accepted values for the "iss" claim inside a Google id_token.
     * Google may issue tokens with either form of the issuer URL, so both
     * are included by default.
     */
    private List<String> allowedIssuers = List.of("accounts.google.com", "https://accounts.google.com");

    /**
     * Time-to-live for the link challenge JWT issued when a LOCAL account
     * already exists for the same email address.
     * The frontend must complete the account-linking flow before this expires.
     * Defaults to 5 minutes.
     */
    private Duration linkChallengeTtl = Duration.ofMinutes(5);

    /**
     * Master switch for the Google OAuth2 login feature.
     * Set to false via GOOGLE_OAUTH2_ENABLED to disable the endpoints at
     * runtime without redeploying, for example during an incident.
     * Defaults to true.
     */
    private boolean enabled = true;
}
