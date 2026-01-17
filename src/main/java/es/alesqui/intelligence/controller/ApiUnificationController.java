package es.alesqui.intelligence.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.*;

import es.alesqui.intelligence.model.api_spec.postman.PostmanDocument;
import es.alesqui.intelligence.model.api_spec.swagger.SwaggerDocument;
import es.alesqui.intelligence.model.api_spec.unified.ApiConfiguration;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedApiDocument;
import es.alesqui.intelligence.service.PostmanService;
import es.alesqui.intelligence.service.SwaggerService;
import es.alesqui.intelligence.service.UnifiedApiService;
import es.alesqui.intelligence.service.access.ApiGroupLinkService;
import es.alesqui.intelligence.service.identity.UserService;
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
    private final ApiGroupLinkService apiGroupLinkService;
    private final UserService userService;

    /**
     * Unifies Swagger and Postman documents by API name and saves the unified document.
     *
     * @param apiName The name of the API to unify.
     * @param request the HTTP request for audit logging
     * @return ResponseEntity containing the result of the operation.
     */
    @PostMapping("/unify")
    public Mono<ResponseEntity<String>> unifyAndSaveApiDocuments(@RequestParam String apiName, 
                                                                   ServerHttpRequest request) {
        log.info("Starting unification process for API: {}", apiName);

        Mono<SwaggerDocument> swaggerMono = swaggerService.findByName(apiName)
                .switchIfEmpty(Mono.error(new RuntimeException("SwaggerDocument not found for API: " + apiName)));

        return swaggerMono
            .flatMap(swaggerDoc -> 
                postmanService.findByName(apiName)
                    .flatMap(postmanDoc -> unifyAndSave(swaggerDoc, postmanDoc, request))
                    .switchIfEmpty(Mono.defer(() -> unifyAndSave(swaggerDoc, null, request)))
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
     * @param request the HTTP request for audit logging
     * @return A Mono with the updated UnifiedApiDocument.
     */
    @PutMapping("/{apiName}/configuration")
    public Mono<ResponseEntity<UnifiedApiDocument>> updateApiConfiguration(
            @PathVariable String apiName,
            @RequestBody ApiConfiguration apiConfiguration,
            ServerHttpRequest request) {
        
        log.info("Updating configuration for API: {}", apiName);
        
        // First find the API to get its ID, then check modification permissions
        return unifiedApiService.findByName(apiName)
                .switchIfEmpty(Mono.error(new RuntimeException("API not found: " + apiName)))
                .flatMap(api -> userService.getCurrentUsername()
                        .flatMap(username -> apiGroupLinkService.canModifyApi(username, api.getId())
                                .flatMap(canModify -> {
                                    if (!canModify) {
                                        log.warn("User {} attempted to modify API {} but lacks permission", username, apiName);
                                        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).<UnifiedApiDocument>build());
                                    }
                                    return unifiedApiService.updateApiConfiguration(apiName, apiConfiguration, request)
                                            .map(ResponseEntity::ok);
                                })
                        )
                )
                .onErrorResume(e -> {
                    log.error("Failed to update configuration for API '{}': {}", apiName, e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }
    
    /**
     * Partially updates an API document, specifically its 'active' status.
     *
     * @param id The unique identifier of the UnifiedApiDocument.
     * @param statusUpdate A map containing the 'active' key, e.g., { "active": true }.
     * @param request the HTTP request for audit logging
     * @return A Mono with the updated UnifiedApiDocument.
     */
    @PatchMapping("/{id}/status")
    public Mono<ResponseEntity<UnifiedApiDocument>> updateApiStatus(
            @PathVariable String id,
            @RequestBody Map<String, Boolean> statusUpdate,
            ServerHttpRequest request) {
        
        Boolean active = statusUpdate.get("active");
        if (active == null) {
            // Return a Bad Request error if the 'active' key is missing from the body
            return Mono.just(ResponseEntity.badRequest().build());
        }

        log.info("PATCH request to update status for unification id: {}", id);
        
        // Check modification permissions before updating
        return userService.getCurrentUsername()
                .flatMap(username -> apiGroupLinkService.canModifyApi(username, id)
                        .flatMap(canModify -> {
                            if (!canModify) {
                                log.warn("User {} attempted to modify status of API {} but lacks permission", username, id);
                                return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).<UnifiedApiDocument>build());
                            }
                            return unifiedApiService.updateApiStatus(id, active, request)
                                    .map(ResponseEntity::ok)
                                    .defaultIfEmpty(ResponseEntity.notFound().build());
                        })
                );
    }
    
    /**
     * Retrieves all visible Unified Api documents from the database.
     * Only returns APIs that the current user has permission to access based on group membership.
     * 
     * @return a Flux containing all visible Unified Api documents
     */
    @GetMapping
    public Flux<UnifiedApiDocument> findAll() {
        log.info("Fetching visible Unified Api documents for current user...");
        return userService.getCurrentUserId()
                .flatMapMany(userId -> {
                    log.debug("Fetching visible APIs for userId: {}", userId);
                    return apiGroupLinkService.listVisibleApis(userId);
                })
                .sort((api1, api2) -> api1.getName().compareToIgnoreCase(api2.getName()))
                .doOnNext(document -> log.debug("Retrieved visible document: {}", document.getName()))
                .doOnError(error -> log.error("Error fetching visible Unified Api documents", error));
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
     * @param request the HTTP request for audit logging
     * @return An empty Mono (Mono<Void>) that completes when the deletion operation is finished,
     * or emits an error if the API with the given ID is not found or if the deletion fails.
     */
	@DeleteMapping("/{id}")
	public Mono<ResponseEntity<Void>> deleteApi(@PathVariable String id, ServerHttpRequest request) {
		log.info("DELETE request received for unification id: {}", id);
        
        // Check modification permissions before deleting
        return userService.getCurrentUsername()
                .flatMap(username -> apiGroupLinkService.canModifyApi(username, id)
                        .flatMap(canModify -> {
                            if (!canModify) {
                                log.warn("User {} attempted to delete API {} but lacks permission", username, id);
                                return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).<Void>build());
                            }
                            return unifiedApiService.deleteApi(id, request)
                                    .then(Mono.just(new ResponseEntity<Void>(HttpStatus.NO_CONTENT))) 
                                    .defaultIfEmpty(new ResponseEntity<>(HttpStatus.NOT_FOUND));
                        })
                );
	}
    
    /**
     * Helper method to unify documents and save the result.
     * Handles a potentially null PostmanDocument.
     *
     * @param swaggerDoc The mandatory Swagger document.
     * @param postmanDoc The optional Postman document.
     * @param request the HTTP request for audit logging
     * @return A Mono with the saved UnifiedApiDocument.
     */
    private Mono<UnifiedApiDocument> unifyAndSave(SwaggerDocument swaggerDoc, @Nullable PostmanDocument postmanDoc, 
                                                   ServerHttpRequest request) {
        return Mono.fromCallable(() -> apiUnificationService.unifyApiDocuments(swaggerDoc, postmanDoc))
                   .flatMap(unifiedDoc -> apiUnificationService.save(unifiedDoc, request)) 
                   .doOnSuccess(savedDoc -> log.info("Successfully unified and saved document for API: {}", savedDoc.getName()))
                   .doOnError(e -> log.error("Error in unifyAndSave for API {}: {}", swaggerDoc.getName(), e.getMessage()));
    }

}
