package es.alesqui.postmangpt.service;

import es.alesqui.postmangpt.config.properties.PostmanCollectionProperties;
import es.alesqui.postmangpt.dto.CollectionInfo;
import es.alesqui.postmangpt.dto.EndpointInfo;
import es.alesqui.postmangpt.helper.PostmanEndpointExtractor;
import es.alesqui.postmangpt.model.Collection;
import es.alesqui.postmangpt.model.Info;
import es.alesqui.postmangpt.repository.PostmanCollectionRepository;
import es.alesqui.postmangpt.util.SafeLogger;
import es.alesqui.postmangpt.util.ValidationChain;
import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Reactive service for managing Postman collections.
 * 
 * This class provides comprehensive functionality for handling Postman
 * collections, including basic CRUD operations, JSON file imports, endpoint
 * extraction, and statistics generation using reactive programming paradigms.
 * 
 * The service handles data validation, operation logging, and error management
 * in a consistent manner. It uses Spring Data MongoDB Reactive for persistence
 * and Jackson for JSON processing.
 * 
 * Main Features: Reactive Collection CRUD management Import from JSON files and
 * strings Endpoint extraction and analysis Collection statistics generation
 * Structure and content validation
 * 
 * Constraints: JSON files cannot exceed 10MB Duplicate collections by name are
 * not allowed Collections must have at least one item/request
 */

@Service
@RequiredArgsConstructor
public class PostmanCollectionService {

	private final PostmanCollectionRepository repository;
	private final ObjectMapper objectMapper;
	private final PostmanEndpointExtractor endpointExtractor;
	private final SafeLogger safeLogger;
	private final PostmanCollectionProperties properties;

	/**
	 * Retrieves all collections from the database reactively.
	 * 
	 * This method performs a non-blocking retrieval of all Postman collections
	 * stored in the MongoDB database using reactive streams. The operation is
	 * asynchronous and returns immediately with a Flux that will emit collections
	 * as they become available from the database.
	 * 
	 * Reactive Flow: 1. Initiates database query through repository.findAll() 2.
	 * Each emitted collection triggers safe debug logging with collection name 3.
	 * When all collections are retrieved, completion is logged safely 4. If no
	 * collections exist, the Flux completes empty (no error)
	 * 
	 * Error Handling: - Database connection errors will propagate through the Flux
	 * - Serialization errors from MongoDB will be emitted as errors - Logging
	 * errors are handled gracefully without affecting the stream - Null collections
	 * or missing info are handled defensively - Logging framework failures fall
	 * back to System.err output
	 * 
	 * Performance Considerations: - Uses reactive streams for memory-efficient
	 * processing - Collections are streamed as they're retrieved (not loaded all at
	 * once) - Suitable for large datasets as it doesn't block the calling thread -
	 * Debug logging only executes when DEBUG level is enabled - Defensive logging
	 * adds minimal overhead with Optional chaining
	 * 
	 * Usage Example: ```java service.getAllCollections() .subscribe( collection ->
	 * processCollection(collection), error -> handleError(error), () ->
	 * log.info("All collections processed") ); ```
	 * 
	 * @return Flux<Collection> A reactive stream that emits all collections from
	 *         the database. The Flux will: - Emit each collection as it's retrieved
	 *         from the database - Complete when all collections have been emitted -
	 *         Complete empty if no collections exist (not an error condition) -
	 *         Emit error if database operation fails - Never fail due to logging
	 *         errors (handled defensively)
	 * 
	 * @throws DataAccessException    if there's a database connectivity issue
	 * @throws SerializationException if collection data cannot be deserialized
	 */
	public Flux<Collection> getAllCollections() {
		return withOperationLogging(repository.findAll(), "Retrieve all collections");
	}

	/**
	 * Retrieves a collection by its unique identifier reactively.
	 * 
	 * This method performs a non-blocking lookup of a specific Postman collection
	 * from the MongoDB database using its unique identifier. The operation is
	 * asynchronous and returns immediately with a Mono that will emit the
	 * collection if found, or complete empty if no matching collection exists.
	 *
	 * Reactive Flow: 1. Initiates database query through repository.findById(id) 2.
	 * If collection is found, triggers safe debug logging with collection name 3.
	 * On completion (success or empty), logs the operation outcome safely 4.
	 * Returns Mono<Collection> that emits the found collection or completes empty
	 * 
	 * Error Handling: - Database connection errors will propagate through the Mono
	 * - Invalid ID format errors from MongoDB will be emitted as errors -
	 * Collection not found is NOT an error (Mono completes empty) - Logging errors
	 * are handled gracefully without affecting the stream - Null collections or
	 * missing info are handled defensively - Logging framework failures fall back
	 * to System.err output
	 * 
	 * Performance Considerations: - Uses reactive streams for non-blocking I/O
	 * operations - Single collection lookup with minimal memory footprint -
	 * Suitable for high-concurrency scenarios as it doesn't block threads - Debug
	 * logging only executes when DEBUG level is enabled - Defensive logging adds
	 * minimal overhead with Optional chaining
	 * 
	 * Usage Examples: ```java // Handle found collection
	 * service.getCollectionById("12345") .subscribe( collection ->
	 * processCollection(collection), error -> handleError(error) );
	 * 
	 * // Handle both found and not found cases service.getCollectionById("12345")
	 * .switchIfEmpty(Mono.fromRunnable(() -> log.info("Collection not found")))
	 * .subscribe(collection -> processCollection(collection)); ```
	 * 
	 * @param id The unique identifier of the collection to retrieve. Must not be
	 *           null or empty. MongoDB ObjectId format recommended but not strictly
	 *           required (depends on repository implementation).
	 * 
	 * @return Mono<Collection> A reactive stream that will: - Emit the collection
	 *         if found in the database - Complete empty if no collection exists
	 *         with the given ID - Emit error if database operation fails or ID
	 *         format is invalid - Never fail due to logging errors (handled
	 *         defensively)
	 * 
	 * @throws IllegalArgumentException           if the provided ID is null or
	 *                                            empty
	 * @throws DataAccessException                if there's a database connectivity
	 *                                            issue
	 * @throws InvalidDataAccessApiUsageException if ID format is invalid for
	 *                                            MongoDB
	 */
	public Mono<Collection> getCollectionById(String id) {
		return withOperationLogging(repository.findById(id)
				.doOnNext(collection -> safeLogger.logCollectionInfo("Retrieved by ID", collection))
				.doOnSuccess(collection -> safeLogger.logLookupResult("Collection lookup by ID", id, collection)),
				"Retrieve collection by ID", id);
	}

	/**
	 * Retrieves a collection by its name reactively.
	 * 
	 * This method performs a non-blocking lookup of a specific Postman collection
	 * from the MongoDB database using its name field. The operation searches for
	 * collections where the nested info.name field matches the provided name
	 * exactly. The method is asynchronous and returns immediately with a Mono that
	 * will emit the collection if found, or complete empty if no matching
	 * collection exists.
	 * 
	 * Reactive Flow: 1. Initiates database query through
	 * repository.findByInfoName(name) 2. If collection is found, triggers safe
	 * debug logging with collection name 3. On completion (success or empty), logs
	 * the operation outcome safely 4. Returns Mono<Collection> that emits the found
	 * collection or completes empty
	 * 
	 * Search Behavior: - Performs exact name matching (case-sensitive by default) -
	 * Searches the nested info.name field within collection documents - Returns the
	 * first matching collection if multiple exist with same name - Database
	 * indexing on info.name field recommended for performance
	 * 
	 * Error Handling: - Database connection errors will propagate through the Mono
	 * - Query execution errors from MongoDB will be emitted as errors - Collection
	 * not found is NOT an error (Mono completes empty) - Logging errors are handled
	 * gracefully without affecting the stream - Null or empty name parameters are
	 * handled by repository layer - Logging framework failures fall back to
	 * System.err output
	 * 
	 * Performance Considerations: - Uses reactive streams for non-blocking I/O
	 * operations - Single collection lookup with minimal memory footprint -
	 * Performance depends on database indexing on info.name field - Suitable for
	 * high-concurrency scenarios as it doesn't block threads - Debug logging only
	 * executes when DEBUG level is enabled - Defensive logging adds minimal
	 * overhead with try-catch blocks
	 * 
	 * Usage Examples: ```java // Handle found collection
	 * service.getCollectionByName("User Management API") .subscribe( collection ->
	 * processCollection(collection), error -> handleError(error) );
	 * 
	 * // Handle both found and not found cases service.getCollectionByName("Payment
	 * API") .switchIfEmpty(Mono.fromRunnable(() -> log.info("Collection '{}' not
	 * found", name))) .subscribe(collection -> processCollection(collection));
	 * 
	 * // Chain with other operations service.getCollectionByName("Orders API")
	 * .flatMap(collection -> processCollectionRequests(collection))
	 * .subscribe(result -> handleResult(result)); ```
	 * 
	 * @param name The name of the collection to search for. This should match the
	 *             info.name field within the collection document exactly. Null or
	 *             empty names may result in empty Mono or repository errors
	 *             depending on implementation. Case-sensitive matching is typically
	 *             performed unless configured otherwise.
	 * 
	 * @return Mono<Collection> A reactive stream that will: - Emit the first
	 *         collection found with matching name - Complete empty if no collection
	 *         exists with the given name - Emit error if database operation fails
	 *         or query is malformed - Never fail due to logging errors (handled
	 *         defensively) - Return deterministic results for duplicate names
	 *         (first found)
	 * 
	 * @throws IllegalArgumentException           if the provided name is null
	 *                                            (repository dependent)
	 * @throws DataAccessException                if there's a database connectivity
	 *                                            issue
	 * @throws InvalidDataAccessApiUsageException if query format is invalid
	 */
	public Mono<Collection> getCollectionByName(String name) {

		return withOperationLogging(repository.findByInfoName(name)
				.doOnNext(collection -> safeLogger.logCollectionInfo("Retrieved by name", collection))
				.doOnSuccess(collection -> safeLogger.logLookupResult("Collection lookup by name", name, collection)),
				"Retrieve collection by name", name);
	}

	/**
	 * Saves a collection to the database reactively.
	 * 
	 * This method performs a non-blocking save operation for a Postman collection
	 * to the MongoDB database. The operation handles both insert (new collection)
	 * and update (existing collection) scenarios based on the presence of an ID.
	 * The method is asynchronous and returns immediately with a Mono that will emit
	 * the saved collection with any generated or updated fields.
	 * 
	 * Validation Rules: - Collection object must not be null - Collection.info
	 * field must not be null (required for logging and business logic) -
	 * Collection.info.name is recommended but not strictly required - Other fields
	 * are validated by MongoDB constraints during save
	 * 
	 * Save Behavior: - New Collection: If ID is null/empty, MongoDB generates a new
	 * ObjectId - Existing Collection: If ID exists, performs upsert operation -
	 * Optimistic locking: Uses version field if present in collection - Atomic
	 * operation: Save is performed as a single database transaction - Index
	 * updates: Triggers any configured index updates automatically
	 * 
	 * Reactive Flow: 1. Validates input parameters synchronously (fail-fast
	 * approach) 2. Logs save operation initiation with collection name 3. Initiates
	 * database save through repository.save(collection) 4. On successful save, logs
	 * success with saved collection details 5. On error, logs error details without
	 * exposing sensitive information 6. Returns Mono<Collection> with the saved
	 * collection or error
	 * 
	 * Error Handling: - Input validation errors are emitted immediately as
	 * IllegalArgumentException - Database constraint violations will propagate
	 * through the Mono - Duplicate key errors from MongoDB will be emitted as
	 * DataIntegrityViolationException - Connection errors will be emitted as
	 * DataAccessException - Logging errors are handled gracefully without affecting
	 * the save operation - Sensitive data is not exposed in error logs
	 * 
	 * Performance Considerations: - Uses reactive streams for non-blocking I/O
	 * operations - Single document save with minimal memory footprint - Suitable
	 * for high-concurrency scenarios as it doesn't block threads - Validation is
	 * performed synchronously for fail-fast behavior - Debug logging only executes
	 * when appropriate levels are enabled - Defensive logging adds minimal overhead
	 * with try-catch blocks
	 * 
	 * Usage Examples: ```java // Save new collection Collection newCollection = new
	 * Collection(); newCollection.setInfo(new Info("My API Collection"));
	 * service.saveCollection(newCollection) .subscribe( saved -> log.info("New
	 * collection saved with ID: {}", saved.getId()), error ->
	 * handleSaveError(error) );
	 * 
	 * // Update existing collection service.getCollectionById("12345")
	 * .map(collection -> { collection.getInfo().setName("Updated Name"); return
	 * collection; }) .flatMap(service::saveCollection) .subscribe(updated ->
	 * processUpdatedCollection(updated));
	 * 
	 * // Handle validation errors service.saveCollection(null)
	 * .doOnError(IllegalArgumentException.class, error -> log.warn("Invalid
	 * collection provided: {}", error.getMessage()))
	 * .onErrorReturn(defaultCollection) .subscribe(collection ->
	 * processCollection(collection)); ```
	 * 
	 * @param collection The collection to save to the database. Must not be null
	 *                   and must have a non-null info field. If the collection has
	 *                   no ID, a new document will be created with a generated
	 *                   ObjectId. If an ID exists, the existing document will be
	 *                   updated (upsert behavior).
	 * 
	 * @return Mono<Collection> A reactive stream that will: - Emit the saved
	 *         collection with generated/updated fields - Include generated ID for
	 *         new collections - Include updated timestamp fields if configured -
	 *         Emit IllegalArgumentException for validation failures - Emit
	 *         DataAccessException for database operation failures - Emit
	 *         DataIntegrityViolationException for constraint violations - Never
	 *         fail due to logging errors (handled defensively)
	 * 
	 * @throws IllegalArgumentException          if collection is null or
	 *                                           collection.info is null
	 * @throws DataAccessException               if there's a database connectivity
	 *                                           issue
	 * @throws DataIntegrityViolationException   if database constraints are
	 *                                           violated
	 * @throws OptimisticLockingFailureException if version conflict occurs during
	 *                                           update
	 */
	public Mono<Collection> saveCollection(Collection collection) {
		// Fail-fast validation
		if (collection == null) {
			return Mono.error(new IllegalArgumentException("Collection cannot be null"));
		}
		if (collection.getInfo() == null) {
			return Mono.error(new IllegalArgumentException("Collection info cannot be null"));
		}

		return withOperationLogging(repository.save(collection), "Save collection", extractCollectionName(collection));
	}

	/**
	 * Deletes a collection by its unique identifier reactively.
	 * 
	 * This method performs a non-blocking delete operation for a Postman collection
	 * from the MongoDB database using its unique identifier. The operation is
	 * asynchronous and returns immediately with a Mono<Void> that will complete
	 * when the delete operation finishes, regardless of whether a collection was
	 * actually found and deleted.
	 * 
	 * Delete Behavior: - Idempotent Operation: Deleting a non-existent ID completes
	 * successfully - No existence check: Does not verify collection exists before
	 * deletion - Atomic operation: Delete is performed as a single database
	 * transaction - Cascade behavior: Does not cascade to related documents
	 * (repository dependent) - Index cleanup: MongoDB automatically removes index
	 * entries - Soft delete: Not implemented - performs hard delete from database
	 * 
	 * Reactive Flow: 1. Logs delete operation initiation with collection ID 2.
	 * Initiates database delete through repository.deleteById(id) 3. On successful
	 * completion, logs success confirmation with ID 4. On error, logs error details
	 * without exposing sensitive information 5. Returns Mono<Void> that completes
	 * on success or emits error on failure
	 * 
	 * Error Handling: - Invalid ID format errors from MongoDB will be emitted as
	 * errors - Database connection errors will propagate through the Mono -
	 * Collection not found is NOT an error (operation completes successfully) -
	 * Constraint violation errors will be emitted if deletion is restricted -
	 * Logging errors are handled gracefully without affecting the delete operation
	 * - Sensitive data is not exposed in error logs
	 * 
	 * Performance Considerations: - Uses reactive streams for non-blocking I/O
	 * operations - Single document delete with minimal memory footprint - Suitable
	 * for high-concurrency scenarios as it doesn't block threads - No existence
	 * check performed (optimized for performance) - Idempotent nature allows safe
	 * retry operations - Debug logging only executes when appropriate levels are
	 * enabled - Defensive logging adds minimal overhead with try-catch blocks
	 * 
	 * Usage Examples: ```java // Simple delete operation
	 * service.deleteCollection("12345") .subscribe( () -> log.info("Delete
	 * operation completed"), error -> handleDeleteError(error) );
	 * 
	 * // Delete with existence check service.getCollectionById("12345")
	 * .flatMap(collection -> service.deleteCollection(collection.getId()))
	 * .switchIfEmpty(Mono.fromRunnable(() -> log.info("Collection not found for
	 * deletion"))) .subscribe( () -> log.info("Collection deleted"), error ->
	 * log.error("Failed to delete collection", error) );
	 * 
	 * // Batch delete operations Flux.fromIterable(collectionIds)
	 * .flatMap(service::deleteCollection) .then() .subscribe(() -> log.info("All
	 * collections deleted"));
	 * 
	 * // Delete with confirmation service.deleteCollection("12345")
	 * .then(service.getCollectionById("12345")) .hasElement() .subscribe(exists ->
	 * { if (!exists) { log.info("Delete confirmed - collection no longer exists");
	 * } }); ```
	 * 
	 * @param id The unique identifier of the collection to delete. Must not be null
	 *           or empty. MongoDB ObjectId format is recommended but not strictly
	 *           required (depends on repository implementation). Invalid ID formats
	 *           may result in repository errors.
	 * 
	 * @return Mono<Void> A reactive stream that will: - Complete successfully when
	 *         delete operation finishes (even if no collection found) - Emit error
	 *         if database operation fails or ID format is invalid - Emit error if
	 *         database constraints prevent deletion - Never fail due to logging
	 *         errors (handled defensively) - Complete immediately for non-existent
	 *         collections (idempotent)
	 * 
	 * @throws IllegalArgumentException           if the provided ID is null or
	 *                                            empty (repository dependent)
	 * @throws DataAccessException                if there's a database connectivity
	 *                                            issue
	 * @throws InvalidDataAccessApiUsageException if ID format is invalid for
	 *                                            MongoDB
	 * @throws DataIntegrityViolationException    if foreign key constraints prevent
	 *                                            deletion
	 * 
	 * @see PostmanCollectionRepository#deleteById(Object)
	 * @see Mono#doOnSuccess(Consumer)
	 * @see Mono#doOnError(Consumer)
	 * @see #logDeleteInitiationSafely(String)
	 * @see #logDeleteSuccessSafely(String)
	 * @see #logDeleteErrorSafely(String, Throwable)
	 * 
	 * @since 1.0
	 */
	public Mono<Void> deleteCollection(String id) {
		return withOperationLogging(repository.deleteById(id).then(Mono.empty()), "Delete collection", id).then();
	}

	/**
	 * Imports a collection from a JSON file reactively.
	 * 
	 * This method performs a non-blocking import operation for a Postman collection
	 * from an uploaded JSON file. The operation includes comprehensive validation,
	 * duplicate name checking, and atomic save functionality. The method is
	 * asynchronous and returns immediately with a Mono that will emit the imported
	 * and saved collection with generated ID and metadata.
	 * 
	 * Import Process Flow: 1. File validation (size, type, content) 2. JSON parsing
	 * and deserialization to Collection object 3. Collection structure validation
	 * 4. Duplicate name existence check 5. Collection save operation with generated
	 * metadata 6. Success confirmation and logging
	 * 
	 * Validation Rules: - File must not be null and must not be empty - File must
	 * have valid JSON content type or .json extension - File size must be within
	 * configured limits - JSON must be valid and parseable to Collection structure
	 * - Collection must have valid info section with name - Collection name must
	 * not already exist in database - Collection structure must conform to Postman
	 * schema
	 * 
	 * Duplicate Handling: - Performs case-sensitive name comparison by default -
	 * Rejects import if collection with same name exists - Does not perform
	 * automatic name conflict resolution - Error is emitted for duplicate names
	 * (not overwrite) - Atomic operation: either imports successfully or fails
	 * completely
	 * 
	 * Reactive Flow: 1. Logs import operation initiation with filename 2. Wraps
	 * blocking file operations in Mono.fromCallable() 3. Validates file format and
	 * content synchronously 4. Parses JSON to Collection object using ObjectMapper
	 * 5. Validates parsed collection structure 6. Checks for existing collection
	 * with same name 7. Saves collection if no duplicates found 8. Logs success or
	 * error outcomes safely
	 * 
	 * Error Handling: - File validation errors are emitted as
	 * IllegalArgumentException - JSON parsing errors are emitted as
	 * JsonProcessingException - Collection validation errors are emitted as
	 * IllegalArgumentException - Duplicate name errors are emitted as
	 * IllegalArgumentException - Database errors propagate through the reactive
	 * stream - IO errors from file reading are emitted as IOException - Logging
	 * errors are handled gracefully without affecting import
	 * 
	 * Performance Considerations: - Uses Mono.fromCallable() to handle blocking
	 * file I/O operations - Single file processing with controlled memory usage -
	 * Streaming JSON parsing for large files (ObjectMapper configuration dependent)
	 * - Suitable for concurrent file uploads as operations don't block threads -
	 * File validation performed early to fail fast on invalid inputs - Defensive
	 * logging adds minimal overhead with try-catch blocks
	 * 
	 * Security Considerations: - File size limits prevent DoS attacks through large
	 * file uploads - JSON parsing limits prevent malicious payload processing -
	 * File type validation prevents non-JSON file processing - Error messages don't
	 * expose internal system details - Temporary file cleanup handled by Spring
	 * framework
	 * 
	 * Usage Examples: ```java // Handle file upload from web
	 * controller @PostMapping("/import") public Mono<ResponseEntity<Collection>>
	 * importCollection( @RequestParam("file") MultipartFile file) { return
	 * service.importCollectionFromFile(file) .map(collection ->
	 * ResponseEntity.ok(collection)) .onErrorReturn(IllegalArgumentException.class,
	 * ResponseEntity.badRequest().build()); }
	 * 
	 * // Import with custom error handling
	 * service.importCollectionFromFile(uploadedFile) .subscribe( imported ->
	 * log.info("Import successful: {}", imported.getId()), error -> { if (error
	 * instanceof IllegalArgumentException) { handleValidationError(error); } else {
	 * handleSystemError(error); } } );
	 * 
	 * // Batch import with error recovery Flux.fromIterable(files) .flatMap(file ->
	 * service.importCollectionFromFile(file) .onErrorResume(error -> {
	 * log.warn("Failed to import {}: {}", file.getOriginalFilename(),
	 * error.getMessage()); return Mono.empty(); })) .collectList()
	 * .subscribe(imported -> log.info("Imported {} collections", imported.size()));
	 * ```
	 * 
	 * @param file The multipart file containing the JSON collection data. Must not
	 *             be null, must not be empty, and must contain valid JSON that can
	 *             be deserialized to a Collection object. File should have
	 *             appropriate content type (application/json) or .json extension.
	 *             File size should be within configured limits.
	 * 
	 * @return Mono<Collection> A reactive stream that will: - Emit the imported
	 *         collection with generated ID and metadata - Include all parsed
	 *         collection data (requests, folders, etc.) - Emit
	 *         IllegalArgumentException for validation failures - Emit
	 *         JsonProcessingException for JSON parsing errors - Emit IOException
	 *         for file reading errors - Emit DataAccessException for database
	 *         operation failures - Never fail due to logging errors (handled
	 *         defensively)
	 * 
	 * @throws IllegalArgumentException        if file is null, empty, invalid
	 *                                         format, or contains invalid
	 *                                         collection data
	 * @throws JsonProcessingException         if JSON parsing fails or structure is
	 *                                         invalid
	 * @throws IOException                     if file reading operations fail
	 * @throws DataAccessException             if database operations fail
	 * @throws DataIntegrityViolationException if collection constraints are
	 *                                         violated
	 */
	public Mono<Collection> importCollectionFromFile(MultipartFile file) {
		safeLogger.logFileOperation("Import collection from file", file);

		return Mono.fromCallable(() -> {
			safeLogger.logValidation("file", file);
			validateFile(file);

			safeLogger.logOperationStart("Parse JSON from file", extractFilename(file));
			Collection collection = objectMapper.readValue(file.getInputStream(), Collection.class);

			safeLogger.logValidation("collection", collection);
			validateCollection(collection);

			return collection;
		}).flatMap(this::checkDuplicatesAndSave)
				.doOnNext(saved -> safeLogger.logOperationSuccess("Import from file",
						"Collection imported with ID: " + saved.getId()))
				.doOnError(error -> safeLogger.logOperationError("Import collection from file", error));
	}

	/**
	 * Imports a collection from a JSON string reactively.
	 * 
	 * This method performs a non-blocking import operation for a Postman collection
	 * from a JSON string. The operation includes comprehensive validation,
	 * duplicate name checking, and atomic save functionality. The method is
	 * asynchronous and returns immediately with a Mono that will emit the imported
	 * and saved collection with generated ID and metadata.
	 * 
	 * Import Process Flow: 1. JSON string validation (null, empty, format checks)
	 * 2. JSON parsing and deserialization to Collection object 3. Collection
	 * structure validation 4. Duplicate name existence check 5. Collection save
	 * operation with generated metadata 6. Success confirmation and logging
	 * 
	 * Validation Rules: - JSON string must not be null or empty - JSON must be
	 * valid and parseable to Collection structure - Collection must have valid info
	 * section with name - Collection name must not already exist in database -
	 * Collection structure must conform to Postman schema - JSON size should be
	 * reasonable to prevent memory issues
	 * 
	 * Duplicate Handling: - Performs case-sensitive name comparison by default -
	 * Rejects import if collection with same name exists - Does not perform
	 * automatic name conflict resolution - Error is emitted for duplicate names
	 * (not overwrite) - Atomic operation: either imports successfully or fails
	 * completely
	 * 
	 * Reactive Flow: 1. Logs import operation initiation with JSON size information
	 * 2. Wraps blocking JSON parsing in Mono.fromCallable() 3. Validates JSON
	 * string format and content 4. Parses JSON to Collection object using
	 * ObjectMapper 5. Validates parsed collection structure 6. Checks for existing
	 * collection with same name 7. Saves collection if no duplicates found 8. Logs
	 * success or error outcomes safely
	 * 
	 * Error Handling: - Null/empty JSON errors are emitted as
	 * IllegalArgumentException - JSON parsing errors are wrapped and emitted as
	 * RuntimeException - Collection validation errors are emitted as
	 * IllegalArgumentException - Duplicate name errors are emitted as
	 * IllegalArgumentException - Database errors propagate through the reactive
	 * stream - Original IOException details are preserved in wrapped exceptions -
	 * Logging errors are handled gracefully without affecting import
	 * 
	 * Performance Considerations: - Uses Mono.fromCallable() to handle blocking
	 * JSON parsing operations - Single JSON string processing with controlled
	 * memory usage - Streaming JSON parsing for large strings (ObjectMapper
	 * configuration dependent) - Suitable for concurrent JSON processing as
	 * operations don't block threads - JSON validation performed early to fail fast
	 * on invalid inputs - Defensive logging adds minimal overhead with try-catch
	 * blocks
	 * 
	 * Security Considerations: - JSON size limits prevent DoS attacks through large
	 * payload processing - JSON parsing limits prevent malicious payload processing
	 * - Error messages don't expose internal system details - Input sanitization
	 * through ObjectMapper configuration - Memory usage monitoring for large JSON
	 * strings
	 * 
	 * Usage Examples: ```java // Handle JSON from web
	 * request @PostMapping("/import-json") public Mono<ResponseEntity<Collection>>
	 * importFromJson(
	 * 
	 * @RequestBody String jsonContent) { return
	 *              service.importCollectionFromJson(jsonContent) .map(collection ->
	 *              ResponseEntity.ok(collection))
	 *              .onErrorReturn(IllegalArgumentException.class,
	 *              ResponseEntity.badRequest().build()); }
	 * 
	 *              // Import with custom error handling String postmanJson = "{
	 *              \"info\": { \"name\": \"My API\" }, ... }";
	 *              service.importCollectionFromJson(postmanJson) .subscribe(
	 *              imported -> log.info("Import successful: {}", imported.getId()),
	 *              error -> { if (error instanceof IllegalArgumentException) {
	 *              handleValidationError(error); } else if (error instanceof
	 *              RuntimeException) { handleJsonParsingError(error); } else {
	 *              handleSystemError(error); } } );
	 * 
	 *              // Batch import from multiple JSON strings
	 *              Flux.fromIterable(jsonStrings) .flatMap(json ->
	 *              service.importCollectionFromJson(json) .onErrorResume(error -> {
	 *              log.warn("Failed to import JSON: {}", error.getMessage());
	 *              return Mono.empty(); })) .collectList() .subscribe(imported ->
	 *              log.info("Imported {} collections", imported.size()));
	 * 
	 *              // Import with size validation if (jsonContent.length() >
	 *              MAX_JSON_SIZE) { return Mono.error(new
	 *              IllegalArgumentException("JSON too large")); } return
	 *              service.importCollectionFromJson(jsonContent); ```
	 * 
	 * @param jsonContent The JSON string containing the collection data. Must not
	 *                    be null or empty, and must contain valid JSON that can be
	 *                    deserialized to a Collection object. Should conform to
	 *                    Postman collection schema. Large JSON strings may impact
	 *                    memory usage and should be validated for size.
	 * 
	 * @return Mono<Collection> A reactive stream that will: - Emit the imported
	 *         collection with generated ID and metadata - Include all parsed
	 *         collection data (requests, folders, etc.) - Emit
	 *         IllegalArgumentException for validation failures - Emit
	 *         RuntimeException for JSON parsing errors (wrapping IOException) -
	 *         Emit DataAccessException for database operation failures - Never fail
	 *         due to logging errors (handled defensively)
	 * 
	 * @throws IllegalArgumentException        if JSON is null, empty, or contains
	 *                                         invalid collection data
	 * @throws RuntimeException                if JSON parsing fails or structure is
	 *                                         invalid (wraps IOException)
	 * @throws DataAccessException             if database operations fail
	 * @throws DataIntegrityViolationException if collection constraints are
	 *                                         violated
	 * 
	 * @see #validateCollection(Collection)
	 * @see #getCollectionByName(String)
	 * @see #saveCollection(Collection)
	 * @see ObjectMapper#readValue(String, Class)
	 * @see Mono#fromCallable(Callable)
	 * 
	 * @since 1.0
	 */
	public Mono<Collection> importCollectionFromJson(String jsonContent) {
		safeLogger.logJsonOperation("Import collection from JSON", jsonContent);

		return Mono.fromCallable(() -> {
			try {
				safeLogger.logValidation("json", jsonContent);
				validateJsonContent(jsonContent);

				safeLogger.logOperationStart("Parse JSON string");
				Collection collection = objectMapper.readValue(jsonContent, Collection.class);

				safeLogger.logValidation("collection", collection);
				validateCollection(collection);

				return collection;
			} catch (IOException e) {
				safeLogger.logOperationError("Parse JSON string", e);
				throw new RuntimeException("Error processing JSON: " + e.getMessage(), e);
			}
		}).flatMap(this::checkDuplicatesAndSave)
				.doOnNext(saved -> safeLogger.logOperationSuccess("Import from JSON",
						"Collection imported with ID: " + saved.getId()))
				.doOnError(error -> safeLogger.logOperationError("Import collection from JSON", error));
	}

	/**
	 * Extracts all endpoints from a collection using the dedicated helper
	 * reactively.
	 * 
	 * @param collectionId the unique identifier of the collection
	 * @return Mono of List of EndpointInfo objects representing all endpoints
	 */
	public Mono<List<EndpointInfo>> getEndpoints(String collectionId) {
		safeLogger.logOperationStart("Extract endpoints", collectionId);

		return getCollectionById(collectionId)
				.switchIfEmpty(
						Mono.error(new IllegalArgumentException("Collection not found with ID: " + collectionId)))
				.map(collection -> {
					List<EndpointInfo> endpoints = endpointExtractor.extractEndpoints(collection.getItem());
					safeLogger.logOperationSuccess("Extract endpoints",
							"Extracted " + endpoints.size() + " endpoints from " + extractCollectionName(collection));
					return endpoints;
				}).doOnError(error -> safeLogger.logOperationError("Extract endpoints", error));
	}

	/**
	 * Retrieves statistics for a specific collection reactively.
	 * 
	 * @param collectionId the unique identifier of the collection
	 * @return Mono of CollectionStats object containing collection statistics
	 */
	public Mono<CollectionInfo> getCollectionStats(String collectionId) {
		safeLogger.logOperationStart("Get collection stats", collectionId);

		return getCollectionById(collectionId)
				.switchIfEmpty(Mono.error(new IllegalArgumentException("Collection not found"))).map(collection -> {
					String collectionName = extractCollectionName(collection);
					int totalItems = collection.getItem() != null ? collection.getItem().size() : 0;

					CollectionInfo stats = CollectionInfo.builder().collectionName(collectionName)
							.totalItems(totalItems).description(getDescriptionSafely(collection)).build();

					safeLogger.logOperationSuccess("Get collection stats",
							"Generated stats for " + collectionName + " with " + totalItems + " items");

					return stats;
				}).doOnError(error -> safeLogger.logOperationError("Get collection stats", error));
	}

	/**
	 * Performs duplicate name validation and saves collection in a single atomic
	 * reactive operation.
	 * 
	 * This method implements a critical business rule that prevents duplicate
	 * collections with the same name from being stored in the database. It combines
	 * duplicate checking with collection persistence in a single reactive chain to
	 * ensure data consistency and provide immediate feedback on naming conflicts.
	 * 
	 * Reactive Flow: 1. Extracts collection name using safe extraction with
	 * fallback handling 2. Logs duplicate check initiation for audit trail 3.
	 * Queries database for existing collection with the same name 4. Converts query
	 * result to boolean existence check 5. Conditionally proceeds based on
	 * duplicate detection: - If duplicate exists: logs conflict and emits
	 * descriptive error - If no duplicate: logs save operation and proceeds with
	 * persistence 6. Returns saved collection with generated ID or error stream
	 * 
	 * Business Rules Enforced: - Collection names must be unique across the entire
	 * database - Name comparison is case-sensitive and exact match - Empty or null
	 * names are handled by fallback extraction - Duplicate detection occurs before
	 * any database write operations
	 * 
	 * Error Handling: - Emits IllegalArgumentException for duplicate name conflicts
	 * - Database errors during lookup propagate through reactive stream - Save
	 * operation errors are handled by the underlying saveCollection method -
	 * Logging errors are handled defensively without affecting business logic
	 * 
	 * Performance Considerations: - Single database query for duplicate detection -
	 * Non-blocking reactive operations suitable for high concurrency - Fail-fast
	 * approach minimizes unnecessary database writes - Efficient name-based
	 * indexing assumed for optimal query performance
	 * 
	 * @param collection The collection to validate and save. Must not be null and
	 *                   should have valid info structure with name. The collection
	 *                   will be saved with auto-generated ID if no duplicates
	 *                   exist.
	 * 
	 * @return Mono<Collection> A reactive stream that will: - Emit the saved
	 *         collection with generated ID if no duplicates - Include all original
	 *         collection data plus persistence metadata - Emit
	 *         IllegalArgumentException if duplicate name exists - Emit
	 *         DataAccessException for database operation failures - Never complete
	 *         empty (always emits result or error)
	 * 
	 * @throws IllegalArgumentException if collection with same name already exists
	 * @throws DataAccessException      if database operations fail
	 */
	private Mono<Collection> checkDuplicatesAndSave(Collection collection) {
		// Extract collection name safely with fallback for logging and lookup
		// This ensures consistent name handling across duplicate check and save
		// operations
		String collectionName = extractCollectionName(collection);

		return safeLogger.logOperationStartReactive("Check for duplicates", collectionName)
				// Query database for existing collection with same name
				.then(getCollectionByName(collectionName))

				// Convert Mono<Collection> to Mono<Boolean> for existence check
				// hasElement() returns true if collection exists, false if empty
				.hasElement()

				// Conditional logic based on duplicate detection result
				.flatMap(exists -> {
					if (exists) {
						// Log duplicate conflict for audit and debugging
						safeLogger.logDuplicateFound("collection", collectionName);

						// Emit descriptive error that can be handled by calling code
						return Mono.error(new IllegalArgumentException("Collection with this name already exists"));
					}

					// No duplicate found - proceed with save operation
					// Log save initiation and chain with actual persistence
					return safeLogger.logOperationStartReactive("Save imported collection", collectionName)
							.then(saveCollection(collection));
				});
	}

	/**
	 * Defensively extracts collection name with comprehensive null safety and
	 * fallback handling.
	 * 
	 * This utility method provides robust name extraction from Postman collection
	 * objects, handling the complex nested structure of collection metadata while
	 * ensuring that a valid string is always returned for logging, validation, and
	 * business operations.
	 * 
	 * Extraction Strategy: 1. Null-safe navigation through collection -> info ->
	 * name hierarchy 2. Validation that extracted name is not null or
	 * whitespace-only 3. Trimming and empty string filtering to ensure meaningful
	 * names 4. Fallback to descriptive default for missing or invalid names
	 * 
	 * Null Safety Features: - Handles null collection objects gracefully - Manages
	 * missing or null info structures - Processes null or empty name fields safely
	 * - Filters out whitespace-only names that appear valid but are meaningless
	 * 
	 * Business Context: - Collection names are used for duplicate detection and
	 * user identification - Fallback names ensure logging and error messages are
	 * always meaningful - Consistent name extraction prevents inconsistencies
	 * across operations - Supports defensive programming practices throughout the
	 * service
	 * 
	 * @param collection The Postman collection from which to extract the name. Can
	 *                   be null, have null info, or null/empty name fields. All
	 *                   null scenarios are handled gracefully.
	 * 
	 * @return String A guaranteed non-null, non-empty collection name: - The actual
	 *         collection name if present and valid - "Unnamed Collection" fallback
	 *         for any null/empty scenarios - Never returns null, empty string, or
	 *         whitespace-only string
	 */
	private String extractCollectionName(Collection collection) {
		return Optional.ofNullable(collection)
				// Navigate to info structure with null safety
				.map(Collection::getInfo)

				// Extract name field with null safety
				.map(Info::getName)

				// Filter out null and whitespace-only names
				// This ensures meaningful names only pass through
				.filter(name -> name != null && !name.trim().isEmpty())

				// Provide descriptive fallback for all failure scenarios
				.orElse("Unnamed Collection");
	}

	/**
	 * Defensively extracts filename from MultipartFile with comprehensive safety
	 * handling.
	 * 
	 * This utility method provides robust filename extraction from Spring's
	 * MultipartFile objects, ensuring that file operations always have a meaningful
	 * identifier for logging, validation, and error reporting, even when dealing
	 * with malformed uploads.
	 * 
	 * Extraction Strategy: 1. Null-safe access to MultipartFile object 2. Safe
	 * extraction of original filename from file metadata 3. Validation that
	 * filename is meaningful (not null or whitespace) 4. Fallback to descriptive
	 * identifier for problematic files
	 * 
	 * Safety Features: - Handles null MultipartFile objects without exceptions -
	 * Manages missing or null original filename metadata - Filters out empty or
	 * whitespace-only filenames - Provides consistent fallback for all edge cases
	 * 
	 * Business Context: - Filenames are used in logging for operation tracking -
	 * Error messages include filenames for user feedback - File validation
	 * processes require consistent naming - Audit trails depend on meaningful file
	 * identification
	 * 
	 * @param file The MultipartFile from which to extract the filename. Can be null
	 *             or have null/empty original filename. All problematic scenarios
	 *             are handled gracefully.
	 * 
	 * @return String A guaranteed non-null, non-empty filename: - The actual
	 *         original filename if present and valid - "unknown-file" fallback for
	 *         any null/empty scenarios - Never returns null, empty string, or
	 *         whitespace-only string
	 */
	private String extractFilename(MultipartFile file) {
		return Optional.ofNullable(file)
				// Extract original filename with null safety
				.map(MultipartFile::getOriginalFilename)

				// Filter out null and whitespace-only filenames
				// Ensures only meaningful filenames pass validation
				.filter(name -> name != null && !name.trim().isEmpty())

				// Provide descriptive fallback for problematic files
				.orElse("unknown-file");
	}

	/**
	 * Validates collection structure and content against business rules and data
	 * integrity requirements.
	 * 
	 * This method enforces critical business rules that ensure imported collections
	 * meet minimum structural requirements for successful processing and storage.
	 * It performs comprehensive validation of the collection hierarchy and
	 * essential metadata fields.
	 * 
	 * Validation Rules Enforced: 1. Collection object must exist (not null) 2. Info
	 * structure must be present for metadata storage 3. Collection name must be
	 * provided and meaningful 4. At least one item/request must exist for
	 * functional collections
	 * 
	 * Business Justification: - Null collections cannot be processed or stored
	 * meaningfully - Info structure contains essential metadata for collection
	 * management - Names are required for duplicate detection and user
	 * identification - Empty collections provide no functional value and may
	 * indicate import errors
	 * 
	 * Validation Strategy: - Fail-fast approach with immediate exception throwing -
	 * Descriptive error messages for each specific validation failure -
	 * Hierarchical validation from top-level to detailed requirements - Success
	 * logging with collection summary for audit trail
	 * 
	 * Error Handling: - Each validation rule throws IllegalArgumentException with
	 * specific message - Exceptions include context about which requirement failed
	 * - Defensive logging ensures validation success is recorded - No partial
	 * validation - all rules must pass for success
	 * 
	 * @param collection The collection to validate against business rules. Must
	 *                   meet all structural and content requirements.
	 * 
	 * @throws IllegalArgumentException with specific message if any validation rule
	 *                                  fails: - "Collection cannot be null" for
	 *                                  null collection - "Collection must have
	 *                                  basic information" for null info -
	 *                                  "Collection must have a name" for
	 *                                  missing/empty names - "Collection must have
	 *                                  at least one item/request" for empty
	 *                                  collections
	 */
	private void validateCollection(Collection collection) {
		ValidationChain.start()
        .validateNotNull(collection, "Collection cannot be null")
        .validateCollectionInfo(collection, "Collection must have basic information")
        .validateCollectionName(collection, "Collection must have a name")
        .validateCollectionItems(collection, "Collection must have at least one item/request")
        .execute();
		
		safeLogger.logOperationSuccess("Collection validation",
				extractCollectionName(collection) + " with " + collection.getItem().size() + " items");
	}

	/**
	 * Validates uploaded file against security, format, and size constraints before
	 * processing.
	 * 
	 * This method implements comprehensive file validation to ensure uploaded files
	 * meet security requirements, format expectations, and resource constraints
	 * before any processing occurs. It serves as the first line of defense against
	 * malicious uploads and prevents resource exhaustion from oversized files.
	 * 
	 * Security Validations: - File existence check prevents null pointer
	 * vulnerabilities - Extension validation ensures only JSON files are processed
	 * - Size limits prevent denial-of-service through large file uploads - Empty
	 * file detection prevents processing of meaningless uploads
	 * 
	 * Format Requirements: - Files must have .json extension for content type
	 * validation - Original filename must be present and accessible - File content
	 * must be readable (not empty or corrupted)
	 * 
	 * Resource Constraints: - Maximum file size of 10MB to prevent memory
	 * exhaustion - Size check occurs before any file reading operations - Efficient
	 * validation without loading entire file content
	 * 
	 * Validation Strategy: - Fail-fast approach with immediate exception throwing -
	 * Specific error messages for each type of validation failure - Security-first
	 * validation order (existence -> format -> size) - Success logging with file
	 * metadata for audit purposes
	 * 
	 * @param file The MultipartFile to validate before processing. Must meet all
	 *             security, format, and size requirements.
	 * 
	 * @throws IllegalArgumentException with specific message if validation fails: -
	 *                                  "File cannot be null or empty" for
	 *                                  null/empty files - "File must be a valid
	 *                                  JSON" for non-JSON files or missing filename
	 *                                  - "File too large (max 10MB)" for files
	 *                                  exceeding size limit
	 */
	private void validateFile(MultipartFile file) {
		ValidationChain.start().validateNotNull(file, "File cannot be null")
				.validateFileNotEmpty(file, "File cannot be empty") 
				.validateFileExtension(file, ".json").validateFileSize(file, properties.getMaxFileSize()).execute();

		safeLogger.logOperationSuccess("File validation", extractFilename(file) + " (" + file.getSize() + " bytes)");
	}

	/**
	 * Validates JSON string content against format, size, and content requirements
	 * before parsing.
	 * 
	 * This method provides comprehensive validation of raw JSON content to ensure
	 * it meets processing requirements and security constraints before expensive
	 * parsing operations. It implements defense-in-depth validation to prevent
	 * various attack vectors and resource exhaustion scenarios.
	 * 
	 * Content Validations: - Null content detection prevents null pointer
	 * exceptions - Empty content validation ensures meaningful data for processing
	 * - Whitespace-only detection prevents processing of effectively empty content
	 * - Size limits prevent memory exhaustion from oversized JSON payloads
	 * 
	 * Security Considerations: - Size limits prevent JSON-based denial-of-service
	 * attacks - Content validation occurs before expensive JSON parsing -
	 * Memory-efficient validation without full content processing - Consistent size
	 * limits with file upload validation
	 * 
	 * Performance Optimizations: - Fast string length checks before any parsing
	 * attempts - Fail-fast validation prevents unnecessary processing overhead -
	 * Efficient memory usage through length-based validation - No regex or complex
	 * parsing during validation phase
	 * 
	 * Business Rules: - JSON content must be present and meaningful - Size
	 * constraints align with file upload limits (10MB) - Validation provides
	 * consistent error messaging - Success logging includes content size for
	 * monitoring
	 * 
	 * @param jsonContent The raw JSON string content to validate. Must be non-null,
	 *                    non-empty, and within size limits.
	 * 
	 * @throws IllegalArgumentException with specific message if validation fails: -
	 *                                  "JSON content cannot be null" for null
	 *                                  content - "JSON content cannot be empty" for
	 *                                  empty/whitespace-only content - "JSON
	 *                                  content too large: X bytes" for oversized
	 *                                  content
	 */
	private void validateJsonContent(String jsonContent) {
		ValidationChain.start().validateNotNull(jsonContent, "JSON content cannot be null")
				.validateNotEmpty(jsonContent, "JSON content cannot be empty")
				.validateJsonSize(jsonContent, properties.getMaxJsonSize()).execute();

		safeLogger.logOperationSuccess("JSON content validation", jsonContent.length() + " characters");
	}

	/**
	 * Defensively extracts collection description with comprehensive null safety
	 * and fallback handling.
	 * 
	 * This utility method navigates the complex nested structure of Postman
	 * collection descriptions while providing robust null safety and meaningful
	 * fallback values. Collection descriptions in Postman format can be deeply
	 * nested and frequently missing, requiring careful defensive programming.
	 * 
	 * Postman Description Structure: - Collections have optional info objects -
	 * Info objects have optional description objects - Description objects have
	 * optional content strings - Any level can be null or missing in imported
	 * collections
	 * 
	 * Extraction Strategy: 1. Validate collection and info structure existence 2.
	 * Check for description object presence 3. Extract content string with null
	 * safety 4. Validate content is meaningful (not empty/whitespace) 5. Provide
	 * consistent fallback for all failure scenarios
	 * 
	 * Null Safety Features: - Handles null collection objects gracefully - Manages
	 * missing info structures without exceptions - Processes null description
	 * objects safely - Validates content strings for meaningful data - Filters
	 * whitespace-only content that appears valid
	 * 
	 * Business Context: - Descriptions are used in collection statistics and
	 * reporting - Fallback values ensure UI displays are always meaningful -
	 * Consistent extraction prevents display inconsistencies - Supports user
	 * experience with predictable behavior
	 * 
	 * @param collection The Postman collection from which to extract description.
	 *                   Can be null, have null info, null description, or null
	 *                   content. All null scenarios are handled gracefully.
	 * 
	 * @return String A guaranteed non-null description: - The actual description
	 *         content if present and meaningful - "No description available"
	 *         fallback for any null/empty scenarios - Never returns null or empty
	 *         string - Consistent fallback message for user interface display
	 */
	private String getDescriptionSafely(Collection collection) {
		// Early validation of collection and info structure
		// Prevents deep null navigation and provides fast failure path
		if (collection.getInfo() == null || collection.getInfo().getDescription() == null) {
			return "No description available";
		}

		// Extract content string from description object
		// Description content is the actual user-provided text
		String content = collection.getInfo().getDescription().getContent();

		// Validate content is meaningful and return with fallback
		// Handles null content and whitespace-only scenarios
		return (content != null && !content.trim().isEmpty()) ? content : "No description available";
	}

	/**
	 * Wraps a reactive operation with consistent logging for start, success, and
	 * error scenarios.
	 */
	private <T> Mono<T> withOperationLogging(Mono<T> operation, String operationName, String identifier) {
		return safeLogger.logOperationStartReactive(operationName, identifier).then(operation).doOnNext(result -> {
			if (result instanceof Collection collection) {
				safeLogger.logCollectionInfo("Processed", collection);
			}
		}).doOnSuccess(result -> safeLogger.logOperationSuccess(operationName,
				buildSuccessMessage(operationName, identifier, result)))
				.doOnError(error -> safeLogger.logOperationError(operationName, error));
	}

	private <T> Flux<T> withOperationLogging(Flux<T> operation, String operationName) {
		return safeLogger.logOperationStartReactive(operationName, (Object[]) null).thenMany(operation).doOnNext(result -> {
			if (result instanceof Collection collection) {
				safeLogger.logCollectionInfo("Retrieved", collection);
			}
		}).doOnComplete(() -> safeLogger.logOperationSuccess(operationName, "All items processed"))
				.doOnError(error -> safeLogger.logOperationError(operationName, error));
	}

	private String buildSuccessMessage(String operation, String identifier, Object result) {
		return switch (operation.toLowerCase()) {
		case "save collection" -> "Collection saved with ID: " + ((Collection) result).getId();
		case "delete collection" -> "Collection deleted with ID: " + identifier;
		case "import from file", "import from json" -> "Collection imported with ID: " + ((Collection) result).getId();
		default -> "Operation completed successfully";
		};
	}

}