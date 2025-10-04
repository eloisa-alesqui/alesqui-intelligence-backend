package es.alesqui.intelligence.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.*;

import es.alesqui.intelligence.model.api_spec.postman.PostmanDocument;
import es.alesqui.intelligence.model.api_spec.swagger.SwaggerDocument;
import es.alesqui.intelligence.model.api_spec.unified.ApiConfiguration;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedApiDocument;
import es.alesqui.intelligence.service.PostmanService;
import es.alesqui.intelligence.service.SwaggerService;
import es.alesqui.intelligence.service.UnifiedApiService;
import es.alesqui.intelligence.service.unification.ApiUnificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/unification")
@RequiredArgsConstructor
public class ApiUnificationController {

    private final ApiUnificationService apiUnificationService;
    private final SwaggerService swaggerService;
    private final PostmanService postmanService;
    private final UnifiedApiService unifiedApiService;

    /**
     * Unifies Swagger and Postman documents by API name and saves the unified document.
     *
     * @param apiName The name of the API to unify.
     * @return ResponseEntity containing the result of the operation.
     */
    @PostMapping("/unify")
    public Mono<ResponseEntity<String>> unifyAndSaveApiDocuments(@RequestParam String apiName) {
        log.info("Starting unification process for API: {}", apiName);

        Mono<SwaggerDocument> swaggerMono = swaggerService.findByName(apiName)
                .switchIfEmpty(Mono.error(new RuntimeException("SwaggerDocument not found for API: " + apiName)));

        return swaggerMono
            .flatMap(swaggerDoc -> 
                postmanService.findByName(apiName)
                    .flatMap(postmanDoc -> unifyAndSave(swaggerDoc, postmanDoc))
                    .switchIfEmpty(Mono.defer(() -> unifyAndSave(swaggerDoc, null)))
            )
            .map(savedDoc -> ResponseEntity.ok("Unified API document saved successfully with ID: " + savedDoc.getId()))
            .onErrorResume(e -> {
                log.error("Failed to unify API '{}': {}", apiName, e.getMessage(), e);
                String errorMessage = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error during unification: " + errorMessage));
            });
    }
    
    /**
     * Updates the runtime configuration for a specific unified API.
     * The API is identified by its unique name.
     *
     * @param apiName The name of the API to configure.
     * @param apiConfiguration The configuration object from the request body.
     * @return A Mono with the updated UnifiedApiDocument.
     */
    @PutMapping("/{apiName}/configuration")
    public Mono<ResponseEntity<UnifiedApiDocument>> updateApiConfiguration(
            @PathVariable String apiName,
            @RequestBody ApiConfiguration apiConfiguration) {
        
        log.info("Updating configuration for API: {}", apiName);
        return unifiedApiService.updateConfiguration(apiName, apiConfiguration)
                .map(ResponseEntity::ok) // On success, return 200 OK with the updated document
                .onErrorResume(e -> {
                    log.error("Failed to update configuration for API '{}': {}", apiName, e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }
    
    /**
     * Retrieves all Unified Api documents from the database.
     * 
     * @return a Flux containing all Unified Api documents
     */
    @GetMapping
    public Flux<UnifiedApiDocument> findAll() {
        log.info("Fetching all Unified Api documents...");
        return unifiedApiService.findAll()
                .doOnNext(document -> log.debug("Retrieved document: {}", document.getName()))
                .doOnError(error -> log.error("Error fetching Unified Api documents", error));
    }

    /**
     * Retrieves a Unified Api document by its unique identifier.
     * 
     * @param id the unique identifier of the document
     * @return a Mono containing the Unified Api document if found, or a 404 response if not found
     */
    @GetMapping("/{id}")
    public Mono<ResponseEntity<UnifiedApiDocument>> findById(@PathVariable String id) {
        log.info("Fetching Unified Api document with ID: {}", id);
        return unifiedApiService.findById(id)
                .map(document -> ResponseEntity.ok(document))
                .defaultIfEmpty(ResponseEntity.notFound().build())
                .doOnError(error -> log.error("Error fetching document with ID: {}", id, error));
    }

    /**
     * Retrieves a Unified Api document by its name.
     * 
     * @param name the name of the document
     * @return a Mono containing the Unified Api document if found, or a 404 response if not found
     */
    @GetMapping("/by-name")
    public Mono<ResponseEntity<UnifiedApiDocument>> findByName(@RequestParam String name) {
        log.info("Fetching Unified Api document with name: {}", name);
        return unifiedApiService.findByName(name)
                .map(document -> ResponseEntity.ok(document))
                .defaultIfEmpty(ResponseEntity.notFound().build())
                .doOnError(error -> log.error("Error fetching document with name: {}", name, error));
    }
    
    /**
     * Deletes an API specification and its related documents.
     *
     * This operation performs a cascading delete, removing the main UnifiedApiDocument
     * as well as any associated Swagger and Postman documents that share the same unique name.
     * The operation is atomic; if any part of the deletion fails, the transaction should ideally be rolled back.
     *
     * @param id The unique identifier (String) of the UnifiedApiDocument to be deleted.
     * @return An empty Mono (Mono<Void>) that completes when the deletion operation is finished,
     * or emits an error if the API with the given ID is not found or if the deletion fails.
     */
	@DeleteMapping("/{id}")
	public Mono<ResponseEntity<Void>> deleteApi(@PathVariable String id) {
		log.info("DELETE request received for unification id: {}", id);
		return unifiedApiService.deleteApi(id)
				.then(Mono.just(new ResponseEntity<Void>(HttpStatus.NO_CONTENT))) 
				.defaultIfEmpty(new ResponseEntity<>(HttpStatus.NOT_FOUND));
	}
    
    /**
     * Helper method to unify documents and save the result.
     * Handles a potentially null PostmanDocument.
     *
     * @param swaggerDoc The mandatory Swagger document.
     * @param postmanDoc The optional Postman document.
     * @return A Mono with the saved UnifiedApiDocument.
     */
    private Mono<UnifiedApiDocument> unifyAndSave(SwaggerDocument swaggerDoc, @Nullable PostmanDocument postmanDoc) {
        return Mono.fromCallable(() -> apiUnificationService.unifyApiDocuments(swaggerDoc, postmanDoc))
                   .flatMap(apiUnificationService::save) 
                   .doOnSuccess(savedDoc -> log.info("Successfully unified and saved document for API: {}", savedDoc.getName()))
                   .doOnError(e -> log.error("Error in unifyAndSave for API {}: {}", swaggerDoc.getName(), e.getMessage()));
    }

}
