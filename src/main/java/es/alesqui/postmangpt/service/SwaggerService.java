package es.alesqui.postmangpt.service;

import es.alesqui.postmangpt.config.properties.PostmanCollectionProperties;
import es.alesqui.postmangpt.model.swagger.SwaggerDocument;
import es.alesqui.postmangpt.repository.SwaggerRepository;
import es.alesqui.postmangpt.util.ValidationChain;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Service for managing Swagger/OpenAPI documents reactively.
 * 
 * This service provides functionality for CRUD operations, importing Swagger documents
 * from files or content, extracting endpoints, and generating API statistics.
 */
@Slf4j
@Service
public class SwaggerService {

    private final SwaggerRepository repository;
    private final ObjectMapper jsonObjectMapper;
    @Qualifier("yamlObjectMapper")
    private final ObjectMapper yamlObjectMapper;
    private final PostmanCollectionProperties properties;
    
	public SwaggerService(SwaggerRepository repository, ObjectMapper jsonObjectMapper,
			@Qualifier("yamlObjectMapper") ObjectMapper yamlObjectMapper, PostmanCollectionProperties properties) {
		this.repository = repository;
		this.jsonObjectMapper = jsonObjectMapper;
		this.yamlObjectMapper = yamlObjectMapper;
		this.properties = properties;
	}

    private static final Pattern YAML_PATTERN =
            Pattern.compile("^\\s*[a-zA-Z_][\\w-]*\\s*:\\s*.+", Pattern.MULTILINE);

    /**
     * Retrieves all Swagger documents from the database.
     * 
     * @return a Flux containing all SwaggerDocument objects
     */
    public Flux<SwaggerDocument> findAll() {
        log.debug("Fetching all Swagger documents...");
        return repository.findAll()
                .doOnNext(doc -> log.info("Retrieved Swagger document: {}", doc.getName()))
                .doOnError(error -> log.error("Error fetching Swagger documents", error));
    }

    /**
     * Retrieves a Swagger document by its unique identifier.
     * 
     * @param id the unique identifier of the Swagger document
     * @return a Mono containing the SwaggerDocument if found, otherwise an error
     */
    public Mono<SwaggerDocument> findById(String id) {
        log.debug("Fetching Swagger document by ID: {}", id);
        return repository.findById(id)
                .doOnNext(doc -> log.info("Found Swagger document: {}", doc.getName()))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Swagger document not found with ID: " + id)))
                .doOnError(error -> log.error("Error fetching Swagger document by ID: {}", id, error));
    }

    /**
     * Retrieves a Swagger document by its name.
     * 
     * @param name the name of the Swagger document
     * @return a Mono containing the SwaggerDocument if found, otherwise an empty Mono
     */
    public Mono<SwaggerDocument> findByName(String name) {
        log.debug("Fetching Swagger document by name: {}", name);
        return repository.findAll()
                .filter(doc -> name.equals(doc.getName()))
                .next()
                .switchIfEmpty(Mono.empty()) // Return an empty Mono if no collection is found
                .doOnNext(doc -> log.info("Found Swagger document: {}", doc.getName()))
                .doOnError(error -> log.error("Error fetching Swagger document by name: {}", name, error));
    }

    /**
     * Saves a Swagger document to the database.
     * 
     * @param document the SwaggerDocument to save
     * @return a Mono containing the saved SwaggerDocument
     */
    public Mono<SwaggerDocument> save(SwaggerDocument document) {
        validateDocument(document);

        document.setUpdatedAt(LocalDateTime.now());
        if (document.getCreatedAt() == null) {
            document.setCreatedAt(LocalDateTime.now());
        }

        log.info("Saving Swagger document: {}", document.getName());
        return repository.save(document)
                .doOnSuccess(saved -> log.info("Swagger document saved with ID: {}", saved.getId()))
                .doOnError(error -> log.error("Error saving Swagger document: {}", document.getName(), error));
    }

    /**
     * Deletes a Swagger document by its unique identifier.
     * 
     * @param id the unique identifier of the Swagger document
     * @return a Mono that completes when the deletion is finished
     */
    public Mono<Void> deleteById(String id) {
        log.info("Deleting Swagger document with ID: {}", id);
        return repository.deleteById(id)
                .doOnSuccess(unused -> log.info("Swagger document deleted with ID: {}", id))
                .doOnError(error -> log.error("Error deleting Swagger document with ID: {}", id, error));
    }

    /**
     * Imports a Swagger/OpenAPI specification from a file and wraps it in a SwaggerDocument.
     * 
     * @param file the file containing the OpenAPI data (JSON or YAML)
     * @param name the name for the Swagger document
     * @param description the description for the Swagger document
     * @param team the team owning this API documentation
     * @param createdBy the user creating this document
     * @return a Mono containing the imported and saved SwaggerDocument
     */
    public Mono<SwaggerDocument> importFromFile(FilePart file, String name, String description, String team, String createdBy) {
        log.info("Importing Swagger document from file: {}", file.filename());

        return validateFile(file) // Perform reactive validation
            .then(file.content() // Read the file content reactively
                .reduce(new StringBuilder(), (builder, buffer) -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    return builder.append(new String(bytes));
                })
                .map(StringBuilder::toString)) // Convert the content to a String
            .flatMap(fileContent -> {
                // Detect content type based on file extension
                String contentType = detectContentType(file.filename());
                return parseOpenAPIFromContent(fileContent, contentType); // Parse OpenAPI from file content
            })
            .flatMap(openAPI -> createAndSaveDocument(openAPI, name, description, team, createdBy, file.filename())) // Save the document
            .doOnSuccess(saved -> log.info("Swagger document imported with ID: {}", saved.getId()))
            .doOnError(error -> log.error("Error importing Swagger document from file: {}", file.filename(), error));
    }




    /**
     * Imports a Swagger/OpenAPI specification from a content string and wraps it in a SwaggerDocument.
     * 
     * @param content the JSON or YAML string containing the OpenAPI data
     * @param name the name for the Swagger document
     * @param description the description for the Swagger document
     * @param team the team owning this API documentation
     * @param createdBy the user creating this document
     * @param contentType the content type (e.g., application/json or application/yaml)
     * @return a Mono containing the imported and saved SwaggerDocument
     */
    public Mono<SwaggerDocument> importFromContent(String content, String name, String description, String team,
                                                   String createdBy, String contentType) {
        log.info("Importing Swagger document from content...");
        validateContent(content);

        return parseOpenAPIFromContent(content, contentType) 
                .flatMap(openAPI -> createAndSaveDocument(openAPI, name, description, team, createdBy, null)) 
                .doOnSuccess(saved -> log.info("Swagger document imported with ID: {}", saved.getId()))
                .doOnError(error -> log.error("Error importing Swagger document from content", error));
    }

    

    /**
     * Validates the provided SwaggerDocument to ensure it is not null and contains the required fields.
     * 
     * @param document the SwaggerDocument to validate
     * @throws IllegalArgumentException if the document, its OpenAPI specification, or its info is null
     */
    private void validateDocument(SwaggerDocument document) {
        if (document == null || document.getOpenApi() == null || document.getOpenApi().getInfo() == null) {
            throw new IllegalArgumentException("Invalid SwaggerDocument: Missing required fields");
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
                .validateFileExtension(file, ".json", ".yaml", ".yml")
                .validateFileSize(file, properties.getMaxFileSize())
                .execute(); 
    }

    /**
     * Validates the provided content string to ensure it meets the required constraints (non-null, non-empty, and within size limits).
     * 
     * @param content the content string to validate
     * @throws IllegalArgumentException if the content does not meet the validation criteria
     */
    private void validateContent(String content) {
        ValidationChain.start()
                .validateNotNull(content, "Content cannot be null")
                .validateNotEmpty(content, "Content cannot be empty")
                .validateJsonSize(content, properties.getMaxJsonSize())
                .execute();
    }

    /**
     * Parses an OpenAPI specification from the provided content string.
     *
     * @param content the content string containing the OpenAPI specification (JSON or YAML)
     * @param contentType the content type (e.g., application/json or application/yaml)
     * @return the parsed OpenAPI object
     * @throws IllegalArgumentException if there is an error parsing the content
     */
    private Mono<OpenAPI> parseOpenAPIFromContent(String content, String contentType) {
        return Mono.fromCallable(() -> {
            // Determine whether the content is YAML or JSON
            ObjectMapper mapper = isYamlContent(content, contentType)
                    ? yamlObjectMapper // YAML parser
                    : jsonObjectMapper; // JSON parser 

            log.info("yamlObjectMapper: {}", yamlObjectMapper.getFactory().getClass().getName());
            log.info("jsonObjectMapper: {}", jsonObjectMapper.getFactory().getClass().getName());

            log.info("Using ObjectMapper: {}", mapper.getFactory().getClass().getName());
            
            // Parse the content into an OpenAPI object
            return mapper.readValue(content, OpenAPI.class);
        }).onErrorMap(IOException.class, ex -> 
            new IllegalArgumentException("Failed to parse OpenAPI content", ex) // Wrap IOException in a more descriptive exception
        );
    }

    /**
     * Determines if the provided content is in YAML format based on its content type or structure.
     * 
     * @param content the content string to check
     * @param contentType the content type (e.g., application/json or application/yaml)
     * @return true if the content is in YAML format, false otherwise
     */
    private boolean isYamlContent(String content, String contentType) {
    	boolean isYamlContent = false;
        if (contentType != null && contentType.toLowerCase().contains("yaml")) {
        	isYamlContent = true;
        }
        isYamlContent = YAML_PATTERN.matcher(content).find();
                
        return isYamlContent;
    }

    /**
     * Creates a SwaggerDocument from the provided OpenAPI specification and metadata, and saves it to the database.
     * 
     * @param openAPI the OpenAPI specification to wrap in a SwaggerDocument
     * @param name the name for the SwaggerDocument
     * @param description the description for the SwaggerDocument
     * @param team the team owning the SwaggerDocument
     * @param createdBy the user creating the SwaggerDocument
     * @param originalFileName the original filename, if applicable
     * @return a Mono containing the saved SwaggerDocument
     */
    private Mono<SwaggerDocument> createAndSaveDocument(OpenAPI openAPI, String name, String description, String team, String createdBy, String originalFileName) {
        String documentName = Optional.ofNullable(name).filter(n -> !n.isBlank()).orElseGet(() -> extractOpenAPITitle(openAPI));
        SwaggerDocument document = SwaggerDocument.builder()
                .name(documentName)
                .description(Optional.ofNullable(description).orElse("No description available"))
                .team(team)
                .createdBy(createdBy)
                .originalFileName(originalFileName)
                .openApi(openAPI)
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
    private Mono<SwaggerDocument> checkDuplicatesAndSave(SwaggerDocument document) {
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
     * Extracts the title from the provided OpenAPI specification.
     * 
     * @param openAPI the OpenAPI specification
     * @return the title of the OpenAPI specification, or "Unnamed API" if not available
     */
    private String extractOpenAPITitle(OpenAPI openAPI) {
        return Optional.ofNullable(openAPI.getInfo())
                .map(Info::getTitle)
                .orElse("Unnamed API");
    }
    
    /**
     * Detects the content type of a file based on its filename extension.
     * This method checks the file extension to determine whether the file is a YAML or JSON file.
     * If the file extension is not recognized, it throws an IllegalArgumentException.
     *
     * @param filename the name of the file whose content type is to be detected
     * @return the content type as a string, either "application/x-yaml" for YAML files
     *         or "application/json" for JSON files
     * @throws IllegalArgumentException if the file extension is not supported
     */
    private String detectContentType(String filename) {
        if (filename.endsWith(".yaml") || filename.endsWith(".yml")) {
            return "application/x-yaml";
        } else if (filename.endsWith(".json")) {
            return "application/json";
        } else {
            throw new IllegalArgumentException("Unsupported file type: " + filename);
        }
    }

}
