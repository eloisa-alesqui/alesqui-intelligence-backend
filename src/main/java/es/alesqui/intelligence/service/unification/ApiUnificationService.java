package es.alesqui.intelligence.service.unification;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import es.alesqui.intelligence.annotation.HandleApiUnificationException;
import es.alesqui.intelligence.config.ChatConfig;
import es.alesqui.intelligence.event.ApiCreatedEvent;
import es.alesqui.intelligence.model.api_spec.postman.Collection;
import es.alesqui.intelligence.model.api_spec.postman.PostmanDocument;
import es.alesqui.intelligence.model.api_spec.swagger.SwaggerDocument;
import es.alesqui.intelligence.model.api_spec.unified.GeneratedCapabilities;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedApiDocument;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedEndpoint;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedParameter;
import es.alesqui.intelligence.repository.UnifiedApiRepository;
import es.alesqui.intelligence.security.SecurityUtils;
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
    private final ChatConfig chatConfig;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Unifies a Swagger document with an optional Postman document into a single UnifiedApiDocument.
     * This method is null-safe and will proceed with unification even if the Postman document is not provided.
     *
     * @param swaggerDoc The mandatory Swagger document.
     * @param postmanDoc The optional Postman document, which can be {@code null}.
     * @return The unified API document.
     */
    @HandleApiUnificationException
    public UnifiedApiDocument unifyApiDocuments(SwaggerDocument swaggerDoc, @Nullable PostmanDocument postmanDoc) {
        if (postmanDoc == null) {
            log.info("Starting unification for API '{}' with Swagger document only.", swaggerDoc.getName());
        } else {
            log.info("Starting unification for API '{}' with both Swagger and Postman documents.", swaggerDoc.getName());
        }
        
        // Conditionally extract properties from postmanDoc only if it's not null.
        String postmanId = (postmanDoc != null) ? postmanDoc.getId() : null;
        List<String> postmanTags = (postmanDoc != null) ? postmanDoc.getTags() : null;
        String postmanTeam = (postmanDoc != null) ? postmanDoc.getTeam() : null;
        boolean isPostmanActive = (postmanDoc != null) ? postmanDoc.isActive() : true; 
        Collection postmanCollection = (postmanDoc != null) ? postmanDoc.getCollection() : null;

        UnifiedApiDocument.UnifiedApiDocumentBuilder builder = UnifiedApiDocument.builder()
            .name(swaggerDoc.getName())
            .description(swaggerDoc.getDescription())
            .sourceSwaggerId(swaggerDoc.getId())
            .sourcePostmanId(postmanId) 
            .team(swaggerDoc.getTeam() != null ? swaggerDoc.getTeam() : postmanTeam) 
            .createdBy(swaggerDoc.getCreatedBy())
            .active(swaggerDoc.isActive() && isPostmanActive); 

        // Process Swagger info 
        swaggerProcessingService.extractSwaggerInfo(builder, swaggerDoc.getOpenApi());

        // Process Postman info. The service itself should be null-safe, but we pass the safe variable.
        postmanProcessingService.mergePostmanInfo(builder, postmanCollection);

        // Merge endpoints. The service should handle a null collection.
        List<UnifiedEndpoint> endpoints = endpointUnificationService.mergeEndpoints(
            swaggerDoc.getOpenApi(), postmanCollection
        );
        builder.endpoints(endpoints);
        
        // Generate AI summary if endpoints were found 
        if (endpoints != null && !endpoints.isEmpty()) {
            try {
                String summary = generateCapabilitiesSummary(endpoints);
                builder.capabilitiesSummary(summary);
                log.info("Successfully generated AI capabilities summary for API: {}", swaggerDoc.getName());
            } catch (Exception e) {
                log.error("Failed to generate AI capabilities summary for API: {}. Summary will be null.", swaggerDoc.getName(), e);
                builder.capabilitiesSummary("Could not determine capabilities.");
            }
        }
        
        // Generate Capabilities
        try {
            GeneratedCapabilities capabilities = generateCapabilities(endpoints, swaggerDoc.getName());
            builder.capabilities(capabilities);
            log.info("Successfully generated capabilities for API: {}", swaggerDoc.getName());
        } catch (Exception e) {
            log.error("Failed to generate capabilities for API: {}. Examples will be null.", swaggerDoc.getName(), e);
            builder.capabilities(null); 
        }

        UnifiedApiDocument unifiedDoc = builder.build();
        log.info("API unification completed: {}", unifiedDoc.getName());
        return unifiedDoc;
    }

	/**
     * Saves a Unified API document.
     * For TRIAL users, automatically links the API to their workspace.
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
                .flatMap(savedDoc -> 
                    // Publish event to auto-link to TRIAL user workspace if applicable
                    SecurityUtils.getCurrentUsername()
                        .doOnNext(username -> {
                            eventPublisher.publishEvent(new ApiCreatedEvent(this, savedDoc.getId(), username));
                            log.debug("Published ApiCreatedEvent for API ID: {}, user: {}", savedDoc.getId(), username);
                        })
                        .thenReturn(savedDoc)
                )
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
    
    /**
     * Generates a comprehensive summary of an API's business capabilities using an AI model.
     *
     * This enhanced method analyzes all endpoints (not just GET) and extracts richer information
     * including paths, methods, descriptions, and parameters to provide a more complete picture
     * of what users can accomplish with the API.
     *
     * @param endpoints A list of UnifiedEndpoint objects representing the API's operations.
     * @param apiName   The name of the API, used to provide context to the AI in the prompt.
     * @return A GeneratedCapabilities object containing the AI-generated category and
     *         list of capabilities.
     */
    private GeneratedCapabilities generateCapabilities(List<UnifiedEndpoint> endpoints, String apiName) {
        // Group endpoints by business domain/resource
        Map<String, List<UnifiedEndpoint>> endpointsByResource = endpoints.stream()
            .collect(Collectors.groupingBy(this::extractResourceFromPath));
        
        StringBuilder endpointAnalysis = new StringBuilder();
        
        for (Map.Entry<String, List<UnifiedEndpoint>> entry : endpointsByResource.entrySet()) {
            String resource = entry.getKey();
            List<UnifiedEndpoint> resourceEndpoints = entry.getValue();
            
            endpointAnalysis.append("\n**").append(resource.toUpperCase()).append(" OPERATIONS:**\n");
            
            for (UnifiedEndpoint endpoint : resourceEndpoints) {
                String operation = analyzeEndpointCapability(endpoint);
                if (!operation.isEmpty()) {
                    endpointAnalysis.append("- ").append(operation).append("\n");
                }
            }
        }

        String systemPrompt = """
            You are an expert UX Writer and Product Designer for AI Assistants. 
            Your goal is to analyze API technical specifications and convert them into 
            short, punchy "Action Tags" that represent what a user can ask the AI to do.
            You avoid corporate jargon and focus on direct, imperative actions.
            """;

        String userPrompt = String.format(
            """
            Analyze the following API endpoints and generate a list of "Action Capabilities" 
            that act as concise prompts for the user.

            **--- STRICT FORMATTING RULES ---**
            1. **SHORT & PUNCHY:** Each capability must be **2 to 5 words maximum**.
            2. **VERB FIRST:** Start with strong, natural verbs (e.g., "Find", "Compare", "Calculate", "Show", "Check").
            3. **NO JARGON:** Do NOT use technical terms like "GET", "Endpoint", "Retrieve", "Return", "Object".
            4. **VARIETY:** Do not repeat the same verb for every item if possible.
            5. **QUANTITY:** Generate between 3 to 10 capabilities based on the API complexity.
                - If the API is small (1-2 endpoints), generate only 3-4 strong actions.
                - If the API is large, generate up to 10 distinct actions.
                - **CRITICAL:** Do NOT create redundant synonyms just to fill space. Quality > Quantity.

            **--- EXAMPLES OF TRANSFORMATION ---**
            
            *Input:* GET /countries/{name} (Returns population, area, capital)
            *Bad:* "Retrieve comprehensive country data for analysis."
            *Good:* "Check Country Facts" or "Compare Populations"

            *Input:* GET /artworks/search (Finds art by query)
            *Bad:* "Search the database for artistic content."
            *Good:* "Find Artworks" or "Discover Artists"

            *Input:* POST /payment/process
            *Bad:* "Process a financial transaction."
            *Good:* "Send Payment"

            **--- INPUT DATA ---**
            **API NAME:** %s
            **ENDPOINTS:** %s

            **--- OUTPUT FORMAT (JSON) ---**
            Return a JSON object with:
            - "category": A short, 2-3 word category name for this tool (e.g., "Global Data", "Art Finder").
            - "capabilities": A list of strings (the action tags).
            
            Generate the JSON response now:
            """, 
            apiName, 
            endpointAnalysis.toString()
        );

        return springAIService.extractStructuredData(systemPrompt, userPrompt, GeneratedCapabilities.class)
                .block(chatConfig.getUtilityAi().getTimeout());
    }

    /**
     * Extracts the main resource name from an endpoint path for grouping purposes.
     * Examples: "/users/{id}" -> "users", "/api/v1/orders" -> "orders"
     */
    private String extractResourceFromPath(UnifiedEndpoint endpoint) {
        String path = endpoint.getPath();
        if (path == null || path.isEmpty()) {
            return "general";
        }
        
        // Remove leading slash and split by slash
        String[] segments = path.replaceFirst("^/", "").split("/");
        
        // Skip common prefixes like "api", "v1", "v2", etc.
        for (String segment : segments) {
            if (!segment.matches("^(api|v\\d+|version)$") && !segment.contains("{")) {
                return segment.toLowerCase();
            }
        }
        
        return segments.length > 0 ? segments[0].toLowerCase() : "general";
    }

    /**
     * Analyzes a single endpoint to extract its business capability description.
     */
    private String analyzeEndpointCapability(UnifiedEndpoint endpoint) {
        String method = endpoint.getMethod();
        String path = endpoint.getPath();
        String summary = endpoint.getSummary();
        String description = endpoint.getDescription();
        
        // Use the best available description
        String mainDescription = "";
        if (summary != null && !summary.trim().isEmpty()) {
            mainDescription = summary.trim();
        } else if (description != null && !description.trim().isEmpty()) {
            mainDescription = description.trim().split("\\n")[0]; // Take first line
        } else if (endpoint.getOperationId() != null) {
            mainDescription = humanizeOperationId(endpoint.getOperationId());
        }
        
        // Add method context if description doesn't already include it
        String methodContext = getMethodContext(method);
        if (!mainDescription.toLowerCase().contains(methodContext.toLowerCase())) {
            mainDescription = methodContext + " " + mainDescription;
        }
        
        // Add parameter context for important parameters
        String paramContext = getParameterContext(endpoint.getParameters());
        if (!paramContext.isEmpty()) {
            mainDescription += " " + paramContext;
        }
        
        return mainDescription;
    }

    /**
     * Converts camelCase operationId to human-readable text.
     * Example: "getUserById" -> "Get User By Id"
     */
    private String humanizeOperationId(String operationId) {
        String result = operationId.replaceAll("([a-z])([A-Z])", "$1 $2");
        return result.substring(0, 1).toUpperCase() + result.substring(1).toLowerCase();
    }

    /**
     * Gets business context based on HTTP method.
     */
    private String getMethodContext(String method) {
        if (method == null) return "";
        
        return switch (method.toUpperCase()) {
            case "GET" -> "Retrieve";
            case "POST" -> "Create";
            case "PUT" -> "Update";
            case "PATCH" -> "Modify";
            case "DELETE" -> "Remove";
            default -> "";
        };
    }

    /**
     * Extracts important parameter information for context.
     */
    private String getParameterContext(List<UnifiedParameter> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return "";
        }
        
        // Look for important parameters that add business context
        List<String> importantParams = parameters.stream()
            .filter(p -> p.getDescription() != null && !p.getDescription().trim().isEmpty())
            .filter(p -> isImportantParameter(p.getName()))
            .map(p -> "by " + p.getName())
            .limit(2)
            .collect(Collectors.toList());
        
        return importantParams.isEmpty() ? "" : "(" + String.join(", ", importantParams) + ")";
    }

    /**
     * Determines if a parameter name indicates important business context.
     */
    private boolean isImportantParameter(String paramName) {
        if (paramName == null) return false;
        
        String lower = paramName.toLowerCase();
        return lower.contains("id") || 
               lower.contains("name") || 
               lower.contains("type") || 
               lower.contains("status") || 
               lower.contains("date") || 
               lower.contains("time") ||
               lower.contains("filter") ||
               lower.contains("search");
    }
    
}

