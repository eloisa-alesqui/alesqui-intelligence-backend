package es.alesqui.postmangpt.service;

import es.alesqui.postmangpt.config.properties.PostmanCollectionProperties;
import es.alesqui.postmangpt.dto.EndpointInfo;
import es.alesqui.postmangpt.helper.PostmanEndpointExtractor;
import es.alesqui.postmangpt.model.postman.Collection;
import es.alesqui.postmangpt.model.postman.Info;
import es.alesqui.postmangpt.model.postman.PostmanDocument;
import es.alesqui.postmangpt.repository.PostmanRepository;
import es.alesqui.postmangpt.util.ValidationChain;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing Postman collection documents using reactive programming.
 * Provides methods to perform CRUD operations, import collections from JSON,
 * extract endpoints, and generate statistics.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostmanService {

    private final PostmanRepository repository;
    private final ObjectMapper objectMapper;
    private final PostmanEndpointExtractor endpointExtractor;
    private final PostmanCollectionProperties properties;

    /**
     * Retrieves all Postman collection documents.
     * 
     * @return a Flux of PostmanDocument representing all collections
     */
    public Flux<PostmanDocument> findAll() {
        log.debug("Fetching all Postman collections...");
        return repository.findAll()
        		.doOnNext(doc -> log.debug("Retrieved collection: {}", doc.getName()))
                .doOnError(error -> log.error("Error fetching collections", error));
    }

    /**
     * Retrieves a Postman collection document by its unique identifier.
     * 
     * @param id the unique identifier of the collection
     * @return a Mono of PostmanDocument if found, otherwise an error
     */
    public Mono<PostmanDocument> findById(String id) {
        log.debug("Fetching Postman collection by ID: {}", id);
        return repository.findById(id)
                .doOnNext(doc -> log.info("Found collection: {}", doc.getName()))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Collection not found with ID: " + id)))
                .doOnError(error -> log.error("Error fetching collection by ID: {}", id, error));
    }

    /**
     * Retrieves a Postman collection document by its name.
     *
     * @param name the name of the collection
     * @return a Mono of PostmanDocument if found, otherwise an empty Mono
     */
    public Mono<PostmanDocument> findByName(String name) {
        log.debug("Fetching Postman collection by name: {}", name);
        return repository.findAll()
                .filter(doc -> name.equals(doc.getName()))
                .next()
                .switchIfEmpty(Mono.empty()) // Return an empty Mono if no collection is found
                .doOnNext(doc -> log.info("Found collection: {}", doc.getName()))
                .doOnError(error -> log.error("Error fetching collection by name: {}", name, error));
    }

    /**
     * Saves a Postman collection document.
     * 
     * @param document the PostmanDocument to save
     * @return a Mono of the saved PostmanDocument
     */
    public Mono<PostmanDocument> save(PostmanDocument document) {
        validateDocument(document);

        document.setUpdatedAt(LocalDateTime.now());
        if (document.getCreatedAt() == null) {
            document.setCreatedAt(LocalDateTime.now());
        }

        log.info("Saving collection: {}", document.getName());
        return repository.save(document)
                .doOnSuccess(saved -> log.info("Collection saved with ID: {}", saved.getId()))
                .doOnError(error -> log.error("Error saving collection: {}", document.getName(), error));
    }

    /**
     * Deletes a Postman collection document by its unique identifier.
     * 
     * @param id the unique identifier of the collection to delete
     * @return a Mono that completes when the deletion is successful
     */
    public Mono<Void> deleteById(String id) {
        log.info("Deleting collection with ID: {}", id);
        return repository.deleteById(id)
                .doOnSuccess(unused -> log.info("Collection deleted with ID: {}", id))
                .doOnError(error -> log.error("Error deleting collection with ID: {}", id, error));
    }

    /**
     * Imports a Postman collection from a JSON file.
     * 
     * @param file the file containing the JSON data
     * @param name the name for the collection document
     * @param description the description for the collection document
     * @param team the team owning this collection
     * @param createdBy the user creating this collection
     * @return a Mono of the imported and saved PostmanDocument
     */
    public Mono<PostmanDocument> importFromFile(FilePart file, String name, String description, String team, String createdBy) {
        log.info("Importing collection from file: {}", file.filename());
        
        return validateFile(file) // Perform reactive validation
                .then(file.content() // Read the file content reactively
                    .reduce(new StringBuilder(), (builder, buffer) -> {
                        byte[] bytes = new byte[buffer.readableByteCount()];
                        buffer.read(bytes);
                        return builder.append(new String(bytes));
                    })
                    .map(StringBuilder::toString)) // Convert the content to a String
                .flatMap(fileContent -> {
                    return parseCollectionFromContent(fileContent); // Parse OpenAPI from file content
                })
                .flatMap(collection -> createAndSaveDocument(collection, name, description, team, createdBy, file.filename())) // Save the document
                .doOnSuccess(saved -> log.info("Postman document imported with ID: {}", saved.getId()))
                .doOnError(error -> log.error("Error importing Postman document from file: {}", file.filename(), error));
 
    }

    /**
     * Imports a Postman collection from a JSON string.
     * 
     * @param content the JSON string containing the collection data
     * @param name the name for the collection document
     * @param description the description for the collection document
     * @param team the team owning this collection
     * @param createdBy the user creating this collection
     * @return a Mono of the imported and saved PostmanDocument
     */
    public Mono<PostmanDocument> importFromContent(String content, String name, String description, String team, String createdBy) {
        log.info("Importing collection from JSON content...");
        validateJsonContent(content);

        return parseCollectionFromContent(content)
                .flatMap(collection -> createAndSaveDocument(collection, name, description, team, createdBy, null))
                .doOnSuccess(saved -> log.info("Postman document imported with ID: {}", saved.getId()))
                .doOnError(error -> log.error("Error importing collection from JSON", error));
    }

    /**
     * Extracts endpoints from a Postman collection document.
     * 
     * @param collectionId the unique identifier of the collection
     * @return a Mono of a list of EndpointInfo objects representing the endpoints
     */
    public Mono<List<EndpointInfo>> extractEndpoints(String collectionId) {
        log.info("Extracting endpoints for collection ID: {}", collectionId);

        return findById(collectionId)
                .map(document -> {
                    log.debug("Extracting endpoints from collection: {}", document.getName());
                    return endpointExtractor.extractEndpoints(document.getCollection().getItem());
                })
                .doOnSuccess(endpoints -> log.info("Extracted {} endpoints for collection ID: {}", endpoints.size(), collectionId))
                .doOnError(error -> log.error("Error extracting endpoints for collection ID: {}", collectionId, error));
    }

    
    
    /**
     * Creates and saves a new PostmanDocument from a Collection.
     * 
     * @param collection the parsed Collection object
     * @param name the name for the document
     * @param description the description for the document
     * @param team the team owning this collection
     * @param createdBy the user creating this collection
     * @param originalFileName the original filename, if applicable
     * @return a Mono of the created and saved PostmanDocument
     */
    private Mono<PostmanDocument> createAndSaveDocument(Collection collection, String name, String description, String team, String createdBy, String originalFileName) {
        String documentName = Optional.ofNullable(name).filter(n -> !n.isBlank()).orElseGet(() -> extractCollectionName(collection));
        PostmanDocument document = PostmanDocument.builder()
                .name(documentName)
                .description(Optional.ofNullable(description).orElseGet(() -> extractCollectionDescription(collection)))
                .collection(collection)
                .team(team)
                .createdBy(createdBy)
                .originalFileName(originalFileName)
                .active(true)
                .build();

        return checkDuplicatesAndSave(document);
    }

    /**
     * Checks for duplicate collections by name and saves the document if no duplicates are found.
     * 
     * @param document the collection document to validate and save
     * @return a Mono of the saved PostmanDocument or an error if a duplicate is found
     */
    private Mono<PostmanDocument> checkDuplicatesAndSave(PostmanDocument document) {
        return findByName(document.getName())
                .hasElement()
                .flatMap(exists -> {
                    if (exists) {
                        log.warn("Duplicate collection found with name: {}", document.getName());
                        return Mono.error(new IllegalArgumentException("Collection with this name already exists"));
                    }
                    return save(document);
                });
    }

    /**
     * Parses a collection from a JSON string.
     * 
     * @param content the JSON string
     * @return the parsed Collection object
     * @throws IOException if an error occurs during parsing
     */
    private Mono<Collection> parseCollectionFromContent(String content) {
    	return Mono.fromCallable(() -> {
            return objectMapper.readValue(content, Collection.class);
        }).onErrorMap(IOException.class, ex -> new IllegalArgumentException("Failed to parse Collection content", ex));
    }

    /**
     * Extracts the name of a collection safely.
     * 
     * @param collection the Collection object
     * @return the name of the collection or "Unnamed Collection" if not available
     */
    private String extractCollectionName(Collection collection) {
        return Optional.ofNullable(collection.getInfo())
                .map(Info::getName)
                .orElse("Unnamed Collection");
    }

    /**
     * Extracts the description of a collection safely.
     * 
     * @param collection the Collection object
     * @return the description of the collection or "No description available" if not available
     */
    private String extractCollectionDescription(Collection collection) {
        return Optional.ofNullable(collection.getInfo())
                .map(Info::getDescription)
                .map(desc -> desc.getContent())
                .orElse("No description available");
    }

    /**
     * Validates a PostmanDocument.
     * 
     * @param document the document to validate
     * @throws IllegalArgumentException if validation fails
     */
    private void validateDocument(PostmanDocument document) {
        if (document == null || document.getCollection() == null || document.getCollection().getInfo() == null) {
            throw new IllegalArgumentException("Invalid PostmanDocument: Missing required fields");
        }
    }

    /**
     * Validates the provided file to ensure it meets the required constraints (non-null, non-empty, valid extension, and size).
     * 
     * @param file the FilePart to validate
     * @throws IllegalArgumentException if the file does not meet the validation criteria
     */
    private Mono<Void> validateFile(FilePart file) {
        return ValidationChain.start()
                .validateNotNull(file, "File cannot be null")
                .validateFileNotEmpty(file, "File cannot be empty")
                .validateFileExtension(file, ".json")
                .validateFileSize(file, properties.getMaxFileSize())
                .execute(); 
    }

    /**
     * Validates JSON content against format, size, and content requirements.
     * 
     * @param jsonContent the JSON content to validate
     */
    private void validateJsonContent(String jsonContent) {
        ValidationChain.start()
                .validateNotNull(jsonContent, "JSON content cannot be null")
                .validateNotEmpty(jsonContent, "JSON content cannot be empty")
                .validateJsonSize(jsonContent, properties.getMaxJsonSize())
                .execute();
    }
}
