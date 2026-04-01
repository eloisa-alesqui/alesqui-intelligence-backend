package es.alesqui.intelligence.dto.admin;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for trial user monitoring in admin panel.
 * 
 * Purpose:
 * - Provides comprehensive view of trial users for admin monitoring
 * - Shows trial status, expiration, and activity info
 * - Used by GET /api/admin/trial-users
 * 
 * Example JSON:
 * {
 *   "id": "user-123",
 *   "email": "trial@example.com",
 *   "isActive": true,
 *   "trialStartDate": "2026-01-03T10:30:00Z",
 *   "trialEndDate": "2026-01-17T10:30:00Z",
 *   "daysRemaining": 14,
 *   "isExpired": false,
 *   "createdAt": "2026-01-03T10:30:00Z",
 *   "workspaceCode": "trial-user-123"
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrialUserResponse {
    
    /**
     * User ID.
     */
    private String id;
    
    /**
     * User email address.
     */
    private String email;
    
    /**
     * Whether account is active (has completed activation).
     */
    private boolean isActive;
    
    /**
     * Trial start date.
     */
    private Instant trialStartDate;
    
    /**
     * Trial expiration date.
     */
    private Instant trialEndDate;
    
    /**
     * Days remaining in trial (0 if expired).
     */
    private long daysRemaining;
    
    /**
     * Whether the trial has expired.
     */
    private boolean isExpired;
    
    /**
     * Account creation timestamp.
     */
    private Instant createdAt;
    
    /**
     * Auto-created workspace code (e.g., "trial-user-123").
     */
    private String workspaceCode;
    
    /**
     * User roles.
     */
    private List<String> roles;
}
