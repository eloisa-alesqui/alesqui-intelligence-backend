package es.alesqui.intelligence.model.core.enums;

/**
 * Represents the user roles within the Alesqui Intelligence application.
 *
 * Roles are used by Spring Security to control access to different APIs
 * and features. This enum distinguishes between users who consume data
 * (Business) and users who manage the system's technical aspects (IT).
 */
public enum Role {

    /**
     * Represents a business user.
     *
     * Users with this role are typically non-technical. They interact with the
     * system primarily through the natural language chat to obtain data insights
     * and visualizations.
     *
     * Key characteristics:
     * - Access is restricted to the chat interface.
     * - They cannot view or modify API configurations.
     * - Technical details, like step-by-step reasoning, are hidden by default.
     */
    ROLE_BUSINESS,

    /**
     * Represents an Information Technology (IT) or system administrator user.
     *
     * Users with this role have privileged access to configure and manage the
     * application. They are responsible for setting up APIs and ensuring the
     * system runs correctly.
     *
     * Key characteristics:
     * - Full access to API configuration panels.
     * - Ability to view system diagnostics and support panels.
     * - Can view detailed step-by-step reasoning for debugging purposes.
     */
    ROLE_IT
}