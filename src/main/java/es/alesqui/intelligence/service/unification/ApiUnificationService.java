package es.alesqui.intelligence.service.unification;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import es.alesqui.intelligence.annotation.HandleApiUnificationException;
import es.alesqui.intelligence.config.ChatConfiguration;
import es.alesqui.intelligence.model.postman.PostmanDocument;
import es.alesqui.intelligence.model.swagger.SwaggerDocument;
import es.alesqui.intelligence.model.unified.UnifiedApiDocument;
import es.alesqui.intelligence.model.unified.UnifiedEndpoint;
import es.alesqui.intelligence.repository.UnifiedApiRepository;
import es.alesqui.intelligence.service.chat.SpringAIService;
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
    private final SpringAIService springAIService;
    private final ChatConfiguration chatConfig;

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

        swaggerProcessingService.extractSwaggerInfo(builder, swaggerDoc.getOpenApi());

        postmanProcessingService.mergePostmanInfo(builder, postmanDoc.getCollection());

        List<UnifiedEndpoint> endpoints = endpointUnificationService.mergeEndpoints(
            swaggerDoc.getOpenApi(), postmanDoc.getCollection()
        );
        builder.endpoints(endpoints);
        
        if (endpoints != null && !endpoints.isEmpty()) {
            try {
                String summary = generateCapabilitiesSummary(endpoints);
                builder.capabilitiesSummary(summary);
                log.info("Successfully generated AI capabilities summary for API: {}", swaggerDoc.getName());
            } catch (Exception e) {
                log.error("Failed to generate AI capabilities summary for API: {}. Summary will be null.", swaggerDoc.getName(), e);
                // Opcional: puedes poner un resumen por defecto o reintentar.
                builder.capabilitiesSummary("Could not determine capabilities.");
            }
        }

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
    
    /**
     * Calls the AI to generate a summary of the API's capabilities based on its endpoints.
     * @param endpoints The list of unified endpoints for the API.
     * @return A string with the key capabilities separated by commas.
     */
    private String generateCapabilitiesSummary(List<UnifiedEndpoint> endpoints) {
        // Prepare the input for the AI: a simple list of the operation summaries
        String endpointSummaries = endpoints.stream()
            .map(e -> "- " + (e.getSummary() != null ? e.getSummary() : e.getOperationId()))
            .filter(s -> !s.isBlank())
            .limit(30) // Limit to the first 30 endpoints to not exceed the prompt's context
            .collect(Collectors.joining("\n"));

        // Create a specific prompt for the summarization task
        String systemPrompt = "You are an expert API documentation analyst. Your task is to summarize the main capabilities of an API based on a list of its operations.";
        String userPrompt = String.format(
            """
            Based on the following list of API operations, generate a concise summary of its main business capabilities.
            Provide the result as 5 to 7 keywords or short phrases, separated by commas.
            Focus on the business concepts (e.g., user management, product catalog, payment processing).

            List of operations:
            %s

            Capabilities Summary:
            """, endpointSummaries);

        // Call the AI without memory and block the result.
        // .block() is used because this is a data ingestion process, not a real-time user request.
        return springAIService.chatWithoutMemory(systemPrompt, userPrompt)
                .block(chatConfig.getUtilityAi().getTimeout()); 
    }
}

