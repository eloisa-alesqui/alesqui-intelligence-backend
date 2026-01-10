package es.alesqui.intelligence.service.notification;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

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
public class EmailTemplateService {

    private static final String ACTIVATION_TEMPLATE_PATH = "templates/email/activation-email.html";
    private static final String PASSWORD_RESET_TEMPLATE_PATH = "templates/email/password-reset-email.html";
    private static final String LOGO_PATH = "static/images/logo.png";

    @Value("${frontend.url}")
    private String frontendUrl;

    // Cached templates for performance
    private String activationTemplate;
    private String passwordResetTemplate;

    /**
     * Builds the HTML content for an account activation email.
     * 
     * @param email the recipient's email address
     * @param token the activation token
     * @param role the user's role label (e.g., "Business", "IT")
     * @return fully rendered HTML email content
     */
    public String buildActivationEmail(String email, String token, String role) {
        String activationUrl = String.format("%s/activate-account?token=%s", frontendUrl, token);
        
        try {
            String template = loadActivationTemplate();
            
            String html = template
                .replace("{{email}}", email)
                .replace("{{role}}", role)
                .replace("{{activationUrl}}", activationUrl);
            
            log.debug("Built activation email for: {}", email);
            return html;
            
        } catch (IOException e) {
            log.error("Failed to load activation email template, using fallback", e);
            return buildFallbackActivationEmail(email, role, activationUrl);
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
    private String buildFallbackActivationEmail(String email, String role, String activationUrl) {
        return String.format("""
            <html>
            <body style="font-family: Arial, sans-serif; padding: 20px;">
                <h2>Welcome to Alesqui Intelligence!</h2>
                <p>Your account has been created:</p>
                <ul>
                    <li><strong>Email:</strong> %s</li>
                    <li><strong>Role:</strong> %s</li>
                </ul>
                <p>Click here to activate your account:</p>
                <p><a href="%s" style="background-color: #4F46E5; color: white; padding: 10px 20px; text-decoration: none; border-radius: 5px;">Activate Account</a></p>
                <p><small>Link expires in 48 hours</small></p>
            </body>
            </html>
            """, email, role, activationUrl);
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
                <p>Click the link below to reset your password:</p>
                <p><a href="%s" style="background-color: #DC2626; color: white; padding: 12px 24px; text-decoration: none; border-radius: 5px; display: inline-block;">Reset Password</a></p>
                <p><small>Link expires in 1 hour</small></p>
                <p>If you didn't request this, please ignore this email.</p>
                <hr>
                <p style="color: #6B7280; font-size: 12px;">Best regards,<br>The Alesqui Team</p>
            </body>
            </html>
            """, email, resetUrl);
    }
}
