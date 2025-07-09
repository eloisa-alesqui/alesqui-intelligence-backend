package es.alesqui.postmangpt.controller;

import es.alesqui.postmangpt.model.postman.Collection;
import es.alesqui.postmangpt.model.postman.PostmanDocument;
import es.alesqui.postmangpt.service.PostmanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.validation.Valid;

/**
 * Reactive REST controller for managing Postman collections and documents.
 * 
 * This controller provides APIs for CRUD operations, file imports, endpoint extraction, 
 * and statistics generation for Postman collections. All endpoints use Spring WebFlux 
 * for reactive programming.
 */
@Slf4j
@RestController
@RequestMapping("/api/postman")
@RequiredArgsConstructor
public class PostmanController {

    private final PostmanService postmanService;

    /**
     * Retrieves all Postman documents from the database.
     * 
     * @return a Flux containing all Postman documents
     */
    @GetMapping(produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<PostmanDocument> findAll() {
        log.info("Fetching all Postman documents...");
        return postmanService.findAll()
                .doOnNext(document -> log.debug("Retrieved document: {}", document.getName()))
                .doOnError(error -> log.error("Error fetching Postman documents", error));
    }

    /**
     * Retrieves a Postman document by its unique identifier.
     * 
     * @param id the unique identifier of the document
     * @return a Mono containing the Postman document if found, or a 404 response if not found
     */
    @GetMapping("/{id}")
    public Mono<ResponseEntity<PostmanDocument>> findById(@PathVariable String id) {
        log.info("Fetching Postman document with ID: {}", id);
        return postmanService.findById(id)
                .map(document -> ResponseEntity.ok(document))
                .defaultIfEmpty(ResponseEntity.notFound().build())
                .doOnError(error -> log.error("Error fetching document with ID: {}", id, error));
    }

    /**
     * Retrieves a Postman document by its name.
     * 
     * @param name the name of the document
     * @return a Mono containing the Postman document if found, or a 404 response if not found
     */
    @GetMapping("/by-name")
    public Mono<ResponseEntity<PostmanDocument>> findByName(@RequestParam String name) {
        log.info("Fetching Postman document with name: {}", name);
        return postmanService.findByName(name)
                .map(document -> ResponseEntity.ok(document))
                .defaultIfEmpty(ResponseEntity.notFound().build())
                .doOnError(error -> log.error("Error fetching document with name: {}", name, error));
    }

    /**
     * Saves or updates a Postman document.
     * 
     * @param document the Postman document to save or update
     * @return a Mono containing the saved document
     */
    @PostMapping
    public Mono<ResponseEntity<Collection>> save(@Valid @RequestBody PostmanDocument document) {
        boolean isNew = document.getId() == null || document.getId().isEmpty();
        log.info("Saving Postman document: {}", isNew ? "New document" : "Updating document with ID " + document.getId());
        return postmanService.save(document)
                .map(saved -> ResponseEntity.status(isNew ? HttpStatus.CREATED : HttpStatus.OK).body(saved.getCollection()))
                .doOnError(error -> log.error("Error saving Postman document", error));
    }

    /**
     * Deletes a Postman document by its unique identifier.
     * 
     * @param id the unique identifier of the document
     * @return a Mono confirming the deletion
     */
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteById(@PathVariable String id) {
        log.info("Deleting Postman document with ID: {}", id);
        return postmanService.deleteById(id)
                .then(Mono.fromCallable(() -> ResponseEntity.noContent().<Void>build())) 
                .doOnError(error -> log.error("Error deleting Postman document with ID: {}", id, error));
    }

    /**
     * Imports a Postman collection from a file.
     * 
     * @param file        the file containing the collection data
     * @param name        the name of the document
     * @param team        the team owning the document
     * @param createdBy   the user creating the document
     * @param description optional description for the document
     * @return a Mono containing the imported collection
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<Collection>> importFromFile(
            @RequestPart("file") FilePart file,
            @RequestPart("name") String name,
            @RequestPart("team") String team,
            @RequestPart("createdBy") String createdBy,
            @RequestPart(value = "description", required = false) String description) {
        log.info("Importing Postman collection from file: {}", file.filename());
        return postmanService.importFromFile(file, name, description, team, createdBy)
                .map(document -> ResponseEntity.status(HttpStatus.CREATED).body(document.getCollection()))
                .doOnError(error -> log.error("Error importing Postman collection from file", error));
    }

    /**
     * Imports a Postman collection from raw JSON content.
     * 
     * @param content the raw JSON content of the collection
     * @param name        the name of the document
     * @param team        the team owning the document
     * @param createdBy   the user creating the document
     * @param description optional description for the document
     * @return a Mono containing the imported PostmanDocument
     */
    @PostMapping(value = "/import-content", consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_PLAIN_VALUE})
    public Mono<ResponseEntity<PostmanDocument>> importFromContent(
            @RequestBody String content,
            @RequestParam("name") String name,
            @RequestParam("team") String team,
            @RequestParam("createdBy") String createdBy,
            @RequestParam(value = "description", required = false) String description) {
        log.info("Importing Postman collection from JSON content");
        return postmanService.importFromContent(content, name, description, team, createdBy)
                .map(imported -> ResponseEntity.status(HttpStatus.CREATED).body(imported))
                .doOnError(error -> log.error("Error importing Postman collection from JSON", error));
    }

}
