package es.alesqui.intelligence.service;

import es.alesqui.intelligence.config.properties.PostmanCollectionProperties;
import es.alesqui.intelligence.model.api_spec.ApiSpecDocument;
import es.alesqui.intelligence.repository.ApiSpecRepository;
import es.alesqui.intelligence.util.ValidationChain;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Abstract base class centralising the shared import pipeline and CRUD operations
 * for API specification documents (Swagger, Postman).
 *
 * Subclasses provide format-specific hooks via the abstract methods below.
 *
 * @param <D> document type (e.g. SwaggerDocument, PostmanDocument)
 * @param <P> parsed payload type (e.g. OpenAPI, Collection)
 */
@Slf4j
public abstract class AbstractApiSpecService<D extends ApiSpecDocument, P> {

    protected final ApiSpecRepository<D> repository;
    protected final PostmanCollectionProperties properties;

    protected AbstractApiSpecService(ApiSpecRepository<D> repository, PostmanCollectionProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    // -------------------------------------------------------------------------
    // Abstract hooks — implemented by each subclass
    // -------------------------------------------------------------------------

    /**
     * Parse raw content into a format-specific payload.
     *
     * @param content     raw file or request body content
     * @param sourceHint  filename (file import) or contentType (content import) or null
     */
    protected abstract Mono<P> parseContent(String content, String sourceHint);

    /**
     * Build a document entity from the parsed payload and request metadata.
     */
    protected abstract D buildDocument(P parsed, String name, String description,
                                       String team, String createdBy, String originalFileName);

    /**
     * Validate the document before saving. Throw {@link IllegalArgumentException} on failure.
     */
    protected abstract void validateDocument(D document);

    /**
     * File extensions accepted for file imports, e.g. {@code ".json"} or {@code ".json", ".yaml", ".yml"}.
     */
    protected abstract String[] allowedFileExtensions();

    /**
     * Human-readable document type name used in log messages, e.g. {@code "Swagger"} or {@code "Postman"}.
     */
    protected abstract String documentTypeName();

    // -------------------------------------------------------------------------
    // Shared CRUD
    // -------------------------------------------------------------------------

    public Flux<D> findAll() {
        log.debug("Fetching all {} documents...", documentTypeName());
        return repository.findAll()
                .doOnNext(doc -> log.debug("Retrieved {} document: {}", documentTypeName(), doc.getName()))
                .doOnError(error -> log.error("Error fetching {} documents", documentTypeName(), error));
    }

    public Mono<D> findById(String id) {
        log.debug("Fetching {} document by ID: {}", documentTypeName(), id);
        return repository.findById(id)
                .doOnNext(doc -> log.info("Found {} document: {}", documentTypeName(), doc.getName()))
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        documentTypeName() + " document not found with ID: " + id)))
                .doOnError(error -> log.error("Error fetching {} document by ID: {}", documentTypeName(), id, error));
    }

    public Mono<D> findByName(String name) {
        log.debug("Fetching {} document by name: {}", documentTypeName(), name);
        return repository.findByName(name)
                .doOnNext(doc -> log.debug("Found {} document: {}", documentTypeName(), doc.getName()))
                .doOnError(error -> log.error("Error fetching {} document by name: {}", documentTypeName(), name, error));
    }

    public Mono<D> save(D document) {
        validateDocument(document);

        document.setUpdatedAt(LocalDateTime.now());
        if (document.getCreatedAt() == null) {
            document.setCreatedAt(LocalDateTime.now());
        }

        log.info("Saving {} document: {}", documentTypeName(), document.getName());
        return repository.save(document)
                .doOnSuccess(saved -> log.info("{} document saved with ID: {}", documentTypeName(), saved.getId()))
                .doOnError(error -> log.error("Error saving {} document: {}", documentTypeName(), document.getName(), error));
    }

    public Mono<Void> deleteById(String id) {
        log.info("Deleting {} document with ID: {}", documentTypeName(), id);
        return repository.deleteById(id)
                .doOnSuccess(unused -> log.info("{} document deleted with ID: {}", documentTypeName(), id))
                .doOnError(error -> log.error("Error deleting {} document with ID: {}", documentTypeName(), id, error));
    }

    public Mono<Void> deleteByName(String name) {
        log.info("Deleting {} document with name: {}", documentTypeName(), name);
        return repository.deleteByName(name)
                .doOnSuccess(unused -> log.info("{} document deleted with name: {}", documentTypeName(), name))
                .doOnError(error -> log.error("Error deleting {} document with name: {}", documentTypeName(), name, error));
    }

    // -------------------------------------------------------------------------
    // Shared import pipeline
    // -------------------------------------------------------------------------

    public Mono<D> importFromFile(FilePart file, String name, String description, String team, String createdBy) {
        log.info("Importing {} document from file: {}", documentTypeName(), file.filename());

        return validateFile(file)
                .then(file.content()
                        .reduce(new StringBuilder(), (builder, buffer) -> {
                            byte[] bytes = new byte[buffer.readableByteCount()];
                            buffer.read(bytes);
                            return builder.append(new String(bytes));
                        })
                        .map(StringBuilder::toString))
                .flatMap(fileContent -> parseContent(fileContent, file.filename()))
                .flatMap(parsed -> checkDuplicatesAndSave(
                        buildDocument(parsed, name, description, team, createdBy, file.filename())))
                .doOnSuccess(saved -> log.info("{} document imported with ID: {}", documentTypeName(), saved.getId()))
                .doOnError(error -> log.error("Error importing {} document from file: {}",
                        documentTypeName(), file.filename(), error));
    }

    /**
     * Content import pipeline — delegates from subclass {@code importFromContent} methods.
     *
     * @param sourceHint contentType string or null; passed through to {@link #parseContent}
     */
    protected Mono<D> doImportFromContent(String content, String name, String description,
                                          String team, String createdBy, String sourceHint) {
        log.info("Importing {} document from content...", documentTypeName());
        validateContent(content);

        return parseContent(content, sourceHint)
                .flatMap(parsed -> checkDuplicatesAndSave(
                        buildDocument(parsed, name, description, team, createdBy, null)))
                .doOnSuccess(saved -> log.info("{} document imported with ID: {}", documentTypeName(), saved.getId()))
                .doOnError(error -> log.error("Error importing {} document from content", documentTypeName(), error));
    }

    // -------------------------------------------------------------------------
    // Shared validation helpers
    // -------------------------------------------------------------------------

    private Mono<Void> validateFile(FilePart file) {
        return ValidationChain.start()
                .validateNotNull(file, "File cannot be null")
                .validateFileNotEmpty(file, "File cannot be empty")
                .validateFileExtension(file, allowedFileExtensions())
                .validateFileSize(file, properties.getMaxFileSize())
                .execute();
    }

    protected void validateContent(String content) {
        ValidationChain.start()
                .validateNotNull(content, "Content cannot be null")
                .validateNotEmpty(content, "Content cannot be empty")
                .validateJsonSize(content, properties.getMaxJsonSize())
                .execute();
    }

    private Mono<D> checkDuplicatesAndSave(D document) {
        return findByName(document.getName())
                .hasElement()
                .flatMap(exists -> {
                    if (exists) {
                        log.warn("Duplicate {} document found with name: {}", documentTypeName(), document.getName());
                        return Mono.error(new IllegalArgumentException("Collection with this name already exists"));
                    }
                    return save(document);
                });
    }
}
