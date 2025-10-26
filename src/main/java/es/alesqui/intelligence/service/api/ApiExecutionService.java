package es.alesqui.intelligence.service.api;

import es.alesqui.intelligence.dto.chat.request.ApiCallRequest;
import es.alesqui.intelligence.dto.chat.response.ApiCallResponse;
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
        // 1. Start the reactive chain by getting the configuration.
        return apiConfigurationService.getConfiguration(request.getApiName())
                .flatMap(config -> {
                    boolean loggingEnabled = config.isEnableLogging();
                    if (loggingEnabled) {
                        log.info("Executing {} {} for API: {} (timeout: {}s, retries: {})",
                                request.getHttpMethod(), request.getEndpoint(),
                                request.getApiName(), config.getTimeoutSeconds(), config.getMaxRetries());
                    }

                    // 2. Get the base URL and static headers in parallel.
                    Mono<String> baseUrlMono = apiConfigurationService.getBaseUrl(request.getApiName());
                    Mono<Map<String, String>> staticHeadersMono = apiConfigurationService.getHeaders(request.getApiName());

                    // 3. Combine the results when both are ready.
                    return Mono.zip(baseUrlMono, staticHeadersMono)
                            .flatMap(tuple -> {
                                String baseUrl = tuple.getT1();
                                Map<String, String> headers = tuple.getT2();

                                // 4. Determine the final headers reactively.
                                // This Mono will contain the headers, including the OAuth2 token if needed.
                                Mono<Map<String, String>> finalHeadersMono;

                                if ("oauth2".equals(config.getAuth().getAuthType())) {
                                    // If it's OAuth2, get the token and add it to the headers.
                                    log.debug("Auth type is OAuth2, attempting to get access token for API '{}'", request.getApiName());
                                    finalHeadersMono = oauth2TokenService.getAccessToken(request.getApiName())
                                            .map(token -> {
                                                headers.put("Authorization", "Bearer " + token);
                                                return headers;
                                            })
                                            // If getAccessToken returns an empty Mono (token could not be obtained),
                                            // proceed with the original headers.
                                            .defaultIfEmpty(headers);
                                } else {
                                    // If not OAuth2, use the static headers directly.
                                    finalHeadersMono = Mono.just(headers);
                                }

                                // 5. With the final headers ready, build and execute the call.
                                return finalHeadersMono.flatMap(finalHeaders -> {
                                    WebClient client = webClientBuilder.baseUrl(baseUrl).build();

                                    // 6. Prepare the request specification.
                                    WebClient.RequestBodySpec requestSpec = client
                                            .method(HttpMethod.valueOf(request.getHttpMethod().toUpperCase()))
                                            .uri(uriBuilder -> {
                                                uriBuilder.path(request.getPath());
                                                if (request.getParameters() != null && isQueryParamMethod(request.getHttpMethod())) {
                                                    request.getParameters().forEach((key, value) -> {
                                                        if (value != null) uriBuilder.queryParam(key, value.toString());
                                                    });
                                                }
                                                if ("api_key".equals(config.getAuth().getAuthType()) && "query".equalsIgnoreCase(config.getAuth().getAddApiKeyTo())) {
                                                    uriBuilder.queryParam(config.getAuth().getApiKeyName(), config.getAuth().getApiKey());
                                                }
                                                return uriBuilder.build();
                                            });

                                    // 7. Apply headers and body.
                                    finalHeaders.forEach(requestSpec::header);
                                    if (request.getHeaders() != null) {
                                        request.getHeaders().forEach(requestSpec::header);
                                    }

                                    Mono<String> responseMono;
                                    if (request.getParameters() != null && !isQueryParamMethod(request.getHttpMethod())) {
                                        responseMono = requestSpec.bodyValue(request.getParameters()).retrieve().bodyToMono(String.class);
                                    } else {
                                        responseMono = requestSpec.retrieve().bodyToMono(String.class);
                                    }
                                    
                                    // 8. Execute with timeout and retries.
                                    return responseMono
                                            .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                                            .retryWhen(Retry.backoff(config.getMaxRetries(), Duration.ofMillis(500))
                                                    .filter(this::isRetryableException))
                                            .map(responseBody -> buildSuccessResponse(responseBody, request, startTime, loggingEnabled))
                                            .doOnError(error -> {
                                                if (loggingEnabled) {
                                                    log.error("API call to '{}' failed: {}", request.getApiName(), error.getMessage());
                                                }
                                            });
                                });
                            });
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
                    webEx.getStatusCode().value()
            );
        } else if (throwable instanceof java.util.concurrent.TimeoutException) {
            failureResponse = ApiCallResponse.failure("API call timeout", 408);
        } else {
            failureResponse = ApiCallResponse.failure("Unexpected error: " + throwable.getMessage(), 500);
        }

        return Mono.just(failureResponse
                .withApiDetails(request.getApiName(), request.getEndpoint(), request.getHttpMethod())
                .withExecutionTime(executionTime));
    }
    
    /**
     * Builds a standardized success response object.
     */
    private ApiCallResponse buildSuccessResponse(String responseBody, ApiCallRequest request, long startTime, boolean loggingEnabled) {
        long executionTime = System.currentTimeMillis() - startTime;
        if (loggingEnabled) {
            log.info("API call for '{}' completed in {}ms", request.getApiName(), executionTime);
        }
        // Keep response payload minimal (just the body as-is) and avoid duplicating raw/parsed data
        return ApiCallResponse.success(responseBody, 200)
                .withApiDetails(request.getApiName(), request.getEndpoint(), request.getHttpMethod())
                .withExecutionTime(executionTime);
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