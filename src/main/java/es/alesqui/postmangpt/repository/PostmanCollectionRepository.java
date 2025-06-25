package es.alesqui.postmangpt.repository;

import es.alesqui.postmangpt.model.Collection;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive Repository interface for managing Postman Collections in MongoDB.
 * 
 * This repository provides reactive CRUD operations and custom queries for
 * Collection entities. It extends ReactiveMongoRepository to inherit basic
 * reactive MongoDB operations and adds custom methods specific to PostmanGPT
 * application requirements.
 * 
 * Based on the Postman Collection Format v2.1.0 specification.
 */
@Repository
public interface PostmanCollectionRepository extends ReactiveMongoRepository<Collection, String> {

// ========================================
// BASIC REACTIVE CRUD OPERATIONS (Inherited from ReactiveMongoRepository)
// ========================================

	/**
	 * Saves a Collection entity to the database reactively. If the entity has an
	 * ID, it will be updated; otherwise, a new document will be created.
	 * 
	 * @param collection the Collection to save
	 * @return Mono<Collection> the saved Collection with generated ID if new
	 */
// save(Collection collection) -> Mono<Collection> - inherited

	/**
	 * Finds a Collection by its unique identifier reactively.
	 * 
	 * @param id the unique identifier of the collection
	 * @return Mono<Collection> containing the collection if found, empty otherwise
	 */
// findById(String id) -> Mono<Collection> - inherited

	/**
	 * Retrieves all Collections from the database reactively.
	 * 
	 * @return Flux<Collection> of all Collections
	 */
// findAll() -> Flux<Collection> - inherited

	/**
	 * Deletes a Collection by its unique identifier reactively.
	 * 
	 * @param id the unique identifier of the collection to delete
	 * @return Mono<Void> completion signal
	 */
// deleteById(String id) -> Mono<Void> - inherited

	/**
	 * Deletes all Collections reactively.
	 * 
	 * @return Mono<Void> completion signal
	 */
// deleteAll() -> Mono<Void> - inherited

	/**
	 * Counts all Collections reactively.
	 * 
	 * @return Mono<Long> count of collections
	 */
// count() -> Mono<Long> - inherited

// ========================================
// REACTIVE QUERIES BASED ON COLLECTION.INFO PROPERTIES
// ========================================

	/**
	 * Finds Collections by name from the Info object (case-insensitive partial
	 * match) reactively. Searches within the collection's info.name field.
	 * 
	 * @param name the name or partial name to search for
	 * @return Flux<Collection> of collections matching the name criteria
	 */
	@Query("{ 'info.name': { $regex: ?0, $options: 'i' } }")
	Flux<Collection> findByInfoNameContainingIgnoreCase(String name);

	/**
	 * Finds a Collection by exact name from the Info object reactively.
	 * 
	 * @param name the exact name to search for
	 * @return Mono<Collection> containing the collection with the exact name, or
	 *         empty if not found
	 */
	@Query("{ 'info.name': ?0 }")
	Mono<Collection> findByInfoName(String name);

	/**
	 * Finds Collections by schema version reactively. Useful for filtering
	 * collections by Postman format version.
	 * 
	 * @param schema the schema version (e.g.,
	 *               "https://schema.getpostman.com/json/collection/v2.1.0/collection.json")
	 * @return Flux<Collection> of collections with the specified schema
	 */
	@Query("{ 'info.schema': ?0 }")
	Flux<Collection> findByInfoSchema(String schema);

	/**
	 * Finds Collections by description type (exact match) reactively. Searches
	 * within the collection's info.description.type field for specific MIME types.
	 * 
	 * @param type the MIME type to search for (e.g., "text/markdown", "text/html",
	 *             "application/json")
	 * @return Flux<Collection> of collections with description type matching the
	 *         specified type
	 */
	@Query("{ 'info.description.type': ?0 }")
	Flux<Collection> findByInfoDescriptionType(String type);

// ========================================
// REACTIVE QUERIES BASED ON COLLECTION STRUCTURE
// ========================================

	/**
	 * Finds Collections that contain items with specific names reactively. Searches
	 * through the collection's item array for matching names.
	 * 
	 * @param itemName the name of the item to search for
	 * @return Flux<Collection> of collections containing items with the specified
	 *         name
	 */
	@Query("{ 'item.name': { $regex: ?0, $options: 'i' } }")
	Flux<Collection> findByItemNameContainingIgnoreCase(String itemName);

	/**
	 * Finds Collections that have requests with specific HTTP methods reactively.
	 * Searches through collection items for requests with the specified method.
	 * 
	 * @param method the HTTP method (GET, POST, PUT, DELETE, etc.)
	 * @return Flux<Collection> of collections containing requests with the
	 *         specified method
	 */
	@Query("{ 'item.request.method': ?0 }")
	Flux<Collection> findByRequestMethod(String method);

	/**
	 * Finds Collections that contain requests to specific URLs or URL patterns
	 * reactively. Searches through collection items for requests with matching
	 * URLs.
	 * 
	 * @param urlPattern the URL pattern to search for
	 * @return Flux<Collection> of collections containing requests with matching
	 *         URLs
	 */
	@Query("{ 'item.request.url.raw': { $regex: ?0, $options: 'i' } }")
	Flux<Collection> findByRequestUrlContaining(String urlPattern);

	/**
	 * Finds Collections that have variables with specific keys reactively. Searches
	 * through the collection's variable array.
	 * 
	 * @param variableKey the variable key to search for
	 * @return Flux<Collection> of collections containing the specified variable
	 */
	@Query("{ 'variable.key': ?0 }")
	Flux<Collection> findByVariableKey(String variableKey);

// ========================================
// REACTIVE ADVANCED SEARCH AND FILTERING
// ========================================

	/**
	 * Finds Collections by keyword in description content (case-insensitive)
	 * reactively. Searches within the collection's info.description.content field
	 * using MongoDB regex.
	 * 
	 * @param content the content to search for in description
	 * @return Flux<Collection> of collections with description content containing
	 *         the specified text
	 */
	@Query("{ 'info.description.content': { $regex: ?0, $options: 'i' } }")
	Flux<Collection> findByInfoDescriptionContentContainingIgnoreCase(String content);

	/**
	 * Comprehensive search across multiple fields in the collection reactively.
	 * Searches in name, description, item names, and request URLs.
	 * 
	 * @param searchTerm the term to search for
	 * @return Flux<Collection> of collections matching the search criteria
	 */
	@Query("{ $or: [ " + "{ 'info.name': { $regex: ?0, $options: 'i' } }, "
			+ "{ 'info.description.content': { $regex: ?0, $options: 'i' } }, "
			+ "{ 'item.name': { $regex: ?0, $options: 'i' } }, "
			+ "{ 'item.request.url.raw': { $regex: ?0, $options: 'i' } } " + "] }")
	Flux<Collection> searchCollections(String searchTerm);

	/**
	 * Finds Collections by multiple criteria with AND logic reactively. Combines
	 * name and method filters.
	 * 
	 * @param namePattern pattern to match in collection name
	 * @param method      HTTP method to filter by
	 * @return Flux<Collection> of collections matching both criteria
	 */
	@Query("{ $and: [ " + "{ 'info.name': { $regex: ?0, $options: 'i' } }, " + "{ 'item.request.method': ?1 } " + "] }")
	Flux<Collection> findByNamePatternAndMethod(String namePattern, String method);

// ========================================
// REACTIVE METADATA AND UTILITY QUERIES
// ========================================

	/**
	 * Counts Collections that contain requests with a specific HTTP method
	 * reactively. Useful for analytics and statistics.
	 * 
	 * @param method the HTTP method to count
	 * @return Mono<Long> count of collections containing the specified method
	 */
	@Query(value = "{ 'item.request.method': ?0 }", count = true)
	Mono<Long> countByRequestMethod(String method);

	/**
	 * Finds Collections that have authentication configured reactively. Searches
	 * for collections with auth objects in requests.
	 * 
	 * @return Flux<Collection> of collections with authentication configured
	 */
	@Query("{ 'item': { $elemMatch: { 'request.auth': { $exists: true, $ne: null } } } }")
	Flux<Collection> findCollectionsWithAuth();

	/**
	 * Finds Collections that contain pre-request or test scripts reactively.
	 * Searches for collections with event objects containing scripts.
	 * 
	 * @return Flux<Collection> of collections containing scripts
	 */
	@Query("{ $or: [ " + "{ 'item.event.script': { $exists: true } }, " + "{ 'event.script': { $exists: true } } "
			+ "] }")
	Flux<Collection> findCollectionsWithScripts();

	/**
	 * Finds Collections by the number of items they contain reactively. Uses
	 * aggregation to filter by item count.
	 * 
	 * @param minItems minimum number of items
	 * @param maxItems maximum number of items
	 * @return Flux<Collection> of collections within the specified item count range
	 */
	@Query("{ $expr: { $and: [ " + "{ $gte: [ { $size: '$item' }, ?0 ] }, " + "{ $lte: [ { $size: '$item' }, ?1 ] } "
			+ "] } }")
	Flux<Collection> findByItemCountBetween(int minItems, int maxItems);

// ========================================
// REACTIVE POSTMAN-SPECIFIC QUERIES
// ========================================

	/**
	 * Finds Collections that use specific Postman features like GraphQL reactively.
	 * Searches for GraphQL body types in requests.
	 * 
	 * @return Flux<Collection> of collections containing GraphQL requests
	 */
	@Query("{ 'item.request.body.graphql': { $exists: true } }")
	Flux<Collection> findCollectionsWithGraphQL();

	/**
	 * Finds Collections that have file uploads (formdata with files) reactively.
	 * Searches for form-data body types with file parameters.
	 * 
	 * @return Flux<Collection> of collections with file upload requests
	 */
	@Query("{ 'item.request.body.formdata.type': 'file' }")
	Flux<Collection> findCollectionsWithFileUploads();

	/**
	 * Finds Collections by protocol profile behavior settings reactively. Useful
	 * for filtering collections with specific behaviors.
	 * 
	 * @param behaviorKey the behavior key to search for
	 * @return Flux<Collection> of collections with the specified behavior
	 */
	@Query("{ 'protocolProfileBehavior.?0': { $exists: true } }")
	Flux<Collection> findByProtocolProfileBehavior(String behaviorKey);

// ========================================
// REACTIVE VALIDATION AND EXISTENCE CHECKS
// ========================================

	/**
	 * Checks if a collection with the given name already exists reactively.
	 * Prevents duplicate collection names.
	 * 
	 * @param name the collection name to check
	 * @return Mono<Boolean> true if a collection with the name exists, false
	 *         otherwise
	 */
	@Query(value = "{ 'info.name': ?0 }", exists = true)
	Mono<Boolean> existsByInfoName(String name);

	/**
	 * Finds the most recently saved collection (by ObjectId) reactively. Useful for
	 * "continue with last collection" features.
	 * 
	 * @return Mono<Collection> containing the most recent collection if any exists
	 */
	Mono<Collection> findFirstByOrderByIdDesc();
}