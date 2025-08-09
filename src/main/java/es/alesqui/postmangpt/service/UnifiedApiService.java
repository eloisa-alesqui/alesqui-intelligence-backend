package es.alesqui.postmangpt.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.bson.types.ObjectId;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import es.alesqui.postmangpt.model.unified.UnifiedApiDocument;
import es.alesqui.postmangpt.repository.UnifiedApiRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Reactive service layer for managing unified API documents. This service
 * provides business logic for API document operations including CRUD
 * operations, search functionality, and data validation using reactive
 * programming.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UnifiedApiService {

	private final UnifiedApiRepository unifiedApiRepository;

	/**
	 * Retrieves all API documents from the repository.
	 *
	 * @return Flux of all UnifiedApiDocument objects
	 */
	public Flux<UnifiedApiDocument> findAll() {
		log.debug("Fetching all API documents");
		return unifiedApiRepository.findAll().doOnNext(doc -> log.debug("Retrieved API document: {}", doc.getName()))
				.doOnError(error -> log.error("Error fetching all API documents", error));
	}

	/**
	 * Retrieves API documents with pagination support.
	 *
	 * @param pageable Pagination information
	 * @return Mono of Page containing UnifiedApiDocument objects
	 */
	public Mono<Page<UnifiedApiDocument>> getApisPageable(Pageable pageable) {
		log.debug("Fetching API documents with pagination: {}", pageable);

		// Get total count and paginated results reactively
		Mono<Long> totalCount = unifiedApiRepository.count()
				.doOnNext(count -> log.debug("Total API documents count: {}", count));

		Flux<UnifiedApiDocument> content = unifiedApiRepository.findAll().skip(pageable.getOffset())
				.take(pageable.getPageSize())
				.doOnNext(doc -> log.debug("Retrieved paginated API document: {}", doc.getName()));

		return Mono.zip(content.collectList(), totalCount).map(tuple -> {
			List<UnifiedApiDocument> documents = tuple.getT1();
			Long total = tuple.getT2();
			Page<UnifiedApiDocument> page = new PageImpl<>(documents, pageable, total);
			return page;
		}).doOnSuccess(page -> log.debug("Created page with {} elements", page.getContent().size()))
				.doOnError(error -> log.error("Error fetching paginated API documents", error));

	}

	/**
	 * Retrieves a specific API document by its identifier.
	 *
	 * @param apiId The unique identifier of the API
	 * @return Mono of UnifiedApiDocument if found, error otherwise
	 */
	@Cacheable(value = "apis", key = "#apiId")
	public Mono<UnifiedApiDocument> findById(String apiId) {
		log.debug("Fetching API document with id: {}", apiId);
		ObjectId objectId = new ObjectId(apiId);
		return unifiedApiRepository.findById(objectId)
				.doOnNext(doc -> log.info("Found API document: {}", doc.getName()))
				.switchIfEmpty(Mono.error(new IllegalArgumentException("API not found with ID: " + apiId)))
				.doOnError(error -> log.error("Error fetching API document by ID: {}", apiId, error));
	}

	/**
	 * Retrieves a specific API document by its name.
	 *
	 * @param apiName The name of the API
	 * @return Mono of UnifiedApiDocument if found, empty Mono otherwise
	 */
	@Cacheable(value = "apis", key = "#apiName")
	public Mono<UnifiedApiDocument> findByName(String apiName) {
		log.debug("Fetching API document with name: {}", apiName);
		return unifiedApiRepository.findByNameIgnoreCase(apiName)
				.doOnNext(doc -> log.info("Found API document by name: {}", doc.getName())).switchIfEmpty(Mono.empty())
				.doOnError(error -> log.error("Error fetching API document by name: {}", apiName, error));
	}

	/**
	 * Creates a new API document entry in the repository.
	 *
	 * @param apiDocument The API document to save
	 * @return Mono of the saved UnifiedApiDocument object
	 */
	@Transactional
	public Mono<UnifiedApiDocument> createApi(UnifiedApiDocument apiDocument) {
		log.info("Creating new API document: {}", apiDocument.getName());

		return Mono.fromCallable(() -> {
			// Validate API document
			validateApiDocument(apiDocument);

			// Set creation timestamp
			apiDocument.setCreatedAt(Instant.now());
			apiDocument.setUpdatedAt(Instant.now());

			return apiDocument;
		}).flatMap(validatedDoc -> checkDuplicatesAndSave(validatedDoc))
				.doOnSuccess(saved -> log.info("API document created with ID: {}", saved.getId()))
				.doOnError(error -> log.error("Error creating API document: {}", apiDocument.getName(), error));
	}

	/**
	 * Updates an existing API document entry.
	 *
	 * @param apiId       The identifier of the API to update
	 * @param apiDocument The updated API document
	 * @return Mono of the updated UnifiedApiDocument object
	 */
	@Transactional
	public Mono<UnifiedApiDocument> updateApi(String apiId, UnifiedApiDocument apiDocument) {
		log.info("Updating API document with id: {}", apiId);
		ObjectId objectId = new ObjectId(apiId);

		return unifiedApiRepository.findById(objectId)
				.switchIfEmpty(Mono.error(new IllegalArgumentException("API not found with id: " + apiId)))
				.flatMap(existingApi -> {
					return Mono.fromCallable(() -> {
						// Validate API document
						validateApiDocument(apiDocument);

						// Preserve creation timestamp and update modification timestamp
						apiDocument.setId(objectId);
						apiDocument.setCreatedAt(existingApi.getCreatedAt());
						apiDocument.setUpdatedAt(Instant.now());

						return apiDocument;
					});
				}).flatMap(updatedDoc -> unifiedApiRepository.save(updatedDoc))
				.doOnSuccess(saved -> log.info("API document updated with ID: {}", saved.getId()))
				.doOnError(error -> log.error("Error updating API document with ID: {}", apiId, error));
	}

	/**
	 * Deletes an API document from the repository.
	 *
	 * @param apiId The identifier of the API to delete
	 * @return Mono that completes when deletion is successful
	 */
	@Transactional
	public Mono<Void> deleteApi(String apiId) {
		log.info("Deleting API document with id: {}", apiId);
		ObjectId objectId = new ObjectId(apiId);

		return unifiedApiRepository.existsById(objectId).flatMap(exists -> {
			if (!exists) {
				return Mono.error(new IllegalArgumentException("API not found with id: " + apiId));
			}
			return unifiedApiRepository.deleteById(objectId);
		}).doOnSuccess(unused -> log.info("API document deleted with ID: {}", apiId))
				.doOnError(error -> log.error("Error deleting API document with ID: {}", apiId, error));
	}

	/**
	 * Searches for API documents by name or description.
	 *
	 * @param searchTerm The term to search for
	 * @return Flux of matching UnifiedApiDocument objects
	 */
	public Flux<UnifiedApiDocument> searchApis(String searchTerm) {
		log.debug("Searching API documents with term: {}", searchTerm);
		return unifiedApiRepository
				.findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(searchTerm, searchTerm)
				.doOnNext(doc -> log.debug("Found matching API document: {}", doc.getName()))
				.doOnError(error -> log.error("Error searching API documents with term: {}", searchTerm, error));
	}

	/**
	 * Finds API documents by tag.
	 *
	 * @param tag The tag to filter by
	 * @return Flux of UnifiedApiDocument objects with the specified tag
	 */
	public Flux<UnifiedApiDocument> findApisByTag(String tag) {
		log.debug("Finding API documents by tag: {}", tag);
		return unifiedApiRepository.findByTagsContaining(tag)
				.doOnNext(doc -> log.debug("Found API document with tag '{}': {}", tag, doc.getName()))
				.doOnError(error -> log.error("Error finding API documents by tag: {}", tag, error));
	}

	/**
	 * Finds API documents by team.
	 *
	 * @param team The team name to filter by
	 * @return Flux of UnifiedApiDocument objects managed by the specified team
	 */
	public Flux<UnifiedApiDocument> findApisByTeam(String team) {
		log.debug("Finding API documents by team: {}", team);
		return unifiedApiRepository.findByTeam(team)
				.doOnNext(doc -> log.debug("Found API document for team '{}': {}", team, doc.getName()))
				.doOnError(error -> log.error("Error finding API documents by team: {}", team, error));
	}

	/**
	 * Gets document IDs associated with a specific API. These IDs are used for
	 * vector store operations.
	 *
	 * @param apiId The API identifier
	 * @return Mono of List containing document IDs
	 */
	public Mono<List<String>> getDocumentIdsByApiId(String apiId) {
		log.debug("Getting document IDs for API: {}", apiId);

		return findById(apiId).map(apiDoc -> {
			List<String> documentIds = new ArrayList<>();

			// Add overview document ID
			documentIds.add(apiId + "_overview");

			// Add endpoint document IDs
			if (apiDoc.getEndpoints() != null) {
				apiDoc.getEndpoints().forEach(endpoint -> documentIds.add(endpoint.getId()));
			}

			return documentIds;
		}).switchIfEmpty(Mono.just(List.of()))
				.doOnNext(ids -> log.debug("Found {} document IDs for API: {}", ids.size(), apiId))
				.doOnError(error -> log.error("Error getting document IDs for API: {}", apiId, error));
	}

	/**
	 * Counts the total number of API documents in the repository.
	 *
	 * @return Mono of the total count of API documents
	 */
	public Mono<Long> countApis() {
		return unifiedApiRepository.count().doOnNext(count -> log.debug("Total API documents count: {}", count))
				.doOnError(error -> log.error("Error counting API documents", error));
	}

	/**
	 * Counts the number of active API documents.
	 *
	 * @return Mono of the count of active API documents
	 */
	public Mono<Long> countActiveApis() {
		return unifiedApiRepository.countByActiveTrue()
				.doOnNext(count -> log.debug("Active API documents count: {}", count))
				.doOnError(error -> log.error("Error counting active API documents", error));
	}

	/**
	 * Checks if an API document exists by its identifier.
	 *
	 * @param apiId The API identifier to check
	 * @return Mono of boolean indicating if the API exists
	 */
	public Mono<Boolean> existsById(String apiId) {
		ObjectId objectId = new ObjectId(apiId);
		return unifiedApiRepository.existsById(objectId)
				.doOnNext(exists -> log.debug("API with ID '{}' exists: {}", apiId, exists))
				.doOnError(error -> log.error("Error checking if API exists by ID: {}", apiId, error));
	}

	/**
	 * Finds all active API documents.
	 *
	 * @return Flux of active UnifiedApiDocument objects
	 */
	public Flux<UnifiedApiDocument> findActiveApis() {
		log.debug("Finding all active API documents");
		return unifiedApiRepository.findByActiveTrue()
				.doOnNext(doc -> log.debug("Found active API document: {}", doc.getName()))
				.doOnError(error -> log.error("Error finding active API documents", error));
	}

	/**
	 * Checks for duplicate API documents by name and saves the document if no
	 * duplicates are found.
	 * 
	 * @param document the API document to validate and save
	 * @return Mono of the saved UnifiedApiDocument or an error if a duplicate is
	 *         found
	 */
	private Mono<UnifiedApiDocument> checkDuplicatesAndSave(UnifiedApiDocument document) {
		return findByName(document.getName()).hasElement().flatMap(exists -> {
			if (exists) {
				log.warn("Duplicate API document found with name: {}", document.getName());
				return Mono.error(new IllegalArgumentException("API document with this name already exists"));
			}
			return unifiedApiRepository.save(document);
		});
	}

	/**
	 * Validates API document data before saving.
	 *
	 * @param apiDocument The API document to validate
	 * @throws IllegalArgumentException if validation fails
	 */
	private void validateApiDocument(UnifiedApiDocument apiDocument) {
		if (apiDocument == null) {
			throw new IllegalArgumentException("API document cannot be null");
		}

		if (apiDocument.getName() == null || apiDocument.getName().trim().isEmpty()) {
			throw new IllegalArgumentException("API name cannot be empty");
		}

		if (apiDocument.getBaseUrl() == null || apiDocument.getBaseUrl().trim().isEmpty()) {
			throw new IllegalArgumentException("API base URL cannot be empty");
		}

		if (apiDocument.getVersion() == null || apiDocument.getVersion().trim().isEmpty()) {
			throw new IllegalArgumentException("API version cannot be empty");
		}
	}
}