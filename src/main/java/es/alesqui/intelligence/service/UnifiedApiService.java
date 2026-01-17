package es.alesqui.intelligence.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import es.alesqui.intelligence.exception.DocumentNotFoundException;
import es.alesqui.intelligence.model.api_spec.unified.ApiConfiguration;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedApiDocument;
import es.alesqui.intelligence.model.audit.AuditAction;
import es.alesqui.intelligence.model.audit.EntityType;
import es.alesqui.intelligence.repository.UnifiedApiRepository;
import es.alesqui.intelligence.service.audit.AuditService;
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
	private final SwaggerService swaggerService;
    private final PostmanService postmanService;
    private final AuditService auditService;

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
		return unifiedApiRepository.findById(apiId)
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
     * Finds a unified document by name, updates its ApiConfiguration, and saves it.
     *
     * @param apiName The name of the API document to update.
     * @param configuration The new configuration to apply.
     * @param request the HTTP request for audit logging
     * @return A Mono containing the updated document, or an error if not found.
     */
    public Mono<UnifiedApiDocument> updateApiConfiguration(String apiName, ApiConfiguration configuration, ServerHttpRequest request) {
        return unifiedApiRepository.findByNameIgnoreCase(apiName)
                .switchIfEmpty(Mono.error(new RuntimeException("API not found: " + apiName))) 
                .flatMap(document -> {
                    document.setApiConfiguration(configuration);
                    return unifiedApiRepository.save(document)
                            .flatMap(updatedDoc -> {
                                // Log successful API configuration update
                                return auditService.logAction(
                                        AuditAction.API_UPDATED,
                                        EntityType.API,
                                        updatedDoc.getId(),
                                        updatedDoc.getName(),
                                        "API configuration updated",
                                        request
                                ).thenReturn(updatedDoc);
                            });
                })
                .onErrorResume(error -> {
                    // Log failure
                    return auditService.logFailure(
                            AuditAction.API_UPDATED,
                            EntityType.API,
                            "unknown",
                            apiName,
                            "Failed to update API configuration: " + error.getMessage(),
                            request
                    ).then(Mono.error(error));
                });
    }
    
    /**
     * Updates the 'active' status of a specific API document.
     *
     * @param apiId The unique identifier of the API document to update.
     * @param active The new status to set (true for active, false for inactive).
     * @param request the HTTP request for audit logging
     * @return A Mono containing the updated document, or an error if not found.
     */
    @Transactional
    public Mono<UnifiedApiDocument> updateApiStatus(String apiId, boolean active, ServerHttpRequest request) {
        log.info("Updating status for API with id: {} to active={}", apiId, active);

        return unifiedApiRepository.findById(apiId)
                .switchIfEmpty(Mono.error(new DocumentNotFoundException("API not found with id: " + apiId)))
                .flatMap(document -> {
                    document.setActive(active);
                    document.setUpdatedAt(Instant.now()); // Update modification timestamp
                    return unifiedApiRepository.save(document)
                            .flatMap(savedDoc -> {
                                // Log successful API status update
                                return auditService.logAction(
                                        AuditAction.API_UPDATED,
                                        EntityType.API,
                                        savedDoc.getId(),
                                        savedDoc.getName(),
                                        "API status updated to " + (active ? "active" : "inactive"),
                                        request
                                ).thenReturn(savedDoc);
                            });
                })
                .doOnSuccess(savedDoc -> log.info("Successfully updated status for API '{}'", savedDoc.getName()))
                .onErrorResume(error -> {
                    log.error("Error updating status for API with ID: {}", apiId, error);
                    // Log failure
                    return auditService.logFailure(
                            AuditAction.API_UPDATED,
                            EntityType.API,
                            apiId,
                            "unknown",
                            "Failed to update API status: " + error.getMessage(),
                            request
                    ).then(Mono.error(error));
                });
    }

    /**
     * Deletes a UnifiedApiDocument and its corresponding Swagger and Postman documents.
     * The deletion is based on the unique name shared across the documents.
     *
     * @param id The ID of the UnifiedApiDocument to delete.
     * @param request the HTTP request for audit logging (can be null for system operations)
     * @return A Mono<Void> that completes when all documents are deleted.
     */
    public Mono<Void> deleteApi(String id, ServerHttpRequest request) {
        log.info("Attempting to delete API with ID: {}", id);

        return unifiedApiRepository.findById(id)
                .flatMap(api -> {
                    String apiName = api.getName();
                    String apiId = api.getId();
                    return swaggerService.deleteByName(apiName)
                        .then(postmanService.deleteByName(apiName))
                        .then(unifiedApiRepository.delete(api))
                        .then(request != null
                            ? auditService.logAction(
                                    AuditAction.API_DELETED,
                                    EntityType.API,
                                    apiId,
                                    apiName,
                                    "API deleted successfully",
                                    request
                            )
                            : Mono.empty() // Skip audit logging if no request context (system operation)
                        );
                })
                .onErrorResume(error -> {
                    log.error("Error deleting API with ID: {}", id, error);
                    // Log failure if request context is available
                    if (request != null) {
                        return auditService.logFailure(
                                AuditAction.API_DELETED,
                                EntityType.API,
                                id,
                                "unknown",
                                "Failed to delete API: " + error.getMessage(),
                                request
                        ).then(Mono.error(error));
                    } else {
                        return Mono.error(error);
                    }
                });
    }

    /**
     * Deletes a UnifiedApiDocument and its corresponding Swagger and Postman documents.
     * This overload is for internal system operations that don't have an HTTP request context.
     * Audit logging will still be performed but without HTTP metadata (IP, user agent).
     *
     * @param id The ID of the UnifiedApiDocument to delete.
     * @return A Mono<Void> that completes when all documents are deleted.
     */
    public Mono<Void> deleteApi(String id) {
        return deleteApi(id, null);
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
		return unifiedApiRepository.existsById(apiId)
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