package es.alesqui.intelligence.dto.admin;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for user update operations.
 * Returns the updated user information without sensitive data (password).
 * 
 * Example JSON:
 * {
 *   "id": "user-123",
 *   "username": "new.email@example.com",
 *   "roles": ["USER", "ADMIN"],
 *   "createdAt": "2024-01-15T10:30:00Z",
 *   "groupCount": 2
 * }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserResponse {
    
    /** Unique identifier of the user. */
    private String id;

    /** User's email address (used as username). */
    private String username;

    /** Granted roles for the user. */
    private List<String> roles;

    /** Creation timestamp. */
    private Instant createdAt;

    /** Number of groups the user belongs to. */
    private long groupCount;
}
