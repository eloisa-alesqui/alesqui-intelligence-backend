package es.alesqui.intelligence.service.notification;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.UnsupportedEncodingException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Reactive service for sending emails using JavaMailSender.
 * Supports both plain text and HTML email formats.
 * 
 * This service uses SMTP configuration from application.properties
 * and provides methods for common email operations like welcome emails
 * and password reset notifications.
 * 
 * All operations are executed on the boundedElastic scheduler to avoid
 * blocking the main reactive pipeline.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${mail.from.email}")
    private String fromEmail;

    @Value("${mail.from.name}")
    private String fromName;

    /**
     * Sends a simple plain text email reactively.
     * 
     * @param to the recipient email address
     * @param subject the email subject line
     * @param text the plain text content of the email
     * @return a Mono that completes when the email is sent
     */
    public Mono<Void> sendSimpleEmail(String to, String subject, String text) {
        return Mono.fromRunnable(() -> {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(fromEmail);
                message.setTo(to);
                message.setSubject(subject);
                message.setText(text);
                
                mailSender.send(message);
                
                log.info("✅ Email SUCCESSFULLY SENT to: {}", to);
                
            } catch (Exception e) {
                log.error("❌ Failed to send email to: {}", to, e);
                throw new RuntimeException("Failed to send email", e);
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    /**
     * Sends an HTML formatted email reactively.
     * 
     * @param to the recipient email address
     * @param subject the email subject line
     * @param htmlContent the HTML content of the email
     * @return a Mono that completes when the email is sent
     */
    public Mono<Void> sendHtmlEmail(String to, String subject, String htmlContent) {
        return Mono.fromRunnable(() -> {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                
                helper.setFrom(fromEmail, fromName);
                helper.setTo(to);
                helper.setSubject(subject);
                helper.setText(htmlContent, true); // true = HTML format
                
                mailSender.send(message);
                
                log.info("✅ HTML email SUCCESSFULLY SENT to: {}", to);
                
            } catch (MessagingException | UnsupportedEncodingException e) {
                log.error("❌ Failed to send HTML email to: {}", to, e);
                throw new RuntimeException("Failed to send HTML email", e);
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    /**
     * Sends a welcome email to a new user reactively.
     * 
     * @param to the recipient email address
     * @param userName the name of the user to personalize the email
     * @return a Mono that completes when the email is sent
     */
    public Mono<Void> sendWelcomeEmail(String to, String userName) {
        String subject = "Welcome to Alesqui Intelligence!";
        String htmlContent = """
            <html>
            <body style="font-family: Arial, sans-serif;">
                <h2>Hello %s!</h2>
                <p>Welcome to <strong>Alesqui Intelligence</strong>.</p>
                <p>We're excited to have you with us.</p>
                <br>
                <p>Best regards,<br>The Alesqui Team</p>
            </body>
            </html>
            """.formatted(userName);
        
        return sendHtmlEmail(to, subject, htmlContent);
    }

    /**
     * Sends a password reset email with a reset token link reactively.
     * 
     * The reset link expires in 1 hour.
     * 
     * @param to the recipient email address
     * @param resetToken the unique token for password reset verification
     * @return a Mono that completes when the email is sent
     */
    public Mono<Void> sendPasswordResetEmail(String to, String resetToken) {
        String subject = "Password Recovery";
        String resetUrl = "https://alesqui.com/reset-password?token=" + resetToken;
        
        String htmlContent = """
            <html>
            <body style="font-family: Arial, sans-serif;">
                <h2>Password Recovery</h2>
                <p>You have requested to reset your password.</p>
                <p>Click the following link to continue:</p>
                <a href="%s" style="display: inline-block; padding: 10px 20px; 
                   background-color: #007bff; color: white; text-decoration: none; 
                   border-radius: 5px;">Reset Password</a>
                <p>This link will expire in 1 hour.</p>
                <p>If you did not request this change, please ignore this email.</p>
                <br>
                <p>Best regards,<br>The Alesqui Team</p>
            </body>
            </html>
            """.formatted(resetUrl);
        
        return sendHtmlEmail(to, subject, htmlContent);
    }
}