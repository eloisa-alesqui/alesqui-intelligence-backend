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
    ROLE_BUSINESS("Business", "Business user access to assigned APIs"),

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
    ROLE_IT("IT", "API configuration, diagnostics, and technical management"),

    /**
     * Global super administrator with unrestricted access.
     * Bypasses group-based API visibility checks.
     */
    ROLE_SUPERADMIN("Super Admin", "Full system access, user management, and configuration"),

    /**
     * Trial user with limited access.
     * Used for evaluating the system before full activation.
     */
    ROLE_TRIAL("Trial", "Limited trial access to the system");

    private final String label;
    private final String description;

    Role(String label, String description) {
        this.label = label;
        this.description = description;
    }

    /**
     * Gets the human-readable label for this role.
     * @return the display label (e.g., "Business", "IT", "Super Admin", "Trial")
     */
    public String getLabel() {
        return label;
    }

    /**
     * Gets the description of this role's capabilities.
     * @return the role description
     */
    public String getDescription() {
        return description;
    }
}