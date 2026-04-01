package es.alesqui.intelligence.validation;

/**
 * Password validation constants and patterns.
 * 
 * Ensures password complexity requirements are consistent across the application
 * and match the frontend validation rules.
 */
public class PasswordValidator {
    
    /**
     * Password regex pattern:
     * - At least 8 characters
     * - At least one uppercase letter (A-Z)
     * - At least one lowercase letter (a-z)
     * - At least one number (0-9)
     * - At least one special character (@$!%*?&#^()_+=\-[\]{};:'",.<>/\\|~`)
     */
    public static final String PASSWORD_PATTERN = 
        "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#^()_+=\\-\\[\\]{};:'\",.<>/\\\\|~`])[A-Za-z\\d@$!%*?&#^()_+=\\-\\[\\]{};:'\",.<>/\\\\|~`]{8,}$";
    
    /**
     * Human-readable password requirements message.
     */
    public static final String PASSWORD_REQUIREMENTS_MESSAGE = 
        "Password must be at least 8 characters long and contain at least one uppercase letter, " +
        "one lowercase letter, one number, and one special character (@$!%*?&#^()_+=-[]{}; :'\",.<>/\\|~`)";
    
    private PasswordValidator() {
        // Utility class, prevent instantiation
    }
}
