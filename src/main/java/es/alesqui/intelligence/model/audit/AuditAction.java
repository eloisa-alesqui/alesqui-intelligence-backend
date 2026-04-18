package es.alesqui.intelligence.model.audit;

/**
 * Enumeration of all auditable actions in the system.
 *
 * Each constant carries a human-readable description so any layer of the
 * application can render it without coupling to a switch or external mapper.
 *
 * Actions are grouped into:
 * - User lifecycle operations (create, update, delete, activate)
 * - User credential and role management
 * - Group CRUD operations
 * - Group membership operations
 * - API access control operations
 * - Authentication and security events
 */
public enum AuditAction {

    // ==================== User Management Actions ====================

    /** Admin creates a user via POST /api/admin/users */
    USER_CREATED("User account created"),

    /** Admin updates user details via PATCH /api/admin/users/{userId} */
    USER_UPDATED("User information updated"),

    /** Admin deletes a user via DELETE /api/admin/users/{userId} */
    USER_DELETED("User account deleted"),

    /** User completes account activation with a valid token */
    USER_ACTIVATED("User account activated"),

    /** Admin deactivates a user account */
    USER_DEACTIVATED("User account deactivated"),

    /** Admin or user changes password */
    USER_PASSWORD_CHANGED("User password changed"),

    /** Admin updates user roles via PATCH /api/admin/users/{userId}/roles */
    USER_ROLES_CHANGED("User roles modified"),

    /** Admin assigns user to groups via POST /api/admin/users/{userId}/groups */
    USER_ASSIGNED_TO_GROUP("User assigned to group"),

    /** Admin removes user from group via DELETE /api/admin/users/{userId}/groups/{groupId} */
    USER_REMOVED_FROM_GROUP("User removed from group"),

    // ==================== Group Management Actions ====================

    /** Admin creates a group via POST /api/admin/groups */
    GROUP_CREATED("Group created"),

    /** Admin updates group details via PATCH /api/admin/groups/{groupId} */
    GROUP_UPDATED("Group information updated"),

    /** Admin deletes a group via DELETE /api/admin/groups/{groupId} */
    GROUP_DELETED("Group deleted"),

    /** Admin assigns users to group via POST /api/admin/groups/{groupId}/users */
    GROUP_USERS_ASSIGNED("Users assigned to group"),

    /** Admin removes user from group via DELETE /api/admin/groups/{groupId}/users/{userId} */
    GROUP_USER_REMOVED("User removed from group"),

    /** Admin assigns APIs to group via POST /api/admin/groups/{groupId}/apis */
    GROUP_APIS_ASSIGNED("APIs assigned to group"),

    /** Admin removes API from group via DELETE /api/admin/groups/{groupId}/apis/{apiId} */
    GROUP_API_REMOVED("API removed from group"),

    // ==================== API Management Actions ====================

    /** User creates an API via unification service */
    API_CREATED("API created"),

    /** User updates API settings */
    API_UPDATED("API updated"),

    /** User deletes an API */
    API_DELETED("API deleted"),

    /** Admin assigns API to groups */
    API_ASSIGNED_TO_GROUP("API assigned to group"),

    /** Admin removes API from group */
    API_REMOVED_FROM_GROUP("API removed from group"),

    // ==================== Authentication and Security Actions ====================

    /**
     * User authenticates (success or failure).
     * Use AuditResult.SUCCESS / AuditResult.FAILURE to distinguish outcomes.
     */
    AUTH_LOGIN("User login attempt"),

    /** User explicitly logs out */
    AUTH_LOGOUT("User logged out"),

    /** User's JWT token is renewed */
    AUTH_TOKEN_REFRESH("Authentication token refreshed"),

    /**
     * User requests or completes a password reset.
     * Use AuditResult.SUCCESS for both the link-sent and password-changed stages.
     */
    AUTH_PASSWORD_RESET("Password reset"),

    // ==================== OAuth2 / Social Login Actions ====================

    /** User authenticated via OAuth2 (e.g. Google Sign-In) */
    AUTH_OAUTH2_LOGIN("OAuth2 login"),

    /** OAuth2 login attempt failed (invalid token, bad audience, etc.) */
    AUTH_OAUTH2_LOGIN_FAILED("OAuth2 login failed"),

    /** Existing LOCAL account linked to a Google OAuth2 identity */
    USER_LINKED_GOOGLE("User account linked to Google"),

    /** New user account created via OAuth2 social login */
    USER_CREATED_OAUTH2("User account created via OAuth2");

    private final String description;

    AuditAction(String description) {
        this.description = description;
    }

    /**
     * Returns a human-readable description of this action suitable for display in audit logs.
     *
     * @return the action description
     */
    public String getDescription() {
        return description;
    }
}
