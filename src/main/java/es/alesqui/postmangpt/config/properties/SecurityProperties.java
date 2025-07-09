package es.alesqui.postmangpt.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * 🔐 Security Configuration Properties
 * 
 * Centralized configuration for all security-related settings
 */
@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

	/**
	 * 🔑 JWT Configuration
	 */
	@Valid
	private Jwt jwt = new Jwt();

	/**
	 * 🌐 CORS Configuration
	 */
	@Valid
	private Cors cors = new Cors();

	/**
	 * 🔒 OAuth2 Configuration
	 */
	@Valid
	private OAuth2 oauth2 = new OAuth2();

	@Data
	public static class Jwt {

		/**
		 * JWT Secret Key (should be at least 256 bits)
		 */
		@NotBlank(message = "JWT secret cannot be blank")
		private String secret = "postmangpt-super-secret-key-change-in-production-minimum-256-bits-required";

		/**
		 * Access token expiration time in milliseconds (default: 15 minutes)
		 */
		@Min(value = 60000, message = "JWT expiration must be at least 1 minute")
		private long expiration = 900000; // 15 minutes

		/**
		 * Refresh token expiration time in milliseconds (default: 7 days)
		 */
		@Min(value = 3600000, message = "Refresh expiration must be at least 1 hour")
		private long refreshExpiration = 604800000; // 7 days

		/**
		 * JWT Token issuer
		 */
		private String issuer = "postmangpt";

		/**
		 * JWT Token audience
		 */
		private String audience = "postmangpt-users";

		/**
		 * Enable JWT token refresh
		 */
		private boolean refreshEnabled = true;

		/**
		 * Maximum number of refresh tokens per user
		 */
		@Min(value = 1, message = "Max refresh tokens must be at least 1")
		private int maxRefreshTokens = 5;
	}

	@Data
	public static class Cors {

		/**
		 * Allowed origins for CORS
		 */
		@NotEmpty(message = "CORS allowed origins cannot be empty")
		private List<String> allowedOrigins = List.of("http://localhost:3000", "http://localhost:8080",
				"https://postmangpt.com");

		/**
		 * Allowed methods for CORS
		 */
		@NotEmpty(message = "CORS allowed methods cannot be empty")
		private List<String> allowedMethods = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

		/**
		 * Allowed headers for CORS
		 */
		@NotEmpty(message = "CORS allowed headers cannot be empty")
		private List<String> allowedHeaders = List.of("Authorization", "Content-Type", "X-Requested-With", "Accept",
				"Origin", "X-Request-ID");

		/**
		 * Allow credentials in CORS requests
		 */
		private boolean allowCredentials = true;

		/**
		 * Max age for CORS preflight requests (in seconds)
		 */
		@Min(value = 0, message = "CORS max age cannot be negative")
		private long maxAge = 3600;
	}

	@Data
	public static class OAuth2 {

		/**
		 * OAuth2 providers configuration
		 */
		private Providers providers = new Providers();

		/**
		 * OAuth2 redirect URIs
		 */
		private List<String> redirectUris = List.of("http://localhost:3000/auth/callback",
				"https://postmangpt.com/auth/callback");

		/**
		 * OAuth2 success redirect URL
		 */
		private String successRedirectUrl = "/dashboard";

		/**
		 * OAuth2 failure redirect URL
		 */
		private String failureRedirectUrl = "/login?error=true";

		@Data
		public static class Providers {

			/**
			 * Google OAuth2 configuration
			 */
			private Provider google = new Provider();

			/**
			 * GitHub OAuth2 configuration
			 */
			private Provider github = new Provider();

			@Data
			public static class Provider {

				/**
				 * Provider enabled
				 */
				private boolean enabled = false;

				/**
				 * Client ID
				 */
				private String clientId;

				/**
				 * Client Secret
				 */
				private String clientSecret;

				/**
				 * Authorization URI
				 */
				private String authorizationUri;

				/**
				 * Token URI
				 */
				private String tokenUri;

				/**
				 * User Info URI
				 */
				private String userInfoUri;

				/**
				 * Scopes
				 */
				private List<String> scopes = List.of();
			}
		}
	}
}