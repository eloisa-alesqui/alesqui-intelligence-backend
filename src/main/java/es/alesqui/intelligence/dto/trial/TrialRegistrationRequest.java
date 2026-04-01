package es.alesqui.intelligence.dto.trial;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for trial user registration from public landing page.
 * 
 * Purpose:
 * - Capture minimal information needed to create a trial account
 * - Validates email format
 * - Used by POST /api/public/trial-registration
 * 
 * Business Rules:
 * - Email must be valid and unique (enforced at service level)
 * - Trial users automatically get ROLE_TRIAL
 * - Trial duration is 14 days from registration
 * - Activation email sent with secure token
 * - Rate limited by IP address (1 trial per IP per day)
 * 
 * Example JSON:
 * {
 *   "email": "user@example.com"
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrialRegistrationRequest {
    
    /**
     * Email address for the trial account.
     * Must be valid email format and unique in the system.
     */
    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    private String email;
}
