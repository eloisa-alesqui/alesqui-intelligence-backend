package es.alesqui.intelligence.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import es.alesqui.intelligence.dto.admin.GroupSummaryResponse;
import es.alesqui.intelligence.model.core.enums.Role;
import es.alesqui.intelligence.service.access.GroupManagementService;
import es.alesqui.intelligence.service.identity.UserService;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

/**
 * REST controller for user-specific endpoints.
 * Provides endpoints for authenticated users to access their own information.
 */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class UserController {

    private final GroupManagementService groupManagementService;
    private final UserService userService;

    /**
     * Retrieves groups for the authenticated user.
     * - For SUPERADMIN users: returns all groups in the system
     * - For IT users: returns only groups the user belongs to
     * This endpoint is restricted to users with IT role or higher.
     *
     * @return a Flux of GroupSummaryResponse containing groups with counts
     */
    @GetMapping("/groups")
    public Flux<GroupSummaryResponse> getCurrentUserGroups() {
        return userService.getCurrentUser()
                .flatMapMany(user -> {
                    boolean isSuperAdmin = user.getRoles() != null && 
                            user.getRoles().contains(Role.ROLE_SUPERADMIN);
                    
                    if (isSuperAdmin) {
                        return groupManagementService.listGroupsWithCounts();
                    } else {
                        return groupManagementService.getGroupsForUser(user.getId());
                    }
                });
    }
}
