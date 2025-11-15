package es.alesqui.intelligence.dto.admin;

import java.util.Set;

import es.alesqui.intelligence.model.core.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request payload for creating a new user.
 * Used by endpoint: POST /api/admin/users
 * 
 * Password is optional - if not provided, user will receive activation email.
 * 
 * Business Rules:
 * - Username must be a valid email format and unique
 * - If password provided: must be 8+ characters and user is immediately active
 * - If password omitted: activation email sent, user must set password via link
 * - At least one role must be assigned
 * 
 * Example JSON (with password - immediate activation):
 * {
 *   "username": "user@example.com",
 *   "password": "SecurePass123!",
 *   "roles": ["ROLE_IT"]
 * }
 * 
 * Example JSON (without password - requires activation):
 * {
 *   "username": "user@example.com",
 *   "roles": ["ROLE_IT"]
 * }
 */
@Data
public class CreateUserRequest {
    
    /**
     * Username (email) for the new user.
     * Must be a valid email format and unique across the system.
     */
    @NotBlank(message = "Username is required")
    @Email(message = "Username must be a valid email address")
    private String username;
    
    /**
     * Password for the new user (OPTIONAL).
     * If provided: must be at least 8 characters, user is immediately active.
     * If omitted: activation email sent, user sets password via secure link.
     */
    @Size(min = 8, message = "Password must be at least 8 characters long")
    private String password;
    
    /**
     * Set of roles for the new user.
     * At least one role must be provided.
     */
    @NotNull(message = "Roles are required")
    @Size(min = 1, message = "At least one role must be assigned")
    private Set<Role> roles;
}
