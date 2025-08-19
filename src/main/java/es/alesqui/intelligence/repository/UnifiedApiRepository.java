package es.alesqui.intelligence.repository;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

import es.alesqui.intelligence.model.unified.UnifiedApiDocument;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Repository interface for UnifiedApiData entities.
 * Provides MongoDB data access operations for unified API data management.
 */
@Repository
public interface UnifiedApiRepository extends ReactiveMongoRepository<UnifiedApiDocument, ObjectId> {

    /**
     * Finds API documents by their name using case-insensitive exact match.
     *
     * @param name The API name to search for
     * @return Mono containing the UnifiedApiDocument if found
     */
    Mono<UnifiedApiDocument> findByNameIgnoreCase(String name);

    /**
     * Finds all API documents managed by a specific team.
     *
     * @param team The team name to filter by
     * @return Flux of API documents managed by the specified team
     */
    Flux<UnifiedApiDocument> findByTeam(String team);

    /**
     * Searches for API documents by name or description containing the search terms.
     * The search is case-insensitive.
     *
     * @param name The search term for name
     * @param description The search term for description
     * @return Flux of matching API documents
     */
    Flux<UnifiedApiDocument> findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
        String name, String description
    );

    /**
     * Finds API documents by their version.
     *
     * @param version The version to search for
     * @return Flux of API documents with the specified version
     */
    Flux<UnifiedApiDocument> findByVersion(String version);

    /**
     * Finds API documents that contain a specific tag.
     *
     * @param tag The tag to search for
     * @return Flux of API documents containing the specified tag
     */
    Flux<UnifiedApiDocument> findByTagsContaining(String tag);

    /**
     * Finds all active API documents.
     *
     * @return Flux of active API documents
     */
    Flux<UnifiedApiDocument> findByActiveTrue();

    /**
     * Finds all inactive API documents.
     *
     * @return Flux of inactive API documents
     */
    Flux<UnifiedApiDocument> findByActiveFalse();

    /**
     * Finds API documents that have been updated after a specific date.
     *
     * @param date The date to compare against
     * @return Flux of recently updated API documents
     */
    @Query("{ 'updatedAt': { $gte: ?0 } }")
    Flux<UnifiedApiDocument> findRecentlyUpdated(LocalDateTime date);

    /**
     * Finds API documents by base URL pattern.
     *
     * @param baseUrlPattern The pattern to match in base URLs
     * @return Flux of API documents matching the base URL pattern
     */
    @Query("{ 'baseUrl': { $regex: ?0, $options: 'i' } }")
    Flux<UnifiedApiDocument> findByBaseUrlPattern(String baseUrlPattern);

    /**
     * Counts API documents by team.
     *
     * @param team The team to count
     * @return Mono of the count of API documents managed by the team
     */
    Mono<Long> countByTeam(String team);

    /**
     * Counts all active API documents.
     *
     * @return Mono of the count of active API documents
     */
    Mono<Long> countByActiveTrue();

    /**
     * Checks if an API document with the given name already exists.
     *
     * @param name The API name to check
     * @return Mono of boolean indicating if an API with this name exists
     */
    Mono<Boolean> existsByNameIgnoreCase(String name);

    /**
     * Finds all distinct teams managing API documents.
     *
     * @return Flux of unique team names
     */
    @Query(value = "{}", fields = "{ 'team': 1 }")
    Flux<String> findDistinctTeams();

    /**
     * Finds API documents created by a specific user.
     *
     * @param createdBy The username of the creator
     * @return Flux of API documents created by the specified user
     */
    Flux<UnifiedApiDocument> findByCreatedBy(String createdBy);

    /**
     * Finds API documents last modified by a specific user.
     *
     * @param lastModifiedBy The username of the last modifier
     * @return Flux of API documents last modified by the specified user
     */
    Flux<UnifiedApiDocument> findByLastModifiedBy(String lastModifiedBy);
}
