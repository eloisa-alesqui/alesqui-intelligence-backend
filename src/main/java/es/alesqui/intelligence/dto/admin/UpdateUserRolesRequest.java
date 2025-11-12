package es.alesqui.intelligence.dto.admin;

import java.util.Set;

import es.alesqui.intelligence.model.core.enums.Role;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * Request to update roles for a user.
 */
@Data
public class UpdateUserRolesRequest {
    @NotEmpty
    private Set<Role> roles; // complete replacement of role set
}
