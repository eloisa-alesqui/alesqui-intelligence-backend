package es.alesqui.intelligence.model.api_spec.unified;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Represents the complete runtime configuration for a specific API.
 * This includes network settings, logging, rate limits, and a detailed,
 * nested authentication configuration.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL) // Exclude null fields from JSON serialization
public class ApiConfiguration {

    /**
     * The base URL for all API requests.
     * This value, if specified, overrides any base URL found in the API's
     * Swagger or Postman documentation.
     */
    @Field("baseUrl")
    @JsonProperty("baseUrl")
    private String baseUrl;

    /**
     * The maximum time in seconds to wait for a response from the API.
     */
    @Field("timeoutSeconds")
    @JsonProperty("timeoutSeconds")
    @Builder.Default
    private int timeoutSeconds = 30;

    /**
     * The maximum number of times to retry a failed request.
     */
    @Field("maxRetries")
    @JsonProperty("maxRetries")
    @Builder.Default
    private int maxRetries = 3;

    /**
     * If true, requests to and responses from the API will be logged.
     * This is useful for debugging purposes.
     */
    @Field("enableLogging")
    @JsonProperty("enableLogging")
    @Builder.Default
    private boolean enableLogging = true;

    /**
     * The maximum number of requests allowed per second.
     * A value of 0 means there is no rate limit.
     */
    @Field("rateLimit")
    @JsonProperty("rateLimit")
    @Builder.Default
    private int rateLimit = 0;

    /**
     * A nested object containing all authentication-related settings.
     * This structure supports multiple authentication strategies.
     */
    @Field("auth")
    @JsonProperty("auth")
    @Builder.Default
    private AuthenticationConfig auth = AuthenticationConfig.builder().build();

    // --- Nested Class for Authentication ---

    /**
     * Encapsulates all possible authentication configurations for an API.
     * The specific fields used are determined by the 'authType'.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AuthenticationConfig {

        /**
         * The type of authentication to use.
         * Possible values: "none", "api_key", "bearer", "basic", "oauth2_client_credentials".
         */
        @Field("authType")
        @JsonProperty("authType")
        @Builder.Default
        private String authType = "none";

        // --- Fields for API Key Authentication ---

        /**
         * The secret API key value.
         */
        @Field("apiKey")
        @JsonProperty("apiKey")
        private String apiKey;

        /**
         * The name of the header or query parameter where the API key will be sent.
         * Example: "X-API-Key" or "api_key".
         */
        @Field("apiKeyName")
        @JsonProperty("apiKeyName")
        @Builder.Default
        private String apiKeyName = "X-API-Key";

        /**
         * Specifies where to add the API key.
         * Possible values: "header" or "query".
         */
        @Field("addApiKeyTo")
        @JsonProperty("addApiKeyTo")
        @Builder.Default
        private String addApiKeyTo = "header";

        // --- Field for Bearer Token Authentication ---

        /**
         * The Bearer token value.
         */
        @Field("bearerToken")
        @JsonProperty("bearerToken")
        private String bearerToken;

        // --- Fields for Basic Authentication ---

        /**
         * The username for Basic Authentication.
         */
        @Field("basicAuthUsername")
        @JsonProperty("basicAuthUsername")
        private String basicAuthUsername;

        /**
         * The password for Basic Authentication.
         */
        @Field("basicAuthPassword")
        @JsonProperty("basicAuthPassword")
        private String basicAuthPassword;

        // --- Nested object for OAuth 2.0 Client Credentials Flow ---

        /**
         * A nested object containing configuration for the OAuth 2.0
         * Client Credentials grant type.
         */
        @Field("oauth2")
        @JsonProperty("oauth2")
        private OAuth2Config oauth2;
    }

    // --- Nested Class for OAuth 2.0 Client Credentials ---

    /**
     * Stores all necessary credentials for OAuth 2.0 flows.
     * The specific fields used depend on the selected grantType.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OAuth2Config {

        /**
         * The grant type to use. e.g., "client_credentials", "password".
         */
        @Field("grantType")
        @JsonProperty("grantType")
        @Builder.Default
        private String grantType = "client_credentials";
        
        /**
         * The URL of the authorization server's token endpoint.
         */
        @Field("tokenUrl")
        @JsonProperty("tokenUrl")
        private String tokenUrl;

        /**
         * The Client ID provided by the OAuth 2.0 authorization server.
         */
        @Field("clientId")
        @JsonProperty("clientId")
        private String clientId;

        /**
         * The Client Secret provided by the OAuth 2.0 authorization server.
         */
        @Field("clientSecret")
        @JsonProperty("clientSecret")
        private String clientSecret;
        
        /**
         * A space-separated list of scopes to request.
         */
        @Field("scopes")
        @JsonProperty("scopes")
        private String scopes;
        
        /**
         * The username for the resource owner (used in 'password' grant type).
         */
        @Field("username")
        @JsonProperty("username")
        private String username;

        /**
         * The password for the resource owner (used in 'password' grant type).
         */
        @Field("password")
        @JsonProperty("password")
        private String password;
    }
}