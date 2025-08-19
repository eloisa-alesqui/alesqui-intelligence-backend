package es.alesqui.intelligence.service.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import es.alesqui.intelligence.dto.chat.request.ApiCallRequest;
import es.alesqui.intelligence.dto.chat.response.ApiCallResponse;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class ApiExecutionService {

  private final WebClient.Builder webClientBuilder;
  private final ApiConfigurationService apiConfigurationService;
  private final Map<String, WebClient> webClientCache = new ConcurrentHashMap<>();

  @Autowired
  public ApiExecutionService(WebClient.Builder webClientBuilder, 
                            ApiConfigurationService apiConfigurationService) {
      this.webClientBuilder = webClientBuilder;
      this.apiConfigurationService = apiConfigurationService;
  }

  /**
   * Execute API call with full error handling and retry logic
   */
  public Mono<ApiCallResponse> executeApiCall(ApiCallRequest request) {
      long startTime = System.currentTimeMillis();

      return validateRequest(request)
              .flatMap(this::buildWebClient)
              .flatMap(webClient -> performApiCall(webClient, request, startTime))
              .onErrorResume(throwable -> handleError(throwable, request, startTime));
  }

  /**
   * Validate the API request
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
   * Build or get cached WebClient for the API
   */
  private Mono<WebClient> buildWebClient(ApiCallRequest request) {
      return Mono.fromCallable(() -> {
          String cacheKey = request.getApiName();

          return webClientCache.computeIfAbsent(cacheKey, key -> {
              WebClient.Builder builder = webClientBuilder.clone();

              // Set base URL from configuration service
              String baseUrl = apiConfigurationService.getBaseUrl(request.getApiName());
              if (baseUrl != null && !baseUrl.isEmpty()) {
                  builder.baseUrl(baseUrl);
              }

              // Set default headers
              builder.defaultHeaders(headers -> {
                  headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                  headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
                  headers.set(HttpHeaders.USER_AGENT, "AlesquiIntelligence-Client/1.0");

                  // Add API-specific headers from configuration
                  Map<String, String> apiHeaders = apiConfigurationService.getHeaders(request.getApiName());
                  if (apiHeaders != null && !apiHeaders.isEmpty()) {
                      apiHeaders.forEach(headers::set);
                  }

                  // Add authentication headers if required
                  if (apiConfigurationService.isAuthRequired(request.getApiName())) {
                      String authToken = apiConfigurationService.getAuthToken(request.getApiName());
                      String authType = apiConfigurationService.getAuthType(request.getApiName());
                      
                      if (authToken != null && !authToken.isEmpty()) {
                          addAuthenticationHeader(headers, authType, authToken);
                      }
                  }
              });

              return builder
                      .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
                      .build();
          });
      });
  }

  /**
   * Add authentication header based on auth type
   */
  private void addAuthenticationHeader(HttpHeaders headers, String authType, String authToken) {
      switch (authType.toLowerCase()) {
          case "bearer":
              headers.setBearerAuth(authToken);
              break;
          case "basic":
              headers.set(HttpHeaders.AUTHORIZATION, "Basic " + authToken);
              break;
          case "apikey":
              headers.set("X-API-Key", authToken);
              break;
          case "custom":
              // For custom auth, assume the token already includes the prefix
              headers.set(HttpHeaders.AUTHORIZATION, authToken);
              break;
          default:
              // Default to Bearer if type is unknown
              headers.setBearerAuth(authToken);
              break;
      }
  }

  /**
   * Perform the actual API call
   */
  private Mono<ApiCallResponse> performApiCall(WebClient webClient, ApiCallRequest request, long startTime) {
      // Get configuration values
      int timeoutSeconds = getTimeoutFromRequest(request);
      int maxRetries = apiConfigurationService.getMaxRetries(request.getApiName());
      boolean loggingEnabled = apiConfigurationService.isLoggingEnabled(request.getApiName());

      // Log request if enabled
      if (loggingEnabled) {
          log.info("Executing {} {} for API: {} (timeout: {}s, retries: {})", 
                  request.getHttpMethod(), request.getEndpoint(), 
                  request.getApiName(), timeoutSeconds, maxRetries);
      }

      WebClient.RequestBodySpec requestSpec = webClient
              .method(HttpMethod.valueOf(request.getHttpMethod().toUpperCase()))
              .uri(uriBuilder -> {
                  uriBuilder.path(request.getPath());

                  // Add query parameters for GET and DELETE requests
                  Map<String, Object> queryParams = getQueryParameters(request);
                  if (!queryParams.isEmpty()) {
                      queryParams.forEach((key, value) -> {
                          if (value != null) {
                              uriBuilder.queryParam(key, value.toString());
                          }
                      });
                  }

                  return uriBuilder.build();
              });

      // Add request headers
      if (request.hasHeaders()) {
          request.getHeaders().forEach(requestSpec::header);
      }

      // Determine if we need to send a body
      Map<String, Object> bodyParams = getBodyParameters(request);
      Mono<String> responseMono;

      if (!bodyParams.isEmpty()) {
          // Send body for POST/PUT/PATCH requests
          responseMono = requestSpec.bodyValue(bodyParams).retrieve().bodyToMono(String.class);
      } else {
          // No body for GET/DELETE requests
          responseMono = requestSpec.retrieve().bodyToMono(String.class);
      }

      return responseMono
              .timeout(Duration.ofSeconds(timeoutSeconds))
              .retryWhen(Retry.backoff(maxRetries, Duration.ofMillis(500))
                      .filter(this::isRetryableException))
              .map(responseBody -> {
                  long executionTime = System.currentTimeMillis() - startTime;

                  if (loggingEnabled) {
                      log.info("API call completed for {} in {}ms", request.getApiName(), executionTime);
                  }

                  return ApiCallResponse
                          .success(parseResponseBody(responseBody), 200, request.getConversationId())
                          .withApiDetails(request.getApiName(), request.getEndpoint(), request.getHttpMethod())
                          .withExecutionTime(executionTime)
                          .withRawResponse(responseBody);
              })
              .doOnError(error -> {
                  if (loggingEnabled) {
                      log.error("API call failed: {} {} - {}", 
                              request.getApiName(), request.getEndpoint(), error.getMessage());
                  }
              });
  }

  /**
   * Separate query parameters from body parameters based on HTTP method
   */
  private Map<String, Object> getQueryParameters(ApiCallRequest request) {
      if (request.getHttpMethod().equalsIgnoreCase("GET") || 
          request.getHttpMethod().equalsIgnoreCase("DELETE")) {
          return request.getParameters() != null ? request.getParameters() : new HashMap<>();
      }
      return new HashMap<>();
  }

  /**
   * Get body parameters for POST/PUT/PATCH requests
   */
  private Map<String, Object> getBodyParameters(ApiCallRequest request) {
      if (request.getHttpMethod().equalsIgnoreCase("POST") || 
          request.getHttpMethod().equalsIgnoreCase("PUT") ||
          request.getHttpMethod().equalsIgnoreCase("PATCH")) {
          return request.getParameters() != null ? request.getParameters() : new HashMap<>();
      }
      return new HashMap<>();
  }

  /**
   * Get timeout from request or configuration service
   */
  private int getTimeoutFromRequest(ApiCallRequest request) {
      // 1. Use request timeout if specified and valid
      if (request.getTimeoutMs() > 0) {
          int timeoutSeconds = Math.max(request.getTimeoutMs() / 1000, 1);
          return Math.min(timeoutSeconds, 300); // Max 5 minutes
      }

      // 2. Get timeout from configuration service
      try {
          int configTimeout = apiConfigurationService.getTimeout(request.getApiName());
          return Math.max(configTimeout, 1);
      } catch (Exception e) {
          log.warn("Failed to get timeout from configuration for {}: {}", 
                  request.getApiName(), e.getMessage());
      }

      // 3. Use default timeout based on HTTP method
      return getDefaultTimeoutByMethod(request.getHttpMethod());
  }

  /**
   * Get default timeout by HTTP method
   */
  private int getDefaultTimeoutByMethod(String httpMethod) {
      switch (httpMethod.toUpperCase()) {
          case "GET":
          case "HEAD":
          case "OPTIONS":
              return 15;
          case "POST":
          case "PUT":
          case "PATCH":
              return 30;
          case "DELETE":
              return 20;
          default:
              return apiConfigurationService.getDefaultTimeout();
      }
  }

  /**
   * Handle errors during API execution
   */
  private Mono<ApiCallResponse> handleError(Throwable throwable, ApiCallRequest request, long startTime) {
      long executionTime = System.currentTimeMillis() - startTime;
      boolean loggingEnabled = apiConfigurationService.isLoggingEnabled(request.getApiName());

      if (loggingEnabled) {
          log.error("API execution error for {}: {}", request.getApiName(), throwable.getMessage(), throwable);
      }

      if (throwable instanceof WebClientResponseException) {
          WebClientResponseException webEx = (WebClientResponseException) throwable;

          return Mono.just(ApiCallResponse
                  .failure("API call failed: " + webEx.getMessage(), 
                          webEx.getStatusCode().value(), 
                          request.getConversationId())
                  .withApiDetails(request.getApiName(), request.getEndpoint(), request.getHttpMethod())
                  .withExecutionTime(executionTime)
                  .withRawResponse(webEx.getResponseBodyAsString()));
      }

      if (throwable instanceof java.util.concurrent.TimeoutException ||
          throwable.getCause() instanceof java.util.concurrent.TimeoutException) {
          return Mono.just(ApiCallResponse
                  .failure("API call timeout", 408, request.getConversationId())
                  .withApiDetails(request.getApiName(), request.getEndpoint(), request.getHttpMethod())
                  .withExecutionTime(executionTime));
      }

      return Mono.just(ApiCallResponse
              .failure("Unexpected error: " + throwable.getMessage(), 500, request.getConversationId())
              .withApiDetails(request.getApiName(), request.getEndpoint(), request.getHttpMethod())
              .withExecutionTime(executionTime));
  }

  /**
   * Parse response body to Map
   */
  private Map<String, Object> parseResponseBody(String responseBody) {
      try {
          // Simple JSON-like parsing - you can use Jackson ObjectMapper here for better parsing
          if (responseBody == null || responseBody.trim().isEmpty()) {
              return Map.of(
                      "message", "Empty response",
                      "timestamp", System.currentTimeMillis(),
                      "success", true
              );
          }

          return Map.of(
                  "response", responseBody,
                  "timestamp", System.currentTimeMillis(),
                  "success", true
          );
      } catch (Exception e) {
          log.warn("Failed to parse response body: {}", e.getMessage());
          return Map.of(
                  "error", "Failed to parse response",
                  "rawResponse", responseBody != null ? responseBody : "",
                  "success", false
          );
      }
  }

  /**
   * Check if exception is retryable
   */
  private boolean isRetryableException(Throwable throwable) {
      if (throwable instanceof WebClientResponseException) {
          WebClientResponseException webEx = (WebClientResponseException) throwable;
          int statusCode = webEx.getStatusCode().value();

          // Retry on server errors (5xx) and some client errors
          return statusCode >= 500 || statusCode == 429 || statusCode == 408;
      }

      return throwable instanceof java.net.ConnectException || 
             throwable instanceof java.net.SocketTimeoutException ||
             throwable instanceof java.io.IOException;
  }

  /**
   * Clear WebClient cache (useful for configuration changes)
   */
  public void clearCache() {
      webClientCache.clear();
      log.info("WebClient cache cleared");
  }

  /**
   * Clear cache for specific API
   */
  public void clearCacheForApi(String apiName) {
      webClientCache.remove(apiName);
      log.info("WebClient cache cleared for API: {}", apiName);
  }

  /**
   * Get cached WebClient count
   */
  public int getCacheSize() {
      return webClientCache.size();
  }

  /**
   * Get cache statistics
   */
  public Map<String, Object> getCacheStats() {
      return Map.of(
              "cacheSize", webClientCache.size(),
              "cachedApis", webClientCache.keySet()
      );
  }
}