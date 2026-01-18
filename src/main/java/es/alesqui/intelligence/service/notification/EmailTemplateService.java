package es.alesqui. intelligence.service.notification;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import es.alesqui.intelligence.config.DeploymentConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service responsible for building HTML email content from templates.
 * Handles template loading, caching, variable replacement, and fallback content.
 * 
 * This service focuses solely on template management and HTML generation,
 * while email sending is delegated to EmailService.
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

    // Cached templates for performance
    private String activationTemplate;
    private String passwordResetTemplate;

    /**
     * Builds the HTML content for an account activation email.
     * Context-aware:  differentiates between TRIAL and CORPORATE modes.
     * 
     * @param email the recipient's email address
     * @param token the activation token
     * @param role the user's role label (e.g., "Business", "IT")
     * @param createdByAdmin optional admin username who created the user (CORPORATE mode)
     * @return fully rendered HTML email content
     */
    public String buildActivationEmail(String email, String token, String role, String createdByAdmin) {
        String activationUrl = String.format("%s/activate-account?token=%s", frontendUrl, token);
        
        try {
            String template = loadActivationTemplate();
            
            // Context-specific replacements
            String context = getActivationContext(createdByAdmin);
            
            String html = template
                .replace("{{email}}", email)
                .replace("{{role}}", role)
                .replace("{{activationUrl}}", activationUrl)
                .replace("{{context}}", context);
            
            log.debug("Built {} activation email for:  {}", 
                    deploymentConfig.getMode(), email);
            return html;
            
        } catch (IOException e) {
            log.error("Failed to load activation email template, using fallback", e);
            return buildFallbackActivationEmail(email, role, activationUrl, createdByAdmin);
        }
    }

    /**
     * Builds the HTML content for an account activation email.
     * This overload is used when the creating admin is unknown or not applicable,
     * typically for trial user self-registration.
     * 
     * @param email the recipient's email address
     * @param token the activation token for account verification
     * @param role the user's role label (e.g., "Business", "IT", "Trial")
     * @return fully rendered HTML email content
     */
    public String buildActivationEmail(String email, String token, String role) {
        return buildActivationEmail(email, token, role, null);
    }

    /**
     * Generates context message based on deployment mode.
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
            // TRIAL mode
            return "Thank you for signing up for a trial of Alesqui Intelligence!";
        }
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
        
        try {
            String template = loadPasswordResetTemplate();
            
            String html = template
                .replace("{{email}}", email)
                .replace("{{resetUrl}}", resetUrl);
            
            log.debug("Built password reset email for: {}", email);
            return html;
            
        } catch (IOException e) {
            log.error("Failed to load password reset template, using fallback", e);
            return buildFallbackPasswordResetEmail(email, resetUrl);
        }
    }

    /**
     * Loads the activation email template from classpath.
     * Template is cached after first load for performance.
     */
    private String loadActivationTemplate() throws IOException {
        if (activationTemplate == null) {
            ClassPathResource resource = new ClassPathResource(ACTIVATION_TEMPLATE_PATH);
            activationTemplate = StreamUtils.copyToString(
                resource.getInputStream(), 
                StandardCharsets.UTF_8
            );
            log.debug("Activation template loaded from: {}", ACTIVATION_TEMPLATE_PATH);
        }
        return activationTemplate;
    }

    /**
     * Loads the password reset email template from classpath.
     * Template is cached after first load for performance.
     */
    private String loadPasswordResetTemplate() throws IOException {
        if (passwordResetTemplate == null) {
            ClassPathResource resource = new ClassPathResource(PASSWORD_RESET_TEMPLATE_PATH);
            passwordResetTemplate = StreamUtils.copyToString(
                resource.getInputStream(), 
                StandardCharsets.UTF_8
            );
            log.debug("Password reset template loaded from: {}", PASSWORD_RESET_TEMPLATE_PATH);
        }
        return passwordResetTemplate;
    }

    /**
     * Fallback activation email body if template loading fails.
     */
    private String buildFallbackActivationEmail(String email, String role, 
                                                String activationUrl, String createdByAdmin) {
        String context = getActivationContext(createdByAdmin);
        
        return String.format("""
            <html>
            <body style="font-family: Arial, sans-serif; padding: 20px;">
                <h2>Welcome to Alesqui Intelligence!</h2>
                <p>%s</p>
                <ul>
                    <li><strong>Email:</strong> %s</li>
                    <li><strong>Role:</strong> %s</li>
                </ul>
                <p>Click here to activate your account: </p>
                <a href="%s" style="background:  #2563EB; color: white; padding: 12px 24px; 
                   text-decoration: none; border-radius: 6px; display: inline-block;">
                    Activate Account
                </a>
                <p style="color: #6B7280; font-size:  12px; margin-top: 20px;">
                    This link will expire in 48 hours.
                </p>
                <p style="color: #6B7280; font-size: 12px;">
                    Best regards,<br>The %s Team
                </p>
            </body>
            </html>
            """, 
            context,
            email, 
            role, 
            activationUrl,
            deploymentConfig.getCompanyName()
        );
    }

    /**
     * Fallback password reset email body if template loading fails.
     */
    private String buildFallbackPasswordResetEmail(String email, String resetUrl) {
        return String.format("""
            <html>
            <body style="font-family: Arial, sans-serif; padding: 20px;">
                <h2>Password Reset Request</h2>
                <p>Hello,</p>
                <p>We received a request to reset the password for your account (%s).</p>
                <p>Click the link below to reset your password: </p>
                <a href="%s" style="background:  #DC2626; color: white; padding:  12px 24px; 
                   text-decoration: none; border-radius: 6px; display: inline-block;">
                    Reset Password
                </a>
                <p style="color: #DC2626; font-weight: 600; font-size: 14px; margin-top: 20px;">
                    ⏱️ This link will expire in <strong>1 hour</strong>. 
                </p>
                <p style="color: #6B7280; font-size: 14px; margin-top: 20px;">
                    If you didn't request this, please ignore this email.  Your password will remain unchanged.
                </p>
                <p style="color: #6B7280; font-size: 12px; margin-top: 32px; padding-top: 16px; border-top: 1px solid #E5E7EB;">
                    Best regards,<br>The %s Team
                </p>
            </body>
            </html>
            """, email, resetUrl, deploymentConfig.getCompanyName());
    }
}