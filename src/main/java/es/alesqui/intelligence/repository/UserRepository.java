package es.alesqui.intelligence.repository;

import es.alesqui.intelligence.model.core.User;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

/**
 * Spring Data Reactive MongoDB repository for the {@link User} document.
 *
 * This interface provides a non-blocking, reactive API for all database
 * operations related to the User entity. By extending ReactiveMongoRepository,
 * it inherits methods like save, findById, findAll, etc., which return
 * reactive types (Mono and Flux).
 */
public interface UserRepository extends ReactiveMongoRepository<User, String> {

    /**
     * Finds a user by their unique username in a non-blocking manner.
     *
     * @param username The unique username to search for. Must not be null.
     * @return A {@link Mono} that emits the {@link User} if found, or completes
     * empty if the user does not exist.
     */
    Mono<User> findByUsername(String username);

    /**
     * Finds a user by their password reset token in a non-blocking manner.
     *
     * @param passwordResetToken The password reset token to search for. Must not be null.
     * @return A {@link Mono} that emits the {@link User} if found, or completes
     * empty if no user with that token exists.
     */
    Mono<User> findByPasswordResetToken(String passwordResetToken);

    /**
     * Finds a user by their activation token in a non-blocking manner.
     *
     * @param activationToken The activation token to search for. Must not be null.
     * @return A {@link Mono} that emits the {@link User} if found, or completes
     * empty if no user with that token exists.
     */
    Mono<User> findByActivationToken(String activationToken);
}