package es.alesqui.intelligence.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import es.alesqui.intelligence.config.properties.PostmanCollectionProperties;
import es.alesqui.intelligence.model.api_spec.swagger.SwaggerDocument;
import es.alesqui.intelligence.repository.SwaggerRepository;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Service for managing Swagger/OpenAPI documents reactively.
 *
 * Extends {@link AbstractApiSpecService} with Swagger-specific parsing and document
 * construction. The only format-specific concern is YAML vs JSON detection.
 */
@Slf4j
@Service
public class SwaggerService extends AbstractApiSpecService<SwaggerDocument, OpenAPI> {

    private static final Pattern YAML_PATTERN =
            Pattern.compile("^\\s*[a-zA-Z_][\\w-]*\\s*:\\s*.+", Pattern.MULTILINE);

    private final ObjectMapper jsonObjectMapper;
    private final ObjectMapper yamlObjectMapper;

    public SwaggerService(SwaggerRepository repository,
                          ObjectMapper jsonObjectMapper,
                          @Qualifier("yamlObjectMapper") ObjectMapper yamlObjectMapper,
                          PostmanCollectionProperties properties) {
        super(repository, properties);
        this.jsonObjectMapper = jsonObjectMapper;
        this.yamlObjectMapper = yamlObjectMapper;
    }

    // -------------------------------------------------------------------------
    // Public API — preserves the existing controller contract
    // -------------------------------------------------------------------------

    /**
     * Imports a Swagger/OpenAPI specification from a content string.
     *
     * @param contentType MIME type hint used for YAML vs JSON detection
     */
    public Mono<SwaggerDocument> importFromContent(String content, String name, String description,
                                                   String team, String createdBy, String contentType) {
        return doImportFromContent(content, name, description, team, createdBy, contentType);
    }

    // -------------------------------------------------------------------------
    // Abstract method implementations
    // -------------------------------------------------------------------------

    @Override
    protected Mono<OpenAPI> parseContent(String content, String sourceHint) {
        return Mono.fromCallable(() -> {
            ObjectMapper mapper = isYamlContent(content, sourceHint) ? yamlObjectMapper : jsonObjectMapper;
            return mapper.readValue(content, OpenAPI.class);
        }).onErrorMap(IOException.class,
                ex -> new IllegalArgumentException("Failed to parse OpenAPI content", ex));
    }

    @Override
    protected SwaggerDocument buildDocument(OpenAPI openAPI, String name, String description,
                                            String team, String createdBy, String originalFileName) {
        String documentName = Optional.ofNullable(name).filter(n -> !n.isBlank())
                .orElseGet(() -> Optional.ofNullable(openAPI.getInfo()).map(Info::getTitle).orElse("Unnamed API"));
        return SwaggerDocument.builder()
                .name(documentName)
                .description(Optional.ofNullable(description).orElse("No description available"))
                .team(team)
                .createdBy(createdBy)
                .originalFileName(originalFileName)
                .openApi(openAPI)
                .active(true)
                .build();
    }

    @Override
    protected void validateDocument(SwaggerDocument document) {
        if (document == null || document.getOpenApi() == null || document.getOpenApi().getInfo() == null) {
            throw new IllegalArgumentException("Invalid SwaggerDocument: Missing required fields");
        }
    }

    @Override
    protected String[] allowedFileExtensions() {
        return new String[]{".json", ".yaml", ".yml"};
    }

    @Override
    protected String documentTypeName() {
        return "Swagger";
    }

    // -------------------------------------------------------------------------
    // Swagger-specific helpers
    // -------------------------------------------------------------------------

    private boolean isYamlContent(String content, String sourceHint) {
        boolean yamlByHint = sourceHint != null && sourceHint.toLowerCase().contains("yaml");
        boolean yamlByPattern = YAML_PATTERN.matcher(content).find();
        return yamlByHint || yamlByPattern;
    }

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
