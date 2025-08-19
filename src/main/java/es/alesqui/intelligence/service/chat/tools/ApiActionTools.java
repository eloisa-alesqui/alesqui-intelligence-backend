package es.alesqui.intelligence.service.chat.tools;

import com.fasterxml.jackson.databind.ObjectMapper;

import es.alesqui.intelligence.dto.chat.request.ApiCallRequest;
import es.alesqui.intelligence.dto.chat.response.ApiCallResponse;
import es.alesqui.intelligence.exception.ApiExecutionException;
import es.alesqui.intelligence.model.unified.UnifiedApiDocument;
import es.alesqui.intelligence.model.unified.UnifiedEndpoint;
import es.alesqui.intelligence.service.UnifiedApiService;
import es.alesqui.intelligence.service.api.ApiExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiActionTools {

    private final UnifiedApiService unifiedApiService;
    private final ApiExecutionService apiExecutionService;
    private final ObjectMapper objectMapper;

    @Tool(description = "Lists all available APIs and their descriptions. This is the first step to take if you don't know which APIs are available to interact with.")
    public String listApis() {
        log.info("Executing tool: listApis");
        try {
            var apis = unifiedApiService.findActiveApis().collectList().block(Duration.ofSeconds(5));
            if (apis == null || apis.isEmpty()) {
                return "No available APIs were found.";
            }
            // We format the output to be readable for the LLM and the user
            return "Available APIs:\n" + apis.stream()
                .map(api -> String.format("• Name: %s, Description: %s", api.getName(), api.getDescription()))
                .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            log.error("Error in listApis tool", e);
            return "Error while trying to list APIs: " + e.getMessage();
        }
    }

    @Tool(description = "Gets the endpoints (operations) for a specific API. You need this to know which operations can be performed before calling 'callApi'.")
    public String listEndpoints(
        @ToolParam(description = "The exact name of the API to inspect. Must be one of the names returned by the 'listApis' tool.") String apiName) {
        log.info("Executing tool: listEndpoints for API '{}'", apiName);
        try {
            UnifiedApiDocument api = unifiedApiService.findByName(apiName.trim()).block(Duration.ofSeconds(5));
            if (api == null) {
                return "Error: No API was found with the name: " + apiName;
            }
            
            if (api.getEndpoints() == null || api.getEndpoints().isEmpty()) {
                return "The API '" + apiName + "' has no available endpoints (operations).";
            }

            // We return a clear list with the operation ID (to be used in callApi) and its description
            return "Available endpoints for the API '" + apiName + "':\n" + api.getEndpoints().stream()
                .map(e -> String.format("• OperationId: %s, Description: %s, Method: %s", e.getOperationId(), e.getSummary(), e.getMethod()))
                .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            log.error("Error in listEndpoints tool for API '{}'", apiName, e);
            return "Error listing endpoints for '" + apiName + "': " + e.getMessage();
        }
    }
    
    @Tool(description = "Calls a specific API endpoint with the provided parameters. Use this after getting the available endpoints with 'listEndpoints'.")
    public ApiCallResponse callApi(
        @ToolParam(description = "The exact name of the API to call. Must be one of the names returned by 'listApis'.") String apiName,
        @ToolParam(description = "The operation ID of the endpoint to call. Must be one of the operation IDs returned by 'listEndpoints'.") String operationId,
        @ToolParam(description = "JSON string with the parameters for the API call. Use {} for no parameters.") String parameters,
        ToolContext toolContext) {
        
        log.info("Executing tool: callApi - API: '{}', Operation: '{}', Parameters: '{}'", apiName, operationId, parameters);
        
        try {
            // 1. Validate inputs
            if (StringUtils.isBlank(apiName)) {
                throw new IllegalArgumentException("❌ Error: API name is required");
            }
            if (StringUtils.isBlank(operationId)) {
            	throw new IllegalArgumentException("❌ Error: Operation ID is required");
            }
            
            // 2. Find the API and endpoint
            UnifiedApiDocument api = unifiedApiService.findByName(apiName.trim())
                .block(Duration.ofSeconds(5));
            if (api == null) {
            	throw new ApiExecutionException("❌ Error: No API found with name: " + apiName);
            }
            
            // 3. Find the specific endpoint
            var endpoint = api.getEndpoints().stream()
                .filter(e -> operationId.trim().equals(e.getOperationId()))
                .findFirst()
                .orElse(null);
                
            if (endpoint == null) {
            	throw new ApiExecutionException("❌ Error: No endpoint found with operation ID '" + operationId + "' in API '" + apiName + "'");
            }
            
            // 4. Parse parameters
            Map<String, Object> paramMap = parseParameters(parameters);
            
            // 5. Extract conversationId
            String conversationId = (String) toolContext.getContext().get("conversationId");
            if (conversationId == null) {
                log.warn("conversationId was not found in ToolContext.");
            }
            
            // 6. Build API call request
            ApiCallRequest apiCallRequest = buildApiCallRequest(api, endpoint, paramMap, conversationId);
            
            // 7. Execute the API call
            ApiCallResponse response = apiExecutionService.executeApiCall(apiCallRequest)
                .block(Duration.ofSeconds(30));
                
            if (response == null) {
            	throw new ApiExecutionException("❌ Error: No response received from API call");
            }
            
            // 8. Format successful response
            return response;
            
        } catch (Exception e) {
            log.error("Error in callApi tool - API: '{}', Operation: '{}'", apiName, operationId, e);
            return createFallbackResponse(apiName, operationId, e);
        }
    }

	private ApiCallResponse createFallbackResponse(String apiName, String operationId, Exception e) {
		return ApiCallResponse.failure("API execution failed: " + e.getMessage(), 503)
				.withApiDetails(apiName, operationId).withExecutionTime(0L);
	}

	/**
     * Parses the parameters string into a Map
     */
    private Map<String, Object> parseParameters(String parameters) {
        if (StringUtils.isBlank(parameters) || "{}".equals(parameters.trim())) {
            return new HashMap<>();
        }
        
        try {
            return objectMapper.readValue(parameters, Map.class);
        } catch (Exception e) {
            log.warn("Failed to parse parameters '{}', using empty map", parameters);
            return new HashMap<>();
        }
    }

    /**
     * Builds the ApiCallRequest from the unified API document and endpoint
     */
    private ApiCallRequest buildApiCallRequest(UnifiedApiDocument api, UnifiedEndpoint endpoint, Map<String, Object> parameters, String conversationId) {
        ApiCallRequest request = ApiCallRequest.builder()
                .apiName(api.getName())
                .endpoint(endpoint.getOperationId())
                .path(endpoint.getPath())
                .headers(new HashMap<>())
                .httpMethod(endpoint.getMethod())
                .parameters(parameters)
                .conversationId(conversationId)
                .build();
        
        return request;
    }

 
}
