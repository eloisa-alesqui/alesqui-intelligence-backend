package es.alesqui.postmangpt.service.unification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import es.alesqui.postmangpt.annotation.HandleApiUnificationException;
import es.alesqui.postmangpt.model.postman.PostmanDocument;
import es.alesqui.postmangpt.model.swagger.SwaggerDocument;
import es.alesqui.postmangpt.model.unified.UnifiedApiDocument;
import es.alesqui.postmangpt.model.unified.UnifiedEndpoint;
import es.alesqui.postmangpt.repository.UnifiedApiRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApiUnificationService {

    private final SwaggerProcessingService swaggerProcessingService;
    private final PostmanProcessingService postmanProcessingService;
    private final EndpointUnificationService endpointUnificationService;
    private final UnifiedApiRepository repository;

    @HandleApiUnificationException
    public UnifiedApiDocument unifyApiDocuments(SwaggerDocument swaggerDoc, PostmanDocument postmanDoc) {
        log.info("Starting unification of API documents: {}", swaggerDoc.getName());

        UnifiedApiDocument.UnifiedApiDocumentBuilder builder = UnifiedApiDocument.builder()
            .name(swaggerDoc.getName())
            .description(swaggerDoc.getDescription())
            .sourceSwaggerId(swaggerDoc.getId())
            .sourcePostmanId(postmanDoc.getId())
            .tags(mergeTags(swaggerDoc.getTags(), postmanDoc.getTags()))
            .team(swaggerDoc.getTeam() != null ? swaggerDoc.getTeam() : postmanDoc.getTeam())
            .createdBy(swaggerDoc.getCreatedBy())
            .active(swaggerDoc.isActive() && postmanDoc.isActive());

        // Extraer información de Swagger
        swaggerProcessingService.extractSwaggerInfo(builder, swaggerDoc.getOpenApi());

        // Extraer información de Postman
        postmanProcessingService.mergePostmanInfo(builder, postmanDoc.getCollection());

        // Fusionar endpoints
        List<UnifiedEndpoint> endpoints = endpointUnificationService.mergeEndpoints(
            swaggerDoc.getOpenApi(), postmanDoc.getCollection()
        );
        builder.endpoints(endpoints);

        UnifiedApiDocument unifiedDoc = builder.build();
        log.info("API unification completed: {}", unifiedDoc.getName());
        return unifiedDoc;
    }

    /**
	 * Merges two lists of tags into a single list, removing duplicates and
	 * preserving order.
	 * 
	 * @param tags1 The first list of tags.
	 * @param tags2 The second list of tags.
	 * @return A merged list of unique tags.
	 */
	@HandleApiUnificationException
	private List<String> mergeTags(List<String> tags1, List<String> tags2) {
		log.debug("Merging tags from two sources");

		Set<String> merged = new LinkedHashSet<>();

		if (tags1 != null) {
			tags1.stream().filter(tag -> tag != null && !tag.trim().isEmpty()).forEach(tag -> merged.add(tag.trim()));
		}
		if (tags2 != null) {
			tags2.stream().filter(tag -> tag != null && !tag.trim().isEmpty()).forEach(tag -> merged.add(tag.trim()));
		}

		return new ArrayList<>(merged);
	}

	/**
     * Saves a Unified API document.
     * 
     * @param document the UnifiedApiDocument to save
     * @return a Mono of the saved UnifiedApiDocument
     */
    public Mono<UnifiedApiDocument> save(UnifiedApiDocument document) {
        document.setUpdatedAt(Instant.now());
        if (document.getCreatedAt() == null) {
            document.setCreatedAt(Instant.now());
        }

        log.info("Saving collection: {}", document.getName());
        return repository.save(document)
                .doOnSuccess(saved -> log.info("UnifiedApiDocument saved with ID: {}", saved.getId()))
                .doOnError(error -> log.error("Error saving collection: {}", document.getName(), error));
    }
}

