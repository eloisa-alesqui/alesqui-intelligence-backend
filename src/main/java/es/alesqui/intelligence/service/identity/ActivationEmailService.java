package es.alesqui.intelligence.service.identity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import es.alesqui.intelligence.service.notification.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Service for sending user activation emails.
 * Handles generation of activation tokens and email notifications.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ActivationEmailService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_LENGTH = 32;
    private static final long TOKEN_VALIDITY_HOURS = 48;
    private static final String EMAIL_TEMPLATE_PATH = "templates/email/activation-email.html";
    private static final String LOGO_PATH = "static/images/logo.png";
    
    private final EmailService emailService;
    private String emailTemplate; // Cached template
    private String logoBase64; // Cached logo
    
    /**
     * Generates a cryptographically secure random token for account activation.
     * 
     * @return a 64-character hexadecimal string (32 bytes)
     */
    public String generateActivationToken() {
        byte[] tokenBytes = new byte[TOKEN_LENGTH];
        SECURE_RANDOM.nextBytes(tokenBytes);
        return bytesToHex(tokenBytes);
    }
    
    /**
     * Calculates the expiration time for an activation token.
     * Default is 48 hours from now.
     * 
     * @return the expiration timestamp
     */
    public Instant calculateTokenExpiration() {
        return Instant.now().plusSeconds(TOKEN_VALIDITY_HOURS * 3600);
    }
    
    /**
     * Sends an activation email to the specified user.
     * Email contains a secure link with the activation token.
     * 
     * @param email the user's email address
     * @param token the activation token
     * @param roles the user's assigned roles
     * @return a Mono signaling completion
     */
    public Mono<Void> sendActivationEmail(String email, String token, String roles) {
        String activationUrl = String.format("https://yourapp.com/activate-account?token=%s", token);
        
        String emailBody = buildEmailBody(email, roles, activationUrl);
        
        log.info("Sending activation email to: {}", email);
        log.debug("Activation URL: {}", activationUrl);
        
        return emailService.sendHtmlEmail(email, "Activate Your Account - Alesqui Intelligence", emailBody);
    }
    
    /**
     * Builds the HTML body of the activation email using a template.
     * Template variables: {{email}}, {{role}}, {{activationUrl}}, {{logoBase64}}
     */
    private String buildEmailBody(String email, String roles, String activationUrl) {
        try {
            String template = loadEmailTemplate();
            String logo = loadLogoAsBase64();
            
            return template
                .replace("{{email}}", email)
                .replace("{{role}}", roles)
                .replace("{{activationUrl}}", activationUrl)
                .replace("{{logoBase64}}", logo);
                
        } catch (IOException e) {
            log.error("Failed to load email template, using fallback", e);
            return buildFallbackEmailBody(email, roles, activationUrl);
        }
    }
    
    /**
     * Loads the email template from classpath.
     * Template is cached after first load for performance.
     */
    private String loadEmailTemplate() throws IOException {
        if (emailTemplate == null) {
            ClassPathResource resource = new ClassPathResource(EMAIL_TEMPLATE_PATH);
            emailTemplate = StreamUtils.copyToString(
                resource.getInputStream(), 
                StandardCharsets.UTF_8
            );
            log.debug("Email template loaded from: {}", EMAIL_TEMPLATE_PATH);
        }
        return emailTemplate;
    }
    
    /**
     * Loads the logo image from classpath and converts it to Base64.
     * Logo is cached after first load for performance.
     */
    private String loadLogoAsBase64() throws IOException {
        if (logoBase64 == null) {
            try {
                ClassPathResource resource = new ClassPathResource(LOGO_PATH);
                byte[] imageBytes = StreamUtils.copyToByteArray(resource.getInputStream());
                logoBase64 = "data:image/png;base64," + Base64.getEncoder().encodeToString(imageBytes);
                log.debug("Logo loaded and encoded from: {}", LOGO_PATH);
            } catch (IOException e) {
                log.warn("Logo not found at {}, using empty string", LOGO_PATH);
                logoBase64 = ""; // Fallback to empty if logo doesn't exist
            }
        }
        return logoBase64;
    }
    
    /**
     * Fallback email body if template loading fails.
     */
    private String buildFallbackEmailBody(String email, String roles, String activationUrl) {
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
            """, email, roles, activationUrl);
    }
    
    /**
     * Converts a byte array to a hexadecimal string.
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
