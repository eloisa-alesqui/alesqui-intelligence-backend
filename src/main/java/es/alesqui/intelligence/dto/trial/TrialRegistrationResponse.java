package es.alesqui.intelligence.dto.trial;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for successful trial registration.
 * 
 * Purpose:
 * - Confirm trial account creation
 * - Provide trial expiration information
 * - Indicate that activation email was sent
 * 
 * Example JSON:
 * {
 *   "message": "Trial account created successfully. Please check your email to activate your account.",
 *   "email": "user@example.com",
 *   "trialEndDate": "2026-01-17T10:30:00Z",
 *   "trialDurationDays": 14
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrialRegistrationResponse {
    
    /**
     * Success message for the user.
     */
    private String message;
    
    /**
     * Email address where activation link was sent.
     */
    private String email;
    
    /**
     * Trial expiration date (14 days from now).
     */
    private Instant trialEndDate;
    
    /**
     * Trial duration in days (typically 14).
     */
    private int trialDurationDays;
}
