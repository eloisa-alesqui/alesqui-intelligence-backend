package es.alesqui.postmangpt.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import es.alesqui.postmangpt.model.postman.PostmanDocument;
import es.alesqui.postmangpt.model.swagger.SwaggerDocument;
import es.alesqui.postmangpt.model.unified.UnifiedApiDocument;
import es.alesqui.postmangpt.service.PostmanService;
import es.alesqui.postmangpt.service.SwaggerService;
import es.alesqui.postmangpt.service.unification.ApiUnificationService;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/unification")
public class ApiUnificationController {

    @Autowired
    private ApiUnificationService apiUnificationService;

    @Autowired
    private SwaggerService swaggerService;

    @Autowired
    private PostmanService postmanService;

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
