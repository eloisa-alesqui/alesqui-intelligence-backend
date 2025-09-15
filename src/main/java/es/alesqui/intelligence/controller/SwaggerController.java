package es.alesqui.intelligence.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import es.alesqui.intelligence.dto.ApiResponse;
import es.alesqui.intelligence.model.api_spec.swagger.SwaggerDocument;
import es.alesqui.intelligence.service.SwaggerService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * Reactive REST Controller for managing Swagger/OpenAPI documents.
 * 
 * This controller provides APIs for CRUD operations, file imports, content-based imports,
 * and health checks for Swagger documents. All endpoints use Spring WebFlux for reactive programming.
 */
@Slf4j
@RestController
@RequestMapping("/api/swagger")
@RequiredArgsConstructor
@Validated
public class SwaggerController {

    private final SwaggerService swaggerService;

    /**
     * Retrieves all Swagger documents from the database.
     *
     * @return a Flux containing all SwaggerDocument objects
     */
    @GetMapping(produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<SwaggerDocument> findAll() {
        log.info("Fetching all Swagger documents...");
        return swaggerService.findAll()
                .doOnNext(document -> log.debug("Retrieved document: {}", document.getName()))
                .doOnError(error -> log.error("Error fetching Swagger documents", error));
    }

    /**
     * Retrieves a Swagger document by its unique identifier.
     *
     * @param id the unique identifier of the document
     * @return a Mono containing the SwaggerDocument if found, or a 404 response if not found
     */
    @GetMapping("/{id}")
    public Mono<ResponseEntity<SwaggerDocument>> findById(@PathVariable @NotBlank String id) {
        log.info("Fetching Swagger document with ID: {}", id);
        return swaggerService.findById(id)
                .map(document -> ResponseEntity.ok(document))
                .defaultIfEmpty(ResponseEntity.notFound().build())
                .doOnError(error -> log.error("Error fetching document with ID: {}", id, error));
    }

    /**
     * Retrieves a Swagger document by its name.
     *
     * @param name the name of the document
     * @return a Mono containing the SwaggerDocument if found, or a 404 response if not found
     */
    @GetMapping("/by-name")
    public Mono<ResponseEntity<SwaggerDocument>> findByName(@RequestParam String name) {
        log.info("Fetching Swagger document with name: {}", name);
        return swaggerService.findByName(name)
                .map(document -> ResponseEntity.ok(document))
                .defaultIfEmpty(ResponseEntity.notFound().build())
                .doOnError(error -> log.error("Error fetching document with name: {}", name, error));
    }

    /**
     * Creates or updates a Swagger document.
     *
     * @param document the SwaggerDocument to save or update
     * @return a Mono containing the saved SwaggerDocument
     */
    @PostMapping
    public Mono<ResponseEntity<SwaggerDocument>> save(@Valid @RequestBody @NotNull SwaggerDocument document) {
        boolean isNew = document.getId() == null || document.getId().isEmpty();
        log.info("Saving Swagger document: {}", isNew ? "New document" : "Updating document with ID " + document.getId());
        return swaggerService.save(document)
                .map(saved -> ResponseEntity.status(isNew ? HttpStatus.CREATED : HttpStatus.OK).body(saved))
                .doOnError(error -> log.error("Error saving Swagger document", error));
    }

    /**
     * Deletes a Swagger document by its unique identifier.
     *
     * @param id the unique identifier of the document
     * @return a Mono confirming the deletion
     */
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteById(@PathVariable @NotBlank String id) {
        log.info("Deleting Swagger document with ID: {}", id);
        return swaggerService.deleteById(id)
                .then(Mono.fromCallable(() -> ResponseEntity.noContent().<Void>build()))
                .doOnError(error -> log.error("Error deleting Swagger document with ID: {}", id, error));
    }

    /**
     * Imports a Swagger document from a file.
     *
     * @param file        the file containing the Swagger document (JSON/YAML)
     * @param name        the name of the document
     * @param team        the team owning the document
     * @param createdBy   the user creating the document
     * @param description optional description for the document
     * @return a Mono containing the imported SwaggerDocument
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<SwaggerDocument>> importFromFile(
            @RequestPart("file") FilePart file,
            @RequestPart("name") String name,
            @RequestPart("team") String team,
            @RequestPart("createdBy") String createdBy,
            @RequestPart(value = "description", required = false) String description) {
        log.info("Importing Swagger document from file: {}", file.filename());
        return swaggerService.importFromFile(file, name, description, team, createdBy)
                .map(imported -> ResponseEntity.status(HttpStatus.CREATED).body(imported))
                .doOnError(error -> log.error("Error importing Swagger document from file", error));
    }

    /**
     * Imports a Swagger document from raw JSON or YAML content.
     *
     * @param content     the raw content of the Swagger document
     * @param name        the name of the document
     * @param team        the team owning the document
     * @param createdBy   the user creating the document
     * @param description optional description for the document
     * @return a Mono containing the imported SwaggerDocument
     */
    @PostMapping(value = "/import-json", consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_PLAIN_VALUE})
    public Mono<ResponseEntity<SwaggerDocument>> importFromJson(
            @RequestBody String content,
            @RequestParam("name") String name,
            @RequestParam("team") String team,
            @RequestParam("createdBy") String createdBy,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("contentType") String contentType) {
        log.info("Importing Swagger document from raw content");
        return swaggerService.importFromContent(content, name, description, team, createdBy, contentType)
                .map(imported -> ResponseEntity.status(HttpStatus.CREATED).body(imported))
                .doOnError(error -> log.error("Error importing Swagger document from content", error));
    }

    /**
     * Health check endpoint for the Swagger service.
     *
     * @return a Mono containing the health status of the service
     */
    @GetMapping("/health")
    public Mono<ResponseEntity<ApiResponse<String>>> healthCheck() {
        log.info("Performing health check for Swagger service");
        return Mono.fromCallable(() -> {
            ApiResponse<String> response = ApiResponse.<String>builder()
                    .success(true)
                    .message("Swagger service is healthy")
                    .data("OK")
                    .timestamp(LocalDateTime.now())
                    .build();

            return ResponseEntity.ok(response);
        });
    }
}
