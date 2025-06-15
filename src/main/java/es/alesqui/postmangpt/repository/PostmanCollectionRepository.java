package es.alesqui.postmangpt.repository;

import es.alesqui.postmangpt.model.Collection;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing Postman Collections in MongoDB.
 * 
 * This repository provides CRUD operations and custom queries for Collection entities.
 * It extends MongoRepository to inherit basic MongoDB operations and adds custom methods
 * specific to PostmanGPT application requirements.
 * 
 * Based on the Postman Collection Format v2.1.0 specification.
 */
@Repository
public interface PostmanCollectionRepository extends MongoRepository<Collection, String> {

  // ========================================
  // BASIC CRUD OPERATIONS (Inherited from MongoRepository)
  // ========================================
  
  /**
   * Saves a Collection entity to the database.
   * If the entity has an ID, it will be updated; otherwise, a new document will be created.
   * 
   * @param collection the Collection to save
   * @return the saved Collection with generated ID if new
   */
  // save(Collection collection) - inherited
  
  /**
   * Finds a Collection by its unique identifier.
   * 
   * @param id the unique identifier of the collection
   * @return Optional containing the collection if found, empty otherwise
   */
  // findById(String id) - inherited
  
  /**
   * Retrieves all Collections from the database.
   * 
   * @return List of all Collections
   */
  // findAll() - inherited
  
  /**
   * Deletes a Collection by its unique identifier.
   * 
   * @param id the unique identifier of the collection to delete
   */
  // deleteById(String id) - inherited

  // ========================================
  // QUERIES BASED ON COLLECTION.INFO PROPERTIES
  // ========================================

  /**
   * Finds Collections by name from the Info object (case-insensitive partial match).
   * Searches within the collection's info.name field.
   * 
   * @param name the name or partial name to search for
   * @return List of collections matching the name criteria
   */
  @Query("{ 'info.name': { $regex: ?0, $options: 'i' } }")
  List<Collection> findByInfoNameContainingIgnoreCase(String name);

  /**
   * Finds a Collection by exact name from the Info object.
   * 
   * @param name the exact name to search for
   * @return Optional containing the collection with the exact name, or empty if not found
   */
  @Query("{ 'info.name': ?0 }")
  Optional<Collection> findByInfoName(String name);
  				   
  /**
   * Finds Collections by schema version.
   * Useful for filtering collections by Postman format version.
   * 
   * @param schema the schema version (e.g., "https://schema.getpostman.com/json/collection/v2.1.0/collection.json")
   * @return List of collections with the specified schema
   */
  @Query("{ 'info.schema': ?0 }")
  List<Collection> findByInfoSchema(String schema);
  
  /**
   * Finds Collections by description type (exact match).
   * Searches within the collection's info.description.type field for specific MIME types.
   * 
   * @param type the MIME type to search for (e.g., "text/markdown", "text/html", "application/json")
   * @return List of collections with description type matching the specified type
   */
  @Query("{ 'info.description.type': ?0 }")
  List<Collection> findByInfoDescriptionType(String type);

  // ========================================
  // QUERIES BASED ON COLLECTION STRUCTURE
  // ========================================

  /**
   * Finds Collections that contain items with specific names.
   * Searches through the collection's item array for matching names.
   * 
   * @param itemName the name of the item to search for
   * @return List of collections containing items with the specified name
   */
  @Query("{ 'item.name': { $regex: ?0, $options: 'i' } }")
  List<Collection> findByItemNameContainingIgnoreCase(String itemName);

  /**
   * Finds Collections that have requests with specific HTTP methods.
   * Searches through collection items for requests with the specified method.
   * 
   * @param method the HTTP method (GET, POST, PUT, DELETE, etc.)
   * @return List of collections containing requests with the specified method
   */
  @Query("{ 'item.request.method': ?0 }")
  List<Collection> findByRequestMethod(String method);

  /**
   * Finds Collections that contain requests to specific URLs or URL patterns.
   * Searches through collection items for requests with matching URLs.
   * 
   * @param urlPattern the URL pattern to search for
   * @return List of collections containing requests with matching URLs
   */
  @Query("{ 'item.request.url.raw': { $regex: ?0, $options: 'i' } }")
  List<Collection> findByRequestUrlContaining(String urlPattern);

  /**
   * Finds Collections that have variables with specific keys.
   * Searches through the collection's variable array.
   * 
   * @param variableKey the variable key to search for
   * @return List of collections containing the specified variable
   */
  @Query("{ 'variable.key': ?0 }")
  List<Collection> findByVariableKey(String variableKey);

  // ========================================
  // ADVANCED SEARCH AND FILTERING
  // ========================================
  
  /**
   * Finds Collections by keyword in description content (case-insensitive).
   * Searches within the collection's info.description.content field using MongoDB regex.
   * 
   * @param content the content to search for in description
   * @return List of collections with description content containing the specified text
   */
  @Query("{ 'info.description.content': { $regex: ?0, $options: 'i' } }")
  List<Collection> findByInfoDescriptionContentContainingIgnoreCase(String content);

  /**
   * Comprehensive search across multiple fields in the collection.
   * Searches in name, description, item names, and request URLs.
   * 
   * @param searchTerm the term to search for
   * @return List of collections matching the search criteria
   */
  @Query("{ $or: [ " +
         "{ 'info.name': { $regex: ?0, $options: 'i' } }, " +
         "{ 'info.description.content': { $regex: ?0, $options: 'i' } }, " +
         "{ 'item.name': { $regex: ?0, $options: 'i' } }, " +
         "{ 'item.request.url.raw': { $regex: ?0, $options: 'i' } } " +
         "] }")
  List<Collection> searchCollections(String searchTerm);

  /**
   * Finds Collections by multiple criteria with AND logic.
   * Combines name and method filters.
   * 
   * @param namePattern pattern to match in collection name
   * @param method HTTP method to filter by
   * @return List of collections matching both criteria
   */
  @Query("{ $and: [ " +
         "{ 'info.name': { $regex: ?0, $options: 'i' } }, " +
         "{ 'item.request.method': ?1 } " +
         "] }")
  List<Collection> findByNamePatternAndMethod(String namePattern, String method);

  // ========================================
  // METADATA AND UTILITY QUERIES
  // ========================================

  /**
   * Counts Collections that contain requests with a specific HTTP method.
   * Useful for analytics and statistics.
   * 
   * @param method the HTTP method to count
   * @return count of collections containing the specified method
   */
  @Query(value = "{ 'item.request.method': ?0 }", count = true)
  long countByRequestMethod(String method);

  /**
   * Finds Collections that have authentication configured.
   * Searches for collections with auth objects in requests.
   * 
   * @return List of collections with authentication configured
   */
  @Query("{ 'item.request.auth': { $exists: true, $ne: null } }")
  List<Collection> findCollectionsWithAuth();

  /**
   * Finds Collections that contain pre-request or test scripts.
   * Searches for collections with event objects containing scripts.
   * 
   * @return List of collections containing scripts
   */
  @Query("{ $or: [ " +
         "{ 'item.event.script': { $exists: true } }, " +
         "{ 'event.script': { $exists: true } } " +
         "] }")
  List<Collection> findCollectionsWithScripts();

  /**
   * Finds Collections by the number of items they contain.
   * Uses aggregation to filter by item count.
   * 
   * @param minItems minimum number of items
   * @param maxItems maximum number of items
   * @return List of collections within the specified item count range
   */
  @Query("{ $expr: { $and: [ " +
         "{ $gte: [ { $size: '$item' }, ?0 ] }, " +
         "{ $lte: [ { $size: '$item' }, ?1 ] } " +
         "] } }")
  List<Collection> findByItemCountBetween(int minItems, int maxItems);

  // ========================================
  // POSTMAN-SPECIFIC QUERIES
  // ========================================

  /**
   * Finds Collections that use specific Postman features like GraphQL.
   * Searches for GraphQL body types in requests.
   * 
   * @return List of collections containing GraphQL requests
   */
  @Query("{ 'item.request.body.graphql': { $exists: true } }")
  List<Collection> findCollectionsWithGraphQL();

  /**
   * Finds Collections that have file uploads (formdata with files).
   * Searches for form-data body types with file parameters.
   * 
   * @return List of collections with file upload requests
   */
  @Query("{ 'item.request.body.formdata.type': 'file' }")
  List<Collection> findCollectionsWithFileUploads();

  /**
   * Finds Collections by protocol profile behavior settings.
   * Useful for filtering collections with specific behaviors.
   * 
   * @param behaviorKey the behavior key to search for
   * @return List of collections with the specified behavior
   */
  @Query("{ 'protocolProfileBehavior.?0': { $exists: true } }")
  List<Collection> findByProtocolProfileBehavior(String behaviorKey);

  // ========================================
  // VALIDATION AND EXISTENCE CHECKS
  // ========================================

  /**
   * Checks if a collection with the given name already exists.
   * Prevents duplicate collection names.
   * 
   * @param name the collection name to check
   * @return true if a collection with the name exists, false otherwise
   */
  @Query(value = "{ 'info.name': ?0 }", exists = true)
  boolean existsByInfoName(String name);

  /**
   * Finds the most recently saved collection (by ObjectId).
   * Useful for "continue with last collection" features.
   * 
   * @return Optional containing the most recent collection if any exists
   */
  Optional<Collection> findFirstByOrderByIdDesc();
}