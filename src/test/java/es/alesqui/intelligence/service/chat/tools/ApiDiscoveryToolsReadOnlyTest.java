package es.alesqui.intelligence.service.chat.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;

import com.fasterxml.jackson.databind.ObjectMapper;

import es.alesqui.intelligence.model.api_spec.unified.ApiConfiguration;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedApiDocument;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedEndpoint;
import es.alesqui.intelligence.service.UnifiedApiService;
import es.alesqui.intelligence.service.access.ApiGroupLinkService;
import es.alesqui.intelligence.service.chat.tools.support.InspectionPolicyService;
import es.alesqui.intelligence.service.identity.UserService;
import reactor.core.publisher.Mono;

/**
 * Unit tests for ApiDiscoveryTools focusing on the readOnly property of ApiConfiguration.
 * Tests that when readOnly is true, only GET endpoints are listed.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ApiDiscoveryTools - ReadOnly Configuration Tests")
class ApiDiscoveryToolsReadOnlyTest {

    @Mock
    private UnifiedApiService unifiedApiService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private InspectionPolicyService inspectionPolicy;

    @Mock
    private ApiGroupLinkService apiGroupLinkService;

    @Mock
    private UserService userService;

    @InjectMocks
    private ApiDiscoveryTools apiDiscoveryTools;

    private ToolContext toolContext;
    private UnifiedApiDocument testApi;

    @BeforeEach
    void setUp() {
        // Setup basic tool context
        Map<String, Object> contextMap = new HashMap<>();
        contextMap.put("userId", "test-user-id");
        toolContext = ToolContext.builder().context(contextMap).build();

        // Setup test API with multiple endpoints
        testApi = UnifiedApiDocument.builder()
                .id("test-api-id")
                .name("Test API")
                .description("Test API for readOnly tests")
                .endpoints(Arrays.asList(
                        UnifiedEndpoint.builder()
                                .operationId("getUsers")
                                .method("GET")
                                .path("/users")
                                .summary("Get all users")
                                .build(),
                        UnifiedEndpoint.builder()
                                .operationId("createUser")
                                .method("POST")
                                .path("/users")
                                .summary("Create a new user")
                                .build(),
                        UnifiedEndpoint.builder()
                                .operationId("getUserById")
                                .method("GET")
                                .path("/users/{id}")
                                .summary("Get user by ID")
                                .build(),
                        UnifiedEndpoint.builder()
                                .operationId("updateUser")
                                .method("PUT")
                                .path("/users/{id}")
                                .summary("Update user")
                                .build(),
                        UnifiedEndpoint.builder()
                                .operationId("deleteUser")
                                .method("DELETE")
                                .path("/users/{id}")
                                .summary("Delete user")
                                .build()
                ))
                .build();
    }

    @Test
    @DisplayName("Should list all endpoints when readOnly is false")
    void testListEndpoints_ReadOnlyFalse() {
        // Given: API with readOnly = false
        ApiConfiguration config = ApiConfiguration.builder()
                .readOnly(false)
                .build();
        testApi.setApiConfiguration(config);

        when(unifiedApiService.findByName("Test API")).thenReturn(Mono.just(testApi));
        when(apiGroupLinkService.canAccess(anyString(), anyString())).thenReturn(Mono.just(true));

        // When: listEndpoints is called
        String result = apiDiscoveryTools.listEndpoints("Test API", toolContext);

        // Then: All endpoints should be listed (2 GET, 1 POST, 1 PUT, 1 DELETE = 5 total)
        assertThat(result).contains("Available endpoints for API 'Test API'");
        assertThat(result).contains("getUsers");
        assertThat(result).contains("createUser");
        assertThat(result).contains("getUserById");
        assertThat(result).contains("updateUser");
        assertThat(result).contains("deleteUser");
        assertThat(result).contains("Method: GET");
        assertThat(result).contains("Method: POST");
        assertThat(result).contains("Method: PUT");
        assertThat(result).contains("Method: DELETE");
    }

    @Test
    @DisplayName("Should list all endpoints when apiConfiguration is null")
    void testListEndpoints_NoConfiguration() {
        // Given: API with no apiConfiguration
        testApi.setApiConfiguration(null);

        when(unifiedApiService.findByName("Test API")).thenReturn(Mono.just(testApi));
        when(apiGroupLinkService.canAccess(anyString(), anyString())).thenReturn(Mono.just(true));

        // When: listEndpoints is called
        String result = apiDiscoveryTools.listEndpoints("Test API", toolContext);

        // Then: All endpoints should be listed (default behavior)
        assertThat(result).contains("Available endpoints for API 'Test API'");
        assertThat(result).contains("getUsers");
        assertThat(result).contains("createUser");
        assertThat(result).contains("getUserById");
        assertThat(result).contains("updateUser");
        assertThat(result).contains("deleteUser");
    }

    @Test
    @DisplayName("Should list only GET endpoints when readOnly is true")
    void testListEndpoints_ReadOnlyTrue() {
        // Given: API with readOnly = true
        ApiConfiguration config = ApiConfiguration.builder()
                .readOnly(true)
                .build();
        testApi.setApiConfiguration(config);

        when(unifiedApiService.findByName("Test API")).thenReturn(Mono.just(testApi));
        when(apiGroupLinkService.canAccess(anyString(), anyString())).thenReturn(Mono.just(true));

        // When: listEndpoints is called
        String result = apiDiscoveryTools.listEndpoints("Test API", toolContext);

        // Then: Only GET endpoints should be listed
        assertThat(result).contains("Available endpoints for API 'Test API'");
        assertThat(result).contains("getUsers");
        assertThat(result).contains("getUserById");
        assertThat(result).contains("Method: GET");
        
        // POST, PUT, DELETE endpoints should NOT be present
        assertThat(result).doesNotContain("createUser");
        assertThat(result).doesNotContain("updateUser");
        assertThat(result).doesNotContain("deleteUser");
        assertThat(result).doesNotContain("Method: POST");
        assertThat(result).doesNotContain("Method: PUT");
        assertThat(result).doesNotContain("Method: DELETE");
    }

    @Test
    @DisplayName("Should return appropriate message when readOnly is true and no GET endpoints exist")
    void testListEndpoints_ReadOnlyTrue_NoGetEndpoints() {
        // Given: API with readOnly = true but only has non-GET endpoints
        ApiConfiguration config = ApiConfiguration.builder()
                .readOnly(true)
                .build();
        
        UnifiedApiDocument apiWithoutGetEndpoints = UnifiedApiDocument.builder()
                .id("test-api-id")
                .name("Test API")
                .description("Test API without GET endpoints")
                .apiConfiguration(config)
                .endpoints(Arrays.asList(
                        UnifiedEndpoint.builder()
                                .operationId("createUser")
                                .method("POST")
                                .path("/users")
                                .summary("Create a new user")
                                .build(),
                        UnifiedEndpoint.builder()
                                .operationId("updateUser")
                                .method("PUT")
                                .path("/users/{id}")
                                .summary("Update user")
                                .build()
                ))
                .build();

        when(unifiedApiService.findByName("Test API")).thenReturn(Mono.just(apiWithoutGetEndpoints));
        when(apiGroupLinkService.canAccess(anyString(), anyString())).thenReturn(Mono.just(true));

        // When: listEndpoints is called
        String result = apiDiscoveryTools.listEndpoints("Test API", toolContext);

        // Then: Should return message indicating no available endpoints for read-only API
        assertThat(result).contains("The API 'Test API' has no available endpoints");
        assertThat(result).contains("API is configured as read-only, only GET endpoints are available");
    }

    @Test
    @DisplayName("Should handle case-insensitive method matching for GET")
    void testListEndpoints_CaseInsensitiveGet() {
        // Given: API with readOnly = true and endpoints with different case
        ApiConfiguration config = ApiConfiguration.builder()
                .readOnly(true)
                .build();
        
        UnifiedApiDocument apiWithMixedCase = UnifiedApiDocument.builder()
                .id("test-api-id")
                .name("Test API")
                .description("Test API with mixed case methods")
                .apiConfiguration(config)
                .endpoints(Arrays.asList(
                        UnifiedEndpoint.builder()
                                .operationId("getUsers")
                                .method("get")  // lowercase
                                .path("/users")
                                .summary("Get all users")
                                .build(),
                        UnifiedEndpoint.builder()
                                .operationId("getUserById")
                                .method("Get")  // mixed case
                                .path("/users/{id}")
                                .summary("Get user by ID")
                                .build(),
                        UnifiedEndpoint.builder()
                                .operationId("createUser")
                                .method("POST")
                                .path("/users")
                                .summary("Create a new user")
                                .build()
                ))
                .build();

        when(unifiedApiService.findByName("Test API")).thenReturn(Mono.just(apiWithMixedCase));
        when(apiGroupLinkService.canAccess(anyString(), anyString())).thenReturn(Mono.just(true));

        // When: listEndpoints is called
        String result = apiDiscoveryTools.listEndpoints("Test API", toolContext);

        // Then: All GET variants should be included
        assertThat(result).contains("getUsers");
        assertThat(result).contains("getUserById");
        assertThat(result).doesNotContain("createUser");
    }
}
