package es.alesqui.intelligence.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import es.alesqui.intelligence.config.properties.PostmanCollectionProperties;
import es.alesqui.intelligence.model.api_spec.postman.Collection;
import es.alesqui.intelligence.model.api_spec.postman.Info;
import es.alesqui.intelligence.model.api_spec.postman.PostmanDocument;
import es.alesqui.intelligence.repository.PostmanRepository;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Optional;

/**
 * Service for managing Postman collection documents reactively.
 *
 * Extends {@link AbstractApiSpecService} with Postman-specific parsing and document
 * construction. Only JSON format is supported.
 */
@Slf4j
@Service
public class PostmanService extends AbstractApiSpecService<PostmanDocument, Collection> {

    private final ObjectMapper objectMapper;

    public PostmanService(PostmanRepository repository, ObjectMapper objectMapper,
                          PostmanCollectionProperties properties) {
        super(repository, properties);
        this.objectMapper = objectMapper;
    }

    // -------------------------------------------------------------------------
    // Public API — preserves the existing controller contract
    // -------------------------------------------------------------------------

    /**
     * Imports a Postman collection from a JSON string.
     */
    public Mono<PostmanDocument> importFromContent(String content, String name, String description,
                                                   String team, String createdBy) {
        return doImportFromContent(content, name, description, team, createdBy, null);
    }

    // -------------------------------------------------------------------------
    // Abstract method implementations
    // -------------------------------------------------------------------------

    @Override
    protected Mono<Collection> parseContent(String content, String sourceHint) {
        return Mono.fromCallable(() -> objectMapper.readValue(content, Collection.class))
                .onErrorMap(IOException.class,
                        ex -> new IllegalArgumentException("Failed to parse Collection content", ex));
    }

    @Override
    protected PostmanDocument buildDocument(Collection collection, String name, String description,
                                            String team, String createdBy, String originalFileName) {
        String documentName = Optional.ofNullable(name).filter(n -> !n.isBlank())
                .orElseGet(() -> Optional.ofNullable(collection.getInfo())
                        .map(Info::getName).orElse("Unnamed Collection"));
        String documentDescription = Optional.ofNullable(description)
                .orElseGet(() -> Optional.ofNullable(collection.getInfo())
                        .map(Info::getDescription)
                        .map(desc -> desc.getContent())
                        .orElse("No description available"));
        return PostmanDocument.builder()
                .name(documentName)
                .description(documentDescription)
                .collection(collection)
                .team(team)
                .createdBy(createdBy)
                .originalFileName(originalFileName)
                .active(true)
                .build();
    }

    @Override
    protected void validateDocument(PostmanDocument document) {
        if (document == null || document.getCollection() == null
                || document.getCollection().getInfo() == null) {
            throw new IllegalArgumentException("Invalid PostmanDocument: Missing required fields");
        }
    }

    @Override
    protected String[] allowedFileExtensions() {
        return new String[]{".json"};
    }

    @Override
    protected String documentTypeName() {
        return "Postman";
    }
}
