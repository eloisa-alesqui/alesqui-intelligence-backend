package es.alesqui.intelligence.dto.admin;

import java.util.Set;

import es.alesqui.intelligence.model.core.enums.Role;
import es.alesqui.intelligence.validation.PasswordValidator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Request payload for partially updating a user's information.
 * Used by endpoint: PATCH /api/admin/users/{userId}
 * 
 * All fields are optional - only provided fields will be updated.
 * 
 * Business Rules:
 * - Username must be a valid email format if provided
 * - Password must meet minimum security requirements if provided
 * - Cannot remove ROLE_SUPERADMIN from the last superadmin user
 * - Username must be unique if changed
 * 
 * Example JSON (partial update):
 * {
 *   "username": "new.email@example.com",
 *   "password": "NewSecurePass123!",
 *   "roles": ["USER", "ADMIN"]
 * }
 */
@Data
public class UpdateUserRequest {
    
    /**
     * New username (email) for the user.
     * Must be a valid email format and unique across the system.
     */
    @Email(message = "Username must be a valid email address")
    private String username;
    
    /**
     * New password for the user.
     * Must meet minimum security requirements (will be BCrypt hashed).
     */
    @Pattern(regexp = PasswordValidator.PASSWORD_PATTERN, 
        message = PasswordValidator.PASSWORD_REQUIREMENTS_MESSAGE)
    private String password;
    
    /**
     * New set of roles for the user.
     * Replaces existing roles completely if provided.
     */
    private Set<Role> roles;
}
