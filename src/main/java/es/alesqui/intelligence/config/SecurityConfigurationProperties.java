package es.alesqui.intelligence.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Configuration
@ConfigurationProperties(prefix = "app.security")
@Data
public class SecurityConfigurationProperties {

    private PasswordReset passwordReset = new PasswordReset();
    private OAuth2 oauth2 = new OAuth2();

    @Data
    public static class PasswordReset {
        private int windowMinutes = 15;
        private int maxAttempts = 3;
    }

    @Data
    public static class OAuth2 {
        private int windowMinutes = 1;
        private int maxAttempts = 10;
    }
}
