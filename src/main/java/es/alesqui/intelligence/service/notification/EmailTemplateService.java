package es.alesqui.intelligence.service.notification;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import es.alesqui.intelligence.config.DeploymentConfig;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service responsible for building HTML email content from templates.
 * Templates are loaded eagerly at startup via @PostConstruct, making
 * all subsequent calls pure in-memory string replacements.
 *
 * Email sending is delegated to EmailService.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EmailTemplateService {

    private static final String ACTIVATION_TEMPLATE_PATH = "templates/email/activation-email.html";
    private static final String PASSWORD_RESET_TEMPLATE_PATH = "templates/email/password-reset-email.html";

    private final DeploymentConfig deploymentConfig;

    @Value("${frontend.url}")
    private String frontendUrl;

    private String activationTemplate;
    private String passwordResetTemplate;

    /**
     * Loads both email templates from the classpath at startup.
     * Failure here will prevent the application from starting, which is
     * intentional — missing templates are a misconfiguration, not a runtime edge case.
     */
    @PostConstruct
    private void init() throws IOException {
        activationTemplate = loadTemplate(ACTIVATION_TEMPLATE_PATH);
        passwordResetTemplate = loadTemplate(PASSWORD_RESET_TEMPLATE_PATH);
        log.debug("Email templates loaded");
    }

    /**
     * Reads a classpath resource into a UTF-8 string.
     *
     * @param path the classpath-relative path to the template file
     * @return the template content
     * @throws IOException if the resource cannot be read
     */
    private String loadTemplate(String path) throws IOException {
        return StreamUtils.copyToString(
            new ClassPathResource(path).getInputStream(),
            StandardCharsets.UTF_8
        );
    }

    /**
     * Builds the HTML content for an account activation email.
     * Context-aware: differentiates between TRIAL and CORPORATE modes.
     *
     * @param email the recipient's email address
     * @param token the activation token
     * @param role the user's role label (e.g., "Business", "IT")
     * @param createdByAdmin optional admin username who created the user (CORPORATE mode)
     * @return fully rendered HTML email content
     */
    public String buildActivationEmail(String email, String token, String role, String createdByAdmin) {
        String activationUrl = String.format("%s/activate-account?token=%s", frontendUrl, token);
        String context = getActivationContext(createdByAdmin);
        String html = activationTemplate
            .replace("{{email}}", email)
            .replace("{{role}}", role)
            .replace("{{activationUrl}}", activationUrl)
            .replace("{{context}}", context);
        log.debug("Built {} activation email for: {}", deploymentConfig.getMode(), email);
        return html;
    }

    /**
     * Builds the HTML content for an account activation email.
     * Used for trial self-registration where no creating admin is known.
     *
     * @param email the recipient's email address
     * @param token the activation token
     * @param role the user's role label (e.g., "Trial")
     * @return fully rendered HTML email content
     */
    public String buildActivationEmail(String email, String token, String role) {
        return buildActivationEmail(email, token, role, null);
    }

    /**
     * Builds the HTML content for a password reset email.
     *
     * @param email the recipient's email address
     * @param token the password reset token
     * @return fully rendered HTML email content
     */
    public String buildPasswordResetEmail(String email, String token) {
        String resetUrl = String.format("%s/reset-password?token=%s", frontendUrl, token);
        String html = passwordResetTemplate
            .replace("{{email}}", email)
            .replace("{{resetUrl}}", resetUrl);
        log.debug("Built password reset email for: {}", email);
        return html;
    }

    /**
     * Generates a context message based on deployment mode and who created the account.
     *
     * @param createdByAdmin optional username of the admin who created the account
     * @return a human-readable context sentence for the email body
     */
    private String getActivationContext(String createdByAdmin) {
        if (deploymentConfig.isCorporate() && StringUtils.isNotBlank(createdByAdmin)) {
            return String.format(
                "Your account has been created by <strong>%s</strong> from %s.",
                createdByAdmin,
                deploymentConfig.getCompanyName()
            );
        } else if (deploymentConfig.isCorporate()) {
            return String.format(
                "Your account has been created by an administrator at %s.",
                deploymentConfig.getCompanyName()
            );
        } else {
            return "Thank you for signing up for a trial of Alesqui Intelligence!";
        }
    }
}
