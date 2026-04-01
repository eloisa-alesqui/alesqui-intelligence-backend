package es.alesqui.intelligence.service.chat.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;

import com.fasterxml.jackson.databind.ObjectMapper;

import es.alesqui.intelligence.dto.chat.request.ApiCallRequest;
import es.alesqui.intelligence.dto.chat.response.ApiCallResponse;
import es.alesqui.intelligence.dto.chat.response.StructuredApiError;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedApiDocument;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedEndpoint;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedParameter;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedSchema;
import es.alesqui.intelligence.service.UnifiedApiService;
import es.alesqui.intelligence.service.access.ApiGroupLinkService;
import es.alesqui.intelligence.service.api.ApiExecutionService;
import es.alesqui.intelligence.service.chat.tools.support.InspectionPolicyService;
import es.alesqui.intelligence.service.identity.UserService;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApiInvocationTools")
class ApiInvocationToolsTest {

    @Mock
    private UnifiedApiService unifiedApiService;

    @Mock
    private ApiExecutionService apiExecutionService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private InspectionPolicyService inspectionPolicy;

    @Mock
    private ApiGroupLinkService apiGroupLinkService;

    @Mock
    private UserService userService;

    @InjectMocks
    private ApiInvocationTools apiInvocationTools;

    private ToolContext toolContext;

    @BeforeEach
    void setUp() {
        Map<String, Object> contextMap = new HashMap<>();
        contextMap.put(ToolContextKeys.USER_ID, "test-user-id");
        contextMap.put(ToolContextKeys.CONVERSATION_ID, "test-conversation-id");
        toolContext = new ToolContext(contextMap);
    }

    private UnifiedApiDocument buildApi(String id, String name, List<UnifiedEndpoint> endpoints) {
        return UnifiedApiDocument.builder()
                .id(id)
                .name(name)
                .endpoints(endpoints)
                .build();
    }

    private UnifiedEndpoint buildEndpoint(String operationId, String method, String path) {
        return UnifiedEndpoint.builder()
                .operationId(operationId)
                .method(method)
                .path(path)
                .build();
    }

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("Should execute API call successfully")
        void callApiSuccess() throws Exception {
            UnifiedEndpoint endpoint = buildEndpoint("getUsers", "GET", "/users");
            UnifiedApiDocument api = buildApi("1", "Users API", List.of(endpoint));

            when(unifiedApiService.findByName("Users API")).thenReturn(Mono.just(api));
            when(apiGroupLinkService.canAccess("test-user-id", "1")).thenReturn(Mono.just(true));
            when(inspectionPolicy.wasInspectedRecently(eq("test-conversation-id"), eq("Users API"), eq("getUsers"), any(Duration.class)))
                    .thenReturn(true);

            ApiCallResponse expectedResponse = ApiCallResponse.success(List.of(Map.of("name", "Alice")), 200);
            when(apiExecutionService.executeApiCall(any(ApiCallRequest.class))).thenReturn(Mono.just(expectedResponse));

            ApiCallResponse result = apiInvocationTools.callApi("Users API", "getUsers", "{}", toolContext);

            assertThat(result).isNotNull();
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getStatusCode()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("Error scenarios")
    class ErrorScenarios {

        @Test
        @DisplayName("Should return error when API not found")
        void apiNotFound() {
            when(unifiedApiService.findByName("Unknown")).thenReturn(Mono.empty());

            ApiCallResponse result = apiInvocationTools.callApi("Unknown", "op1", "{}", toolContext);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getStatusCode()).isEqualTo(502);
        }

        @Test
        @DisplayName("Should return 403 when user has no permission")
        void noPermission() {
            UnifiedEndpoint endpoint = buildEndpoint("getUsers", "GET", "/users");
            UnifiedApiDocument api = buildApi("1", "Restricted API", List.of(endpoint));

            when(unifiedApiService.findByName("Restricted API")).thenReturn(Mono.just(api));
            when(apiGroupLinkService.canAccess("test-user-id", "1")).thenReturn(Mono.just(false));

            ApiCallResponse result = apiInvocationTools.callApi("Restricted API", "getUsers", "{}", toolContext);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getStatusCode()).isEqualTo(403);
            assertThat(result.getResponseData()).isInstanceOf(StructuredApiError.class);
            StructuredApiError error = (StructuredApiError) result.getResponseData();
            assertThat(error.getErrorType()).isEqualTo("INSUFFICIENT_PRIVILEGES");
        }

        @Test
        @DisplayName("Should return 428 when inspection is required")
        void inspectionRequired() {
            UnifiedEndpoint endpoint = buildEndpoint("getUsers", "GET", "/users");
            UnifiedApiDocument api = buildApi("1", "Users API", List.of(endpoint));

            when(unifiedApiService.findByName("Users API")).thenReturn(Mono.just(api));
            when(apiGroupLinkService.canAccess("test-user-id", "1")).thenReturn(Mono.just(true));
            when(inspectionPolicy.wasInspectedRecently(anyString(), anyString(), anyString(), any(Duration.class)))
                    .thenReturn(false);

            ApiCallResponse result = apiInvocationTools.callApi("Users API", "getUsers", "{}", toolContext);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getStatusCode()).isEqualTo(428);
            StructuredApiError error = (StructuredApiError) result.getResponseData();
            assertThat(error.getErrorType()).isEqualTo("INSPECTION_REQUIRED");
        }

        @Test
        @DisplayName("Should return 400 for blank API name")
        void blankApiName() {
            ApiCallResponse result = apiInvocationTools.callApi("", "op1", "{}", toolContext);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getStatusCode()).isEqualTo(400);
        }

        @Test
        @DisplayName("Should return 400 for blank operation ID")
        void blankOperationId() {
            ApiCallResponse result = apiInvocationTools.callApi("Users API", "", "{}", toolContext);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getStatusCode()).isEqualTo(400);
        }

        @Test
        @DisplayName("Should return error when endpoint not found in API")
        void endpointNotFound() {
            UnifiedEndpoint endpoint = buildEndpoint("getUsers", "GET", "/users");
            UnifiedApiDocument api = buildApi("1", "Users API", List.of(endpoint));

            when(unifiedApiService.findByName("Users API")).thenReturn(Mono.just(api));
            when(apiGroupLinkService.canAccess("test-user-id", "1")).thenReturn(Mono.just(true));

            ApiCallResponse result = apiInvocationTools.callApi("Users API", "nonExistent", "{}", toolContext);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getStatusCode()).isEqualTo(502);
        }

        @Test
        @DisplayName("Should return 400 for invalid parameters JSON")
        void invalidParametersJson() throws Exception {
            UnifiedEndpoint endpoint = buildEndpoint("getUsers", "GET", "/users");
            UnifiedApiDocument api = buildApi("1", "Users API", List.of(endpoint));

            when(unifiedApiService.findByName("Users API")).thenReturn(Mono.just(api));
            when(apiGroupLinkService.canAccess("test-user-id", "1")).thenReturn(Mono.just(true));
            when(inspectionPolicy.wasInspectedRecently(anyString(), anyString(), anyString(), any(Duration.class)))
                    .thenReturn(true);
            when(objectMapper.readValue(eq("not-json"), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                    .thenThrow(new com.fasterxml.jackson.core.JsonParseException(null, "Unexpected character"));

            ApiCallResponse result = apiInvocationTools.callApi("Users API", "getUsers", "not-json", toolContext);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getStatusCode()).isEqualTo(400);
        }

        @Test
        @DisplayName("Should return 400 for enum validation failure")
        void enumValidationFailure() throws Exception {
            UnifiedSchema enumSchema = UnifiedSchema.builder()
                    .enumValues(List.of("active", "inactive", "pending"))
                    .build();
            UnifiedParameter statusParam = UnifiedParameter.builder()
                    .name("status")
                    .schema(enumSchema)
                    .build();
            UnifiedEndpoint endpoint = UnifiedEndpoint.builder()
                    .operationId("getUsers")
                    .method("GET")
                    .path("/users")
                    .parameters(List.of(statusParam))
                    .build();
            UnifiedApiDocument api = buildApi("1", "Users API", List.of(endpoint));

            when(unifiedApiService.findByName("Users API")).thenReturn(Mono.just(api));
            when(apiGroupLinkService.canAccess("test-user-id", "1")).thenReturn(Mono.just(true));
            when(inspectionPolicy.wasInspectedRecently(anyString(), anyString(), anyString(), any(Duration.class)))
                    .thenReturn(true);

            Map<String, Object> params = Map.of("status", "INVALID_VALUE");
            when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                    .thenReturn(new HashMap<>(params));

            ApiCallResponse result = apiInvocationTools.callApi("Users API", "getUsers", "{\"status\":\"INVALID_VALUE\"}", toolContext);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getStatusCode()).isEqualTo(400);
            StructuredApiError error = (StructuredApiError) result.getResponseData();
            assertThat(error.getErrorType()).isEqualTo("INVALID_PARAMETERS");
            assertThat(error.getDetails()).containsKey("allowedValues");
        }
    }
}
