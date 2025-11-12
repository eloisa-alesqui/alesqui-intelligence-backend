package es.alesqui.intelligence.service.chat.tools;

import java.time.Duration;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import es.alesqui.intelligence.dto.chat.response.SseEvent;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedApiDocument;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedEndpoint;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedParameter;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedTag;
import es.alesqui.intelligence.service.UnifiedApiService;
import es.alesqui.intelligence.service.access.ApiVisibilityService;
import static es.alesqui.intelligence.service.chat.tools.support.EndpointSupport.formatParametersDetailedForLLM;
import static es.alesqui.intelligence.service.chat.tools.support.EndpointSupport.formatResponsesForLLM;
import static es.alesqui.intelligence.service.chat.tools.support.EndpointSupport.getOperationIdOrDefault;
import static es.alesqui.intelligence.service.chat.tools.support.EndpointSupport.normalizePath;
import static es.alesqui.intelligence.service.chat.tools.support.EndpointSupport.parseMethodPath;
import static es.alesqui.intelligence.service.chat.tools.support.EndpointSupport.renderRequestBodySchemaMarkdown;
import static es.alesqui.intelligence.service.chat.tools.support.SseSupport.getSinkFromContext;
import es.alesqui.intelligence.service.identity.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Sinks;

/**
 * Tools for discovering API catalogs and inspecting endpoint specifications.
 * 
 * This class exposes read-only operations that help the LLM understand what APIs
 * are available, which endpoints each API provides, and the detailed contract of
 * a specific endpoint. It does not execute requests; invocation is handled by a
 * separate tool class.
 * 
 * Conventions
 * - All methods optionally emit progress updates via an SSE sink when present in the ToolContext.
 * - Endpoints missing an explicit operationId are presented using a synthetic identifier METHOD:/normalized/path.
 * - Operation identifiers provided in METHOD:PATH form are accepted by inspectors as a fallback.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiDiscoveryTools {

    private final UnifiedApiService unifiedApiService;
    private final ObjectMapper objectMapper;
    private final es.alesqui.intelligence.service.chat.tools.support.InspectionPolicyService inspectionPolicy;
    private final ApiVisibilityService apiVisibilityService;
    private final UserService userService;

    /**
     * Lists all active APIs with basic metadata to help choose a target system.
     * Emits status to the SSE sink when available.
     *
     * @param toolContext optional context that may contain an SSE sink under key "sseSink"
     * @return a multi-line string with API name, description, tags, and capability summary,
     *         or a short message when none are available
     */
    @Tool(name = "list_apis", description = "Lists all available APIs with their descriptions, tags, and a summary of their capabilities.")
    public String listApis(ToolContext toolContext) {
        Sinks.Many<SseEvent> sink = getSinkFromContext(toolContext);
        if (sink != null) sink.tryEmitNext(SseEvent.status("Listing available APIs..."));

        log.info("Executing tool: listApis");
        try {
            // Resolve current user id (if available)
            String userId = userService.getCurrentUserIdBlocking(Duration.ofSeconds(5));

            var apis = apiVisibilityService.listVisibleApis(userId).collectList().block(Duration.ofSeconds(10));
            if (apis == null || apis.isEmpty()) {
                if (sink != null) sink.tryEmitNext(SseEvent.status("No APIs found."));
                return "No available APIs were found.";
            }
    
            if (sink != null) sink.tryEmitNext(SseEvent.status("Found " + apis.size() + " APIs."));
            return "Available APIs:\n" + apis.stream()
                .map(api -> {
                    String tagsString = api.getTags() != null ? api.getTags().stream().map(UnifiedTag::getName).collect(Collectors.joining(", ")) : "none";
                    String capabilities = StringUtils.isNotBlank(api.getCapabilitiesSummary()) ? api.getCapabilitiesSummary() : "Not available";
                    return String.format("• Name: %s\n  - Description: %s\n  - Tags: [%s]\n  - Capabilities: %s", api.getName(), api.getDescription(), tagsString, capabilities);
                })
                .collect(Collectors.joining("\n\n"));
        } catch (Exception e) {
            log.error("Error in listApis tool", e);
            if (sink != null) sink.tryEmitNext(SseEvent.status("Failed to list APIs."));
            String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return "Error while trying to list APIs: " + message;
        }
    }

    /**
     * Lists endpoints for a specific API including operationId (or synthetic fallback),
     * HTTP method, path, summary, and parameter names. Use "inspect_endpoint" for full details.
     * Emits status to the SSE sink when available.
     *
     * @param apiName exact API name to inspect
     * @param toolContext optional context that may contain an SSE sink under key "sseSink"
     * @return a multi-line string describing available endpoints or an error message if the API is not found
     */
    @Tool(name = "list_endpoints", description = "Lists endpoints for a specific API (operationId, method, path, summary). Use 'inspect_endpoint' to get full details.")
    public String listEndpoints(@ToolParam(description = "The exact name of the API to inspect.") String apiName, ToolContext toolContext) {
        Sinks.Many<SseEvent> sink = getSinkFromContext(toolContext);
        if (sink != null) sink.tryEmitNext(SseEvent.status("Listing endpoints for API: " + apiName + "..."));

        log.info("Executing tool: listEndpoints for API '{}'", apiName);
        try {
            UnifiedApiDocument api = unifiedApiService.findByName(apiName.trim()).block(Duration.ofSeconds(10));
            if (api == null) {
                return "Error: No API was found with the name: " + apiName;
            }
            // Access control: verify visibility for current user
            String userId = userService.getCurrentUserIdBlocking(Duration.ofSeconds(5));
            Boolean allowed = apiVisibilityService.canAccess(userId, api.getId()).block(Duration.ofSeconds(5));
            if (allowed == null || !allowed) {
                return "Error: You don't have permission to view endpoints for API: " + apiName;
            }
            if (api.getEndpoints() == null || api.getEndpoints().isEmpty()) {
                return "The API '" + apiName + "' has no available endpoints.";
            }

            if (sink != null) sink.tryEmitNext(SseEvent.status("Found " + api.getEndpoints().size() + " endpoints for " + apiName + "."));
            return "Available endpoints for API '" + apiName + "':\n" + api.getEndpoints().stream()
                .map(e -> {
                    String displayOp = getOperationIdOrDefault(e);
                    String summary = StringUtils.isNotBlank(e.getSummary()) ? e.getSummary() : (e.getDescription() != null ? e.getDescription() : "");
                    String base = String.format("• OperationId: %s, Method: %s, Path: %s, Summary: %s", displayOp, e.getMethod(), e.getPath(), summary);
                    if (e.getParameters() != null && !e.getParameters().isEmpty()) {
                        String names = e.getParameters().stream().map(UnifiedParameter::getName).collect(Collectors.joining(", "));
                        base += String.format("\n  - Parameter names: [%s]", names);
                    }
                    return base;
                })
                .collect(Collectors.joining("\n\n"));
        } catch (Exception e) {
            log.error("Error in listEndpoints tool for API '{}'", apiName, e);
            if (sink != null) sink.tryEmitNext(SseEvent.status("Failed to list endpoints."));
            return "Error listing endpoints for '" + apiName + "': " + e.getMessage();
        }
    }

    /**
     * Shows full details for a specific endpoint including description, detailed
     * parameters with resolved schemas, request body schema, and a responses summary.
     * Supports operation lookup by explicit operationId or by METHOD:PATH syntax.
     * Emits status to the SSE sink when available.
     *
     * @param apiName exact name of the API that contains the endpoint
     * @param operationId either a concrete operationId or a METHOD:PATH identifier
     * @param toolContext optional context that may contain an SSE sink under key "sseSink"
     * @return a formatted markdown-like block with endpoint details, or an error message
     */
    @Tool(name = "inspect_endpoint", description = "Shows full details for an API endpoint (description, parameters with schema, requestBody schema if present, and responses). Use after list_endpoints.")
    public String inspectEndpoint(
        @ToolParam(description = "The exact name of the API.") String apiName,
        @ToolParam(description = "The operation ID of the endpoint.") String operationId,
        ToolContext toolContext
    ) {
        Sinks.Many<SseEvent> sink = getSinkFromContext(toolContext);
        if (sink != null) sink.tryEmitNext(SseEvent.status("Inspecting endpoint details for API: " + apiName + ", Operation: " + operationId + "..."));

        try {
            UnifiedApiDocument api = unifiedApiService.findByName(apiName.trim()).block(Duration.ofSeconds(10));
            if (api == null) {
                return "Error: No API found with name: " + apiName;
            }

            String userId = userService.getCurrentUserIdBlocking(Duration.ofSeconds(5));
            Boolean allowed = apiVisibilityService.canAccess(userId, api.getId()).block(Duration.ofSeconds(5));
            if (allowed == null || !allowed) {
                return "Error: You don't have permission to inspect endpoints for API: " + apiName;
            }

            UnifiedEndpoint endpoint = api.getEndpoints().stream()
                    .filter(e -> operationId.trim().equals(e.getOperationId()))
                    .findFirst()
                    .orElse(null);

            if (endpoint == null) {
                var mp = parseMethodPath(operationId);
                if (mp != null) {
                    String normPath = normalizePath(mp.path());
                    endpoint = api.getEndpoints().stream()
                            .filter(e -> mp.method().equalsIgnoreCase(e.getMethod())
                                    && normalizePath(e.getPath()).equalsIgnoreCase(normPath))
                            .findFirst()
                            .orElse(null);
                    if (endpoint == null) {
                        var byPath = api.getEndpoints().stream()
                                .filter(e -> normalizePath(e.getPath()).equalsIgnoreCase(normPath))
                                .toList();
                        if (byPath.size() == 1) {
                            endpoint = byPath.get(0);
                        }
                    }
                }
            }

            if (endpoint == null) {
                return "Error: No endpoint found with operation ID '" + operationId + "' in API '" + apiName + "'";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("## Endpoint: ").append(endpoint.getOperationId())
                    .append(" (").append(endpoint.getMethod()).append(") ")
                    .append(endpoint.getPath() != null ? endpoint.getPath() : "").append("\n\n");

            if (StringUtils.isNotBlank(endpoint.getSummary())) {
                sb.append("**Summary:** ").append(endpoint.getSummary()).append("\n\n");
            }
            if (StringUtils.isNotBlank(endpoint.getDescription())) {
                sb.append("**Description:** ").append(endpoint.getDescription()).append("\n\n");
            }

            sb.append("### Parameters\n");
            sb.append(formatParametersDetailedForLLM(api, endpoint.getParameters())).append("\n\n");

            sb.append("### Request Body\n");
            sb.append(renderRequestBodySchemaMarkdown(api, endpoint.getRequestBody(), objectMapper)).append("\n\n");

            sb.append("### Responses\n");
            sb.append(formatResponsesForLLM(api, endpoint.getResponses(), objectMapper)).append("\n");

            try {
                String conversationId = toolContext != null && toolContext.getContext() != null
                        ? (String) toolContext.getContext().get("conversationId")
                        : null;
                String opKey = getOperationIdOrDefault(endpoint);
                inspectionPolicy.recordInspection(conversationId, api.getName(), opKey);
            } catch (Exception ignore) {
                // best-effort only
            }

            if (sink != null) sink.tryEmitNext(SseEvent.status("Endpoint details assembled."));
            return sb.toString();

        } catch (Exception e) {
            log.error("Error inspecting endpoint for API: '{}', Operation: '{}'", apiName, operationId, e);
            return "Error inspecting endpoint: " + e.getMessage();
        }
    }
}
