package es.alesqui.intelligence.service;

import java.security.SecureRandom;
import java.util.Set;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import es.alesqui.intelligence.config.DeploymentConfig;
import es.alesqui.intelligence.config.InitialAdminProperties;
import es.alesqui.intelligence.dto.admin.CreateUserRequest;
import es.alesqui.intelligence.model.core.User;
import es.alesqui.intelligence.model.core.enums.Role;
import es.alesqui.intelligence.repository.UserRepository;
import es.alesqui.intelligence.service.access.UserManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Service responsible for creating the initial admin user on first startup.
 * 
 * Purpose:
 * - Automatically creates an admin user if none exists in CORPORATE mode
 * - Generates secure random passwords if not provided
 * - Logs credentials clearly for first-time setup
 * 
 * Behavior:
 * - Only runs in CORPORATE deployment mode
 * - Only executes if database has zero users
 * - Runs once on ApplicationReadyEvent
 * - Blocks startup until complete to ensure admin exists
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InitialAdminSetupService {
    
    private final UserRepository userRepository;
    private final UserManagementService userManagementService;
    private final DeploymentConfig deploymentConfig;
    private final InitialAdminProperties initialAdminProperties;
    
    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final String SPECIAL_CHARS = "!@#$%^&*";
    private static final int PASSWORD_LENGTH = 16;
    
    /**
     * Creates initial admin user on application startup if needed.
     * Only runs in CORPORATE mode when no users exist.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void createInitialAdminIfNeeded() {
        // Only run in CORPORATE mode
        if (!deploymentConfig.isCorporate()) {
            log.debug("[InitialAdmin] Skipping initial admin creation - not in CORPORATE mode");
            return;
        }
        
        log.info("[InitialAdmin] Checking if initial admin user needs to be created...");
        
        // Check if any users exist and create admin if needed
        // Block to ensure admin creation completes before application fully starts
        try {
            userRepository.count()
                .flatMap(count -> {
                    if (count > 0) {
                        log.info("[InitialAdmin] Users already exist (count: {}), skipping initial admin creation", count);
                        return Mono.empty();
                    }
                    
                    log.info("[InitialAdmin] No users found in database, creating initial admin user");
                    return createInitialAdmin();
                })
                .doOnError(error -> log.error("[InitialAdmin] Error during initial admin setup", error))
                .block(java.time.Duration.ofSeconds(30));  // Block with timeout to prevent indefinite hanging
        } catch (Exception e) {
            log.error("[InitialAdmin] Failed to complete initial admin setup within timeout period", e);
            log.error("[InitialAdmin] Application will start without initial admin user");
        }
    }
    
    /**
     * Creates the initial admin user with configured or generated credentials.
     */
    private Mono<Void> createInitialAdmin() {
        String emailRaw = initialAdminProperties.getEmail();
        
        // Log warning if using default email (likely not configured)
        // Note: getEmail() already provides a fallback if null/empty
        if (emailRaw.equals("admin@company.com")) {
            log.warn("[InitialAdmin] ========================================");
            log.warn("[InitialAdmin] USING DEFAULT ADMIN EMAIL");
            log.warn("[InitialAdmin] ========================================");
            log.warn("[InitialAdmin] Initial admin email may not be configured!");
            log.warn("[InitialAdmin] To customize, set INITIAL_ADMIN_EMAIL environment variable");
            log.warn("[InitialAdmin] Example: INITIAL_ADMIN_EMAIL=admin@mycompany.com");
            log.warn("[InitialAdmin] Currently using: admin@company.com");
            log.warn("[InitialAdmin] ========================================");
        }
        
        final String email = emailRaw.trim().toLowerCase();
        log.debug("[InitialAdmin] Creating admin user with email: {}", email);
        
        String password = initialAdminProperties.getPassword();
        
        // Generate password if not provided
        boolean passwordGenerated = false;
        if (password == null || password.trim().isEmpty()) {
            password = generateSecurePassword();
            passwordGenerated = true;
            log.info("[InitialAdmin] Auto-generating secure password for admin user");
        }
        
        // Create user request
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername(email);
        request.setPassword(password);
        request.setRoles(Set.of(Role.ROLE_SUPERADMIN));
        
        final String finalPassword = password;
        final boolean wasGenerated = passwordGenerated;
        
        // Create user (without HTTP request context since this is startup)
        // Pass callback that ignores the user parameter since we don't need trial workspace
        return userManagementService.createUser(request, _ignoredUser -> Mono.empty())
            .doOnSuccess(user -> {
                log.info("[InitialAdmin] ========================================");
                log.info("[InitialAdmin] INITIAL ADMIN USER CREATED SUCCESSFULLY");
                log.info("[InitialAdmin] ========================================");
                log.info("[InitialAdmin] ");
                log.warn("[InitialAdmin] ⚠️  SECURITY NOTICE: Credentials are displayed below for initial setup ONLY");
                log.warn("[InitialAdmin] ⚠️  These logs may be persisted - ensure they are secured appropriately");
                log.info("[InitialAdmin] ");
                log.info("[InitialAdmin] Login Credentials:");
                log.info("[InitialAdmin]   Email:    {}", email);
                log.info("[InitialAdmin]   Password: {}", finalPassword);
                log.info("[InitialAdmin] ");
                if (wasGenerated) {
                    log.info("[InitialAdmin] ⚠️  Password was auto-generated");
                }
                log.info("[InitialAdmin] 🔒 IMPORTANT: Change this password after first login!");
                log.info("[InitialAdmin] ");
                log.info("[InitialAdmin] Access the application at: {}", initialAdminProperties.getAppUrl());
                log.info("[InitialAdmin] ========================================");
            })
            .doOnError(error -> {
                log.error("[InitialAdmin] ========================================");
                log.error("[InitialAdmin] FAILED TO CREATE INITIAL ADMIN USER");
                log.error("[InitialAdmin] ========================================");
                log.error("[InitialAdmin] Error: {}", error.getMessage());
                log.error("[InitialAdmin] Please create an admin user manually");
                log.error("[InitialAdmin] ========================================");
            })
            .then();
    }
    
    /**
     * Generates a secure random password.
     * 
     * Password format:
     * - 16 characters total
     * - Mix of uppercase, lowercase, numbers
     * - At least one special character
     * - Cryptographically secure random generation
     * 
     * @return secure random password
     */
    private String generateSecurePassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder(PASSWORD_LENGTH);
        
        // Ensure at least one special character
        password.append(SPECIAL_CHARS.charAt(random.nextInt(SPECIAL_CHARS.length())));
        
        // Fill rest with alphanumeric
        for (int i = 1; i < PASSWORD_LENGTH; i++) {
            password.append(ALPHANUMERIC.charAt(random.nextInt(ALPHANUMERIC.length())));
        }
        
        // Shuffle the password to randomize special char position
        char[] passwordArray = password.toString().toCharArray();
        for (int i = passwordArray.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char temp = passwordArray[i];
            passwordArray[i] = passwordArray[j];
            passwordArray[j] = temp;
        }
        
        return new String(passwordArray);
    }
}
