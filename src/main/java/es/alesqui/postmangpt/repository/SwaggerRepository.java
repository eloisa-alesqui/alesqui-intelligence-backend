package es.alesqui.postmangpt.repository;

import es.alesqui.postmangpt.model.swagger.SwaggerDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Reactive repository interface for managing SwaggerDocument entities in a MongoDB collection.
 *
 * Inherits basic CRUD operations from ReactiveMongoRepository, including:
 * - save(document): Saves or updates a SwaggerDocument
 * - findById(id): Retrieves a document by its ID
 * - findAll(): Retrieves all documents
 * - deleteById(id): Deletes a document by its ID
 * - count(): Returns the total number of documents
 * - existsById(id): Checks existence of a document by its ID
 *
 * Includes custom query methods for domain-specific access patterns.
 */
@Repository
public interface SwaggerRepository extends ReactiveMongoRepository<SwaggerDocument, String> {

    /**
     * Finds a SwaggerDocument by its name field.
     *
     * @param name the name of the SwaggerDocument
     * @return a Mono emitting the matching SwaggerDocument, or empty if not found
     */
    Mono<SwaggerDocument> findByName(String name);
}
