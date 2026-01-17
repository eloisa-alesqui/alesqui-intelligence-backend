package es.alesqui.intelligence.model.audit;

/**
 * Enumeration of all auditable actions in the system.
 * 
 * This enum categorizes administrative operations into logical groups:
 * - User lifecycle operations (create, update, delete, activate)
 * - User credential and role management
 * - Group CRUD operations
 * - Group membership operations
 * - API access control operations
 * - Authentication and security events
 * 
 * Each action corresponds to a specific operation that should be logged
 * for compliance, security monitoring, or troubleshooting purposes.
 */
public enum AuditAction {
    
    // ==================== User Management Actions ====================
    
    /**
     * A new user account was created in the system.
     * Logged when: Admin creates a user via POST /api/admin/users
     */
    USER_CREATED,
    
    /**
     * An existing user's information was updated.
     * Logged when: Admin updates user details via PATCH /api/admin/users/{userId}
     */
    USER_UPDATED,
    
    /**
     * A user account was permanently deleted from the system.
     * Logged when: Admin deletes a user via DELETE /api/admin/users/{userId}
     */
    USER_DELETED,
    
    /**
     * A user account was activated after registration.
     * Logged when: User completes account activation with a valid token
     */
    USER_ACTIVATED,
    
    /**
     * A user account was deactivated (suspended).
     * Logged when: Admin deactivates a user account
     */
    USER_DEACTIVATED,
    
    /**
     * A user's password was changed.
     * Logged when: Admin or user changes password
     */
    USER_PASSWORD_CHANGED,
    
    /**
     * A user's role assignments were modified.
     * Logged when: Admin updates user roles via PATCH /api/admin/users/{userId}/roles
     */
    USER_ROLES_CHANGED,
    
    /**
     * A user was added to one or more groups.
     * Logged when: Admin assigns user to groups via POST /api/admin/users/{userId}/groups
     */
    USER_ASSIGNED_TO_GROUP,
    
    /**
     * A user was removed from a group.
     * Logged when: Admin removes user from group via DELETE /api/admin/users/{userId}/groups/{groupId}
     */
    USER_REMOVED_FROM_GROUP,
    
    // ==================== Group Management Actions ====================
    
    /**
     * A new group was created in the system.
     * Logged when: Admin creates a group via POST /api/admin/groups
     */
    GROUP_CREATED,
    
    /**
     * An existing group's information was updated.
     * Logged when: Admin updates group details via PATCH /api/admin/groups/{groupId}
     */
    GROUP_UPDATED,
    
    /**
     * A group was permanently deleted from the system.
     * Logged when: Admin deletes a group via DELETE /api/admin/groups/{groupId}
     */
    GROUP_DELETED,
    
    /**
     * Multiple users were assigned to a group.
     * Logged when: Admin assigns users to group via POST /api/admin/groups/{groupId}/users
     */
    GROUP_USERS_ASSIGNED,
    
    /**
     * A user was removed from a group.
     * Logged when: Admin removes user from group via DELETE /api/admin/groups/{groupId}/users/{userId}
     */
    GROUP_USER_REMOVED,
    
    /**
     * Multiple APIs were assigned to a group.
     * Logged when: Admin assigns APIs to group via POST /api/admin/groups/{groupId}/apis
     */
    GROUP_APIS_ASSIGNED,
    
    /**
     * An API was removed from a group.
     * Logged when: Admin removes API from group via DELETE /api/admin/groups/{groupId}/apis/{apiId}
     */
    GROUP_API_REMOVED,
    
    // ==================== API Management Actions ====================
    
    /**
     * A new API was registered in the system.
     * Logged when: User creates an API via unification service
     */
    API_CREATED,
    
    /**
     * An existing API's configuration was updated.
     * Logged when: User updates API settings
     */
    API_UPDATED,
    
    /**
     * An API was deleted from the system.
     * Logged when: User deletes an API
     */
    API_DELETED,
    
    /**
     * An API was assigned to one or more groups.
     * Logged when: Admin assigns API to groups
     */
    API_ASSIGNED_TO_GROUP,
    
    /**
     * An API was removed from a group.
     * Logged when: Admin removes API from group
     */
    API_REMOVED_FROM_GROUP,
    
    // ==================== Authentication and Security Actions ====================
    
    /**
     * A user attempted to log into the system.
     * Use AuditResult.SUCCESS for successful logins, AuditResult.FAILURE for failed attempts.
     * Logged when: User authenticates (success or failure)
     */
    AUTH_LOGIN,
    
    /**
     * A user logged out of the system.
     * Logged when: User explicitly logs out
     */
    AUTH_LOGOUT,
    
    /**
     * An authentication token was refreshed.
     * Logged when: User's JWT token is renewed
     */
    AUTH_TOKEN_REFRESH,
    
    /**
     * A password reset flow was initiated or completed.
     * Use AuditResult.SUCCESS for different stages:
     * - When reset link is sent (requested)
     * - When password is successfully changed (completed)
     * Use AuditResult.FAILURE when the operation fails.
     * Logged when: User requests or completes password reset
     */
    AUTH_PASSWORD_RESET
}
