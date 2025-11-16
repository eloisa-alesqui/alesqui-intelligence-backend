package es.alesqui.intelligence.service.identity;

import java.time.Duration;

import org.springframework.stereotype.Service;

import es.alesqui.intelligence.model.core.User;
import es.alesqui.intelligence.model.core.enums.Role;
import es.alesqui.intelligence.repository.UserRepository;
import es.alesqui.intelligence.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * Lightweight user facade for resolving the current authenticated user in a reactive context.
 * Encapsulates access to SecurityUtils and UserRepository to keep higher layers decoupled from persistence.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /**
     * Returns the username of the current authenticated principal, or empty if not available.
     */
    public Mono<String> getCurrentUsername() {
        return SecurityUtils.getCurrentUsername();
    }

    /**
     * Resolves the current authenticated user document from the database.
     */
    public Mono<User> getCurrentUser() {
        return getCurrentUsername()
                .flatMap(userRepository::findByUsername);
    }

    /**
     * Resolves the id of the current authenticated user, or emits empty if unauthenticated.
     */
    public Mono<String> getCurrentUserId() {
        return getCurrentUser().map(User::getId);
    }

    /**
     * Synchronous helper that blocks briefly to obtain the current user id.
     * Use only in contexts where reactive composition is not practical.
     * Returns null if no user is authenticated or timeout occurs.
     */
    public String getCurrentUserIdBlocking(Duration timeout) {
        return getCurrentUserId().block(timeout);
    }

    /**
     * Resolves the userId from a given username with a blocking call.
     * Use only in contexts where reactive composition is not practical (e.g., boundedElastic threads).
     * Returns null if user is not found or timeout occurs.
     */
    public String getUserIdByUsernameBlocking(String username, Duration timeout) {
        if (username == null || username.isEmpty()) {
            return null;
        }
        return userRepository.findByUsername(username)
                .map(User::getId)
                .block(timeout);
    }

    /**
     * Checks if the provided userId belongs to a SUPERADMIN user.
     * If userId is null or user not found, returns false.
     */
    public Mono<Boolean> isUserSuperAdmin(String userId) {
        if (userId == null) return Mono.just(false);
        return userRepository.findById(userId)
                .map(u -> u.getRoles() != null && u.getRoles().contains(Role.ROLE_SUPERADMIN))
                .defaultIfEmpty(false);
    }

    /**
     * Checks if current authenticated user has SUPERADMIN role.
     */
    public Mono<Boolean> isCurrentUserSuperAdmin() {
        return getCurrentUser().map(u -> u.getRoles() != null && u.getRoles().contains(Role.ROLE_SUPERADMIN))
                .defaultIfEmpty(false);
    }

    /** Blocking convenience for SUPERADMIN check. */
    public boolean isCurrentUserSuperAdminBlocking(Duration timeout) {
        return Boolean.TRUE.equals(isCurrentUserSuperAdmin().block(timeout));
    }
}
