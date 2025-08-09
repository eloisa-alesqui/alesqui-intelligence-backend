package es.alesqui.postmangpt.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import es.alesqui.postmangpt.model.postman.PostmanDocument;
import es.alesqui.postmangpt.model.swagger.SwaggerDocument;
import es.alesqui.postmangpt.model.unified.UnifiedApiDocument;
import es.alesqui.postmangpt.service.PostmanService;
import es.alesqui.postmangpt.service.SwaggerService;
import es.alesqui.postmangpt.service.UnifiedApiService;
import es.alesqui.postmangpt.service.unification.ApiUnificationService;
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
        return swaggerService.findByName(apiName)
                .flatMap(swaggerDoc -> {
                    if (swaggerDoc == null) {
                        return Mono.just(ResponseEntity.badRequest().body("SwaggerDocument not found for API: " + apiName));
                    }

                    return postmanService.findByName(apiName)
                            .flatMap(postmanDoc -> {
                                // Validar que el documento Postman no sea nulo
                                if (postmanDoc == null) {
                                    return Mono.just(ResponseEntity.badRequest().body("PostmanDocument not found for API: " + apiName));
                                }

                                // Intentar unificar los documentos
                                return unifyAndSave(swaggerDoc, postmanDoc);
                            });
                })
                .onErrorResume(e -> Mono.just(ResponseEntity.status(500).body("Unexpected error: " + e.getMessage())));
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
    
    private Mono<ResponseEntity<String>> unifyAndSave(SwaggerDocument swaggerDoc, PostmanDocument postmanDoc) {
        try {
            UnifiedApiDocument unifiedDocument = apiUnificationService.unifyApiDocuments(swaggerDoc, postmanDoc);

            return apiUnificationService.save(unifiedDocument)
                    .map(savedDocument -> ResponseEntity.ok("Unified API document saved successfully"))
                    .onErrorResume(e -> Mono.just(ResponseEntity.status(500).body("Error saving unified document: " + e.getMessage())));
        } catch (Exception e) {
            return Mono.just(ResponseEntity.status(500).body("Error unifying API documents: " + e.getMessage()));
        }
    }

}
