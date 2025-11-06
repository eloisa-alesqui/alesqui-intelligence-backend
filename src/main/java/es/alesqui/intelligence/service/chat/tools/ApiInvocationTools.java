package es.alesqui.intelligence.service.chat.tools;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import es.alesqui.intelligence.dto.chat.request.ApiCallRequest;
import es.alesqui.intelligence.dto.chat.response.ApiCallResponse;
import es.alesqui.intelligence.dto.chat.response.SseEvent;
import es.alesqui.intelligence.dto.chat.response.StructuredApiError;
import es.alesqui.intelligence.exception.ApiExecutionException;
import es.alesqui.intelligence.exception.ParameterValidationException;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedApiDocument;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedEndpoint;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedParameter;
import es.alesqui.intelligence.service.UnifiedApiService;
import es.alesqui.intelligence.service.api.ApiExecutionService;
import static es.alesqui.intelligence.service.chat.tools.support.EndpointSupport.getOperationIdOrDefault;
import static es.alesqui.intelligence.service.chat.tools.support.EndpointSupport.normalizePath;
import static es.alesqui.intelligence.service.chat.tools.support.EndpointSupport.parseMethodPath;
import static es.alesqui.intelligence.service.chat.tools.support.SseSupport.getSinkFromContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Sinks;

/**
 * Tools for invoking API endpoints.
 * 
 * This class contains the action-oriented tool that performs actual HTTP calls
 * against endpoints defined in a unified API catalog. It resolves endpoints by
 * explicit operationId or by METHOD:PATH syntax, validates parameters against
 * the specification (including enum values), expands path variables, and
 * delegates execution to the ApiExecutionService.
 * 
 * Conventions
 * - Optionally emits progress updates via an SSE sink when present in the ToolContext.
 * - Uses a synthetic METHOD:/normalized/path identifier when the endpoint has no explicit operationId.
 * - Produces structured error responses to help the LLM recover from failures.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiInvocationTools {

    private final UnifiedApiService unifiedApiService;
    private final ApiExecutionService apiExecutionService;
    private final ObjectMapper objectMapper;
    private final es.alesqui.intelligence.service.chat.tools.support.InspectionPolicyService inspectionPolicy;

    /**
     * Calls a specific API endpoint with the provided parameters.
     * 
     * Resolution rules
     * - First tries to locate the endpoint by explicit operationId.
     * - If not found, accepts METHOD:PATH (or METHOD PATH) identifiers and matches by method and normalized path.
     * - If still not found, attempts a unique match by path alone.
     * 
     * Behavior
     * - Validates provided parameters against the endpoint specification; enum mismatches raise a validation error.
     * - Replaces path variables in the URL and forwards remaining parameters as query/body as decided downstream.
     * - Emits progress updates to the SSE sink when available.
     * 
     * @param apiName exact name of the API that contains the endpoint
     * @param operationId operation identifier or METHOD:PATH alias to select the endpoint
     * @param parameters JSON string containing the call parameters
     * @param toolContext optional context that may contain an SSE sink under key "sseSink"
     * @return the ApiCallResponse produced by the execution service
     */
    @Tool(name = "call_api", description = "Calls a specific API endpoint with the provided parameters.")
    public ApiCallResponse callApi(
        @ToolParam(description = "The exact name of the API to call.") String apiName,
        @ToolParam(description = "The operation ID of the endpoint to call.") String operationId,
        @ToolParam(description = "JSON string with the parameters for the API call.") String parameters,
        ToolContext toolContext) {

        Sinks.Many<SseEvent> sink = getSinkFromContext(toolContext);
        if (sink != null) sink.tryEmitNext(SseEvent.status("Preparing API call to operation: " + operationId + "..."));

        log.info("Executing tool: callApi - API: '{}', Operation: '{}'", apiName, operationId);
        try {
            validateApiCallInputs(apiName, operationId);

            UnifiedApiDocument api = unifiedApiService.findByName(apiName.trim()).block(Duration.ofSeconds(10));
            if (api == null) throw new ApiExecutionException("No API found with name: " + apiName);

            UnifiedEndpoint endpoint = api.getEndpoints().stream()
                .filter(e -> operationId.trim().equals(e.getOperationId())).findFirst()
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
                        List<UnifiedEndpoint> byPath = api.getEndpoints().stream()
                                .filter(e -> normalizePath(e.getPath()).equalsIgnoreCase(normPath))
                                .toList();
                        if (byPath.size() == 1) {
                            endpoint = byPath.get(0);
                        }
                    }
                }
            }

            if (endpoint == null) {
                throw new ApiExecutionException("No endpoint found with operation ID '" + operationId + "' in API '" + apiName + "'");
            }

            // Enforce inspect-before-execute policy
            String conversationId = toolContext != null && toolContext.getContext() != null
                    ? (String) toolContext.getContext().get("conversationId")
                    : null;
            String opKey = getOperationIdOrDefault(endpoint);
            if (!inspectionPolicy.wasInspectedRecently(conversationId, api.getName(), opKey, Duration.ofMinutes(10))) {
                if (sink != null) sink.tryEmitNext(SseEvent.status("Inspection required before calling this endpoint."));
                return createInspectionRequiredResponse(api.getName(), opKey);
            }

            Map<String, Object> paramMap = parseParameters(parameters);
            validateParametersAgainstSpec(endpoint, paramMap);

            ApiCallRequest apiCallRequest = buildApiCallRequest(api, endpoint, paramMap);

            if (sink != null) sink.tryEmitNext(SseEvent.status("Executing API call..."));
            ApiCallResponse response = apiExecutionService.executeApiCall(apiCallRequest).block(Duration.ofSeconds(30));
            if (response == null) throw new ApiExecutionException("No response received from API call");

            if (sink != null) sink.tryEmitNext(SseEvent.status("API call to '" + operationId + "' was successful."));
            return response;

        } catch (Exception e) {
            log.error("Error in callApi tool - API: '{}', Operation: '{}'", apiName, operationId, e);
            if (sink != null) sink.tryEmitNext(SseEvent.status("API call to '" + operationId + "' failed."));
            return createStructuredErrorResponse(apiName, operationId, e);
        }
    }

    /**
     * Ensures required inputs are provided for an API call.
     *
     * @param apiName target API name
     * @param operationId endpoint identifier
     * @throws IllegalArgumentException when any required input is blank
     */
    private void validateApiCallInputs(String apiName, String operationId) {
        if (StringUtils.isBlank(apiName)) throw new IllegalArgumentException("API name is required.");
        if (StringUtils.isBlank(operationId)) throw new IllegalArgumentException("Operation ID is required.");
    }

    /**
     * Validates parameters against the endpoint spec, focusing on enum values.
     * If a provided value does not match the allowed set, an exception is thrown
     * so the caller can surface a structured error to the user/LLM.
     *
     * @param endpoint endpoint definition from the catalog
     * @param llmParameters parameters provided by the LLM
     */
    private void validateParametersAgainstSpec(UnifiedEndpoint endpoint, Map<String, Object> llmParameters) {
        if (endpoint.getParameters() == null || llmParameters == null || llmParameters.isEmpty()) {
            return;
        }
        for (UnifiedParameter paramSpec : endpoint.getParameters()) {
            if (paramSpec.getSchema() != null) {
                List<Object> validEnumValues = paramSpec.getSchema().getEnumValues();
                if (validEnumValues != null && !validEnumValues.isEmpty() && llmParameters.containsKey(paramSpec.getName())) {
                    Object providedValueObj = llmParameters.get(paramSpec.getName());
                    if (providedValueObj == null) continue;
                    String providedValue = String.valueOf(providedValueObj);
                    boolean isValid = validEnumValues.stream().anyMatch(enumVal -> String.valueOf(enumVal).equalsIgnoreCase(providedValue));
                    if (!isValid) {
                        throw new ParameterValidationException(paramSpec.getName(), providedValue, validEnumValues);
                    }
                }
            }
        }
    }

    /**
     * Parses the parameters JSON string into a map. Returns an empty map for
     * blank or "{}" input.
     *
     * @param parameters JSON text provided by the LLM
     * @return a Map representation of the parameters
     */
    private Map<String, Object> parseParameters(String parameters) {
        if (StringUtils.isBlank(parameters) || "{}".equals(parameters.trim())) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(parameters, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid parameters JSON: " + e.getMessage());
        }
    }

    /**
     * Substitutes path parameters in the endpoint path using provided values.
     * Parameters marked with in="path" are replaced, and the consumed entries
     * remain available to callers for removal from the query/body map.
     *
     * @param endpoint endpoint definition
     * @param parameters provided values
     * @return the resolved path string
     */
    private String resolvePath(UnifiedEndpoint endpoint, Map<String, Object> parameters) {
        String resolvedPath = endpoint.getPath();
        List<UnifiedParameter> endpointParams = endpoint.getParameters();
        if (endpointParams == null) return resolvedPath;
        for (UnifiedParameter paramDef : endpointParams) {
            if ("path".equalsIgnoreCase(paramDef.getIn())) {
                String paramName = paramDef.getName();
                if (parameters.containsKey(paramName)) {
                    resolvedPath = resolvedPath.replace("{" + paramName + "}", String.valueOf(parameters.get(paramName)));
                }
            }
        }
        return resolvedPath;
    }

    /**
     * Builds the request object to hand off to the execution layer. It resolves
     * the path, removes consumed path parameters from the parameter map, and
     * uses the endpoint's operationId or a synthetic fallback as identifier.
     *
     * @param api the API document that owns the endpoint
     * @param endpoint endpoint to invoke
     * @param parameters full parameter map provided by the LLM
     * @return a fully populated ApiCallRequest
     */
    private ApiCallRequest buildApiCallRequest(UnifiedApiDocument api, UnifiedEndpoint endpoint, Map<String, Object> parameters) {
        String resolvedPath = resolvePath(endpoint, parameters);
        Map<String, Object> remainingParameters = new HashMap<>(parameters);
        if (endpoint.getParameters() != null) {
            endpoint.getParameters().stream()
                .filter(p -> "path".equalsIgnoreCase(p.getIn()))
                .forEach(p -> remainingParameters.remove(p.getName()));
        }
        return ApiCallRequest.builder()
                .apiName(api.getName())
                .endpoint(getOperationIdOrDefault(endpoint))
                .path(resolvedPath)
                .headers(new HashMap<>())
                .httpMethod(endpoint.getMethod())
                .parameters(remainingParameters)
                .build();
    }

    /**
     * Creates a structured error response suitable for LLM consumption. It
     * includes a machine-readable error type, a human-readable message, and
     * context details such as attempted API and operation identifiers.
     *
     * @param apiName attempted API name
     * @param operationId attempted operation identifier
     * @param e the underlying exception
     * @return an ApiCallResponse encoding the error and an HTTP-like status code
     */
    private ApiCallResponse createStructuredErrorResponse(String apiName, String operationId, Exception e) {
        StructuredApiError.StructuredApiErrorBuilder errorBuilder = StructuredApiError.builder().message(e.getMessage());
        Map<String, Object> details = new HashMap<>();
        details.put("apiNameAttempted", apiName);
        details.put("operationIdAttempted", operationId);
        int statusCode = 500;
        if (e instanceof ParameterValidationException pve) {
            errorBuilder.errorType("INVALID_PARAMETERS");
            errorBuilder.message("Invalid value '" + pve.getInvalidValue() + "' for parameter '" + pve.getParameterName() + "'.");
            details.put("failingParameter", pve.getParameterName());
            details.put("providedValue", pve.getInvalidValue());
            details.put("allowedValues", pve.getValidValues());
            statusCode = 400;
        } else if (e instanceof IllegalArgumentException) {
            errorBuilder.errorType("INVALID_PARAMETERS");
            statusCode = 400;
        } else if (e instanceof ApiExecutionException) {
            errorBuilder.errorType("API_EXECUTION_FAILED");
            statusCode = 502;
        } else {
            errorBuilder.errorType("INTERNAL_TOOL_ERROR");
        }
        errorBuilder.details(details);
        return ApiCallResponse.failure(errorBuilder.build(), statusCode)
                .withApiDetails(apiName, operationId)
                .withExecutionTime(0L);
    }

    /**
     * Creates a structured response indicating that the model must call
     * inspect_endpoint before executing this operation.
     */
    private ApiCallResponse createInspectionRequiredResponse(String apiName, String operationId) {
    StructuredApiError error = StructuredApiError.builder()
        .errorType("INSPECTION_REQUIRED")
        .message("You must call inspect_endpoint for this operation before calling call_api.")
        .details(Map.of(
            "nextTool", "inspect_endpoint",
            "apiName", apiName,
            "operationId", operationId,
            "reason", "Policy requires inspecting parameters and requestBody just before execution."
        ))
        .build();
    return ApiCallResponse.failure(error, 428)
        .withApiDetails(apiName, operationId)
        .withExecutionTime(0L);
    }
}
