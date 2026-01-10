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
 * This service uses SMTP configuration from application.properties.
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

}