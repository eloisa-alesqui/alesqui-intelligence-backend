package es.alesqui.intelligence.repository;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

import es.alesqui.intelligence.model.access.Group;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive Mongo repository for {@link Group} documents.
 * Provides non-blocking CRUD and query operations over user-defined access groups.
 */
@Repository
public interface GroupRepository extends ReactiveMongoRepository<Group, String> {

    /**
     * Finds a group by its unique code (case-insensitive exact match).
     * @param code Unique short code identifying the group.
     * @return Mono emitting the matching group or empty if not found.
     */
    Mono<Group> findByCodeIgnoreCase(String code);

    /**
     * Checks existence by case-insensitive code.
     * @param code Code to verify.
     * @return Mono emitting true if a group with that code exists.
     */
    Mono<Boolean> existsByCodeIgnoreCase(String code);
}
