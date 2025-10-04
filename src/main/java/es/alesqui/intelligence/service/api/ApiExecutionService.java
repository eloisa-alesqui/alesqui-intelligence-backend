package es.alesqui.intelligence.service.api;

import es.alesqui.intelligence.dto.chat.request.ApiCallRequest;
import es.alesqui.intelligence.dto.chat.response.ApiCallResponse;
import es.alesqui.intelligence.model.api_spec.unified.ApiConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Map;

/**
 * Service responsible for executing dynamic API calls.
 * It orchestrates request validation, dynamic client configuration (including OAuth 2.0 token fetching),
 * execution with retry logic, and comprehensive error handling.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ApiExecutionService {

    private final WebClient.Builder webClientBuilder;
    private final ApiConfigurationService apiConfigurationService;
    private final OAuth2TokenService oauth2TokenService;

    /**
     * Executes an API call with full error handling and retry logic.
     * This is the main entry point for the service.
     *
     * @param request The request DTO containing all necessary call details.
     * @return A Mono emitting an ApiCallResponse.
     */
    public Mono<ApiCallResponse> executeApiCall(ApiCallRequest request) {
        long startTime = System.currentTimeMillis();

        return validateRequest(request)
                .flatMap(validatedRequest -> performApiCall(validatedRequest, startTime))
                .onErrorResume(throwable -> handleError(throwable, request, startTime));
    }

    /**
     * Validates the incoming API call request for required fields.
     */
    private Mono<ApiCallRequest> validateRequest(ApiCallRequest request) {
        if (request.getApiName() == null || request.getApiName().trim().isEmpty()) {
            return Mono.error(new IllegalArgumentException("API name is required"));
        }
        if (request.getEndpoint() == null || request.getEndpoint().trim().isEmpty()) {
            return Mono.error(new IllegalArgumentException("Endpoint is required"));
        }
        if (request.getHttpMethod() == null) {
            return Mono.error(new IllegalArgumentException("HTTP method is required"));
        }
        return Mono.just(request);
    }

    /**
     * Performs the actual API call using a dynamically configured WebClient.
     * This method is responsible for assembling all parts of the request, including the final auth headers.
     */
    private Mono<ApiCallResponse> performApiCall(ApiCallRequest request, long startTime) {
        // 1. Fetch the entire configuration object once.
        ApiConfiguration config = apiConfigurationService.getConfiguration(request.getApiName());
        boolean loggingEnabled = config.isEnableLogging();

        if (loggingEnabled) {
            log.info("Executing {} {} for API: {} (timeout: {}s, retries: {})",
                    request.getHttpMethod(), request.getEndpoint(),
                    request.getApiName(), config.getTimeoutSeconds(), config.getMaxRetries());
        }

        // 2. Build the WebClient for this specific request.
        String baseUrl = apiConfigurationService.getBaseUrl(request.getApiName());
        WebClient client = webClientBuilder.baseUrl(baseUrl).build();

        WebClient.RequestBodySpec requestSpec = client
                .method(HttpMethod.valueOf(request.getHttpMethod().toUpperCase()))
                .uri(uriBuilder -> {
                	uriBuilder.path(request.getPath());
                    // Add query parameters from the request itself
                    if (request.getParameters() != null && isQueryParamMethod(request.getHttpMethod())) {
                        request.getParameters().forEach((key, value) -> {
                            if (value != null) uriBuilder.queryParam(key, value.toString());
                        });
                    }
                    // Add API Key to query if configured to do so
                    if ("api_key".equals(config.getAuth().getAuthType()) && "query".equalsIgnoreCase(config.getAuth().getAddApiKeyTo())) {
                       uriBuilder.queryParam(config.getAuth().getApiKeyName(), config.getAuth().getApiKey());
                    }
                    return uriBuilder.build();
                });

        // 3. Add all headers, orchestrating static and dynamic ones.
        // Get static headers (Basic, static Bearer, API Key in header)
        Map<String, String> headers = apiConfigurationService.getHeaders(request.getApiName());

        // If auth type is OAuth2, fetch the dynamic token and add the final header
        if ("oauth2".equals(config.getAuth().getAuthType())) {
            String token = oauth2TokenService.getAccessToken(request.getApiName());
            if (token != null) {
                headers.put("Authorization", "Bearer " + token);
            } else {
                log.warn("Could not retrieve OAuth 2.0 token for API '{}'. The call may fail.", request.getApiName());
            }
        }
        
        // Apply the final map of headers to the request
        headers.forEach(requestSpec::header);
        
        // Apply any headers passed in the original request
        if (request.getHeaders() != null) {
            request.getHeaders().forEach(requestSpec::header);
        }

        // 4. Set the body if applicable
        Mono<String> responseMono;
        if (request.getParameters() != null && !isQueryParamMethod(request.getHttpMethod())) {
            responseMono = requestSpec.bodyValue(request.getParameters()).retrieve().bodyToMono(String.class);
        } else {
            responseMono = requestSpec.retrieve().bodyToMono(String.class);
        }

        // 5. Execute with timeout and retry logic from the configuration.
        return responseMono
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .retryWhen(Retry.backoff(config.getMaxRetries(), Duration.ofMillis(500))
                        .filter(this::isRetryableException))
                .map(responseBody -> buildSuccessResponse(responseBody, request, startTime))
                .doOnError(error -> {
                    if (loggingEnabled) {
                        log.error("API call to '{}' failed: {}", request.getApiName(), error.getMessage());
                    }
                });
    }

    /**
     * Handles errors that occur during the API execution pipeline.
     */
    private Mono<ApiCallResponse> handleError(Throwable throwable, ApiCallRequest request, long startTime) {
        long executionTime = System.currentTimeMillis() - startTime;
        log.error("API execution error for '{}': {}", request.getApiName(), throwable.getMessage(), throwable);

        ApiCallResponse failureResponse;
        if (throwable instanceof WebClientResponseException) {
            WebClientResponseException webEx = (WebClientResponseException) throwable;
            failureResponse = ApiCallResponse.failure(
                    "API call failed: " + webEx.getMessage(),
                    webEx.getStatusCode().value(),
                    request.getConversationId()
            ).withRawResponse(webEx.getResponseBodyAsString());
        } else if (throwable instanceof java.util.concurrent.TimeoutException) {
            failureResponse = ApiCallResponse.failure("API call timeout", 408, request.getConversationId());
        } else {
            failureResponse = ApiCallResponse.failure("Unexpected error: " + throwable.getMessage(), 500, request.getConversationId());
        }

        return Mono.just(failureResponse
                .withApiDetails(request.getApiName(), request.getEndpoint(), request.getHttpMethod())
                .withExecutionTime(executionTime));
    }
    
    /**
     * Builds a standardized success response object.
     */
    private ApiCallResponse buildSuccessResponse(String responseBody, ApiCallRequest request, long startTime) {
        long executionTime = System.currentTimeMillis() - startTime;
        if (apiConfigurationService.isLoggingEnabled(request.getApiName())) {
            log.info("API call for '{}' completed in {}ms", request.getApiName(), executionTime);
        }
        return ApiCallResponse.success(Map.of("response", responseBody), 200, request.getConversationId())
                .withApiDetails(request.getApiName(), request.getEndpoint(), request.getHttpMethod())
                .withExecutionTime(executionTime)
                .withRawResponse(responseBody);
    }
    
    /**
     * Determines if an exception is suitable for a retry attempt.
     */
    private boolean isRetryableException(Throwable throwable) {
        if (throwable instanceof WebClientResponseException) {
            int statusCode = ((WebClientResponseException) throwable).getStatusCode().value();
            // Retry on 5xx server errors, 408 Request Timeout, or 429 Too Many Requests.
            return statusCode >= 500 || statusCode == 408 || statusCode == 429;
        }
        // Retry on network-level issues.
        return throwable instanceof java.io.IOException;
    }

    /**
     * Checks if the HTTP method typically uses query parameters instead of a body.
     */
    private boolean isQueryParamMethod(String httpMethod) {
        return "GET".equalsIgnoreCase(httpMethod) || "DELETE".equalsIgnoreCase(httpMethod);
    }
}