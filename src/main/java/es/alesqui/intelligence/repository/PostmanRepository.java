package es.alesqui.intelligence.repository;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

import es.alesqui.intelligence.model.api_spec.postman.PostmanDocument;
import reactor.core.publisher.Mono;

/**
 * Reactive repository interface for managing PostmanDocument entities in a MongoDB collection.
 *
 * Inherits basic CRUD operations from ReactiveMongoRepository, including:
 * - save(document): Saves or updates a PostmanDocument
 * - findById(id): Retrieves a document by its ID
 * - findAll(): Retrieves all documents
 * - deleteById(id): Deletes a document by its ID
 * - count(): Returns the total number of documents
 * - existsById(id): Checks existence of a document by its ID
 *
 * Includes custom query methods for domain-specific access patterns.
 */
@Repository
public interface PostmanRepository extends ReactiveMongoRepository<PostmanDocument, String> {

    /**
     * Finds a PostmanDocument by its name field.
     *
     * @param name the name of the PostmanDocument
     * @return a Mono emitting the matching PostmanDocument, or empty if not found
     */
    Mono<PostmanDocument> findByName(String name);
    
    /**
     * Deletes a PostmanDocument by its unique name.
     *
     * @param name the name of the PostmanDocument to delete
     * @return a Mono that completes when the document is deleted
     */
    Mono<Void> deleteByName(String name);
}
