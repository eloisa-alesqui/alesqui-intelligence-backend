package es.alesqui.intelligence.service.chat.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
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

import es.alesqui.intelligence.model.api_spec.unified.ApiConfiguration;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedApiDocument;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedEndpoint;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedParameter;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedTag;
import es.alesqui.intelligence.service.UnifiedApiService;
import es.alesqui.intelligence.service.access.ApiGroupLinkService;
import es.alesqui.intelligence.service.chat.tools.support.InspectionPolicyService;
import es.alesqui.intelligence.service.identity.UserService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApiDiscoveryTools")
class ApiDiscoveryToolsTest {

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

    @BeforeEach
    void setUp() {
        Map<String, Object> contextMap = new HashMap<>();
        contextMap.put(ToolContextKeys.USER_ID, "test-user-id");
        contextMap.put(ToolContextKeys.CONVERSATION_ID, "test-conversation-id");
        toolContext = new ToolContext(contextMap);
    }

    private UnifiedApiDocument buildApi(String id, String name, String description, List<UnifiedEndpoint> endpoints) {
        return UnifiedApiDocument.builder()
                .id(id)
                .name(name)
                .description(description)
                .endpoints(endpoints)
                .build();
    }

    private UnifiedEndpoint buildEndpoint(String operationId, String method, String path, String summary) {
        return UnifiedEndpoint.builder()
                .operationId(operationId)
                .method(method)
                .path(path)
                .summary(summary)
                .build();
    }

    @Nested
    @DisplayName("listApis")
    class ListApis {

        @Test
        @DisplayName("Should return message when no APIs found")
        void noApisFound() {
            when(apiGroupLinkService.listVisibleApis("test-user-id")).thenReturn(Flux.empty());

            String result = apiDiscoveryTools.listApis(toolContext);

            assertThat(result).contains("No available APIs");
        }

        @Test
        @DisplayName("Should list multiple APIs with details")
        void multipleApis() {
            UnifiedApiDocument api1 = buildApi("1", "Users API", "Manage users", Collections.emptyList());
            api1.setTags(List.of(UnifiedTag.builder().name("users").build()));
            api1.setCapabilitiesSummary("CRUD operations for users");

            UnifiedApiDocument api2 = buildApi("2", "Orders API", "Manage orders", Collections.emptyList());
            api2.setTags(List.of(UnifiedTag.builder().name("orders").build()));

            when(apiGroupLinkService.listVisibleApis("test-user-id")).thenReturn(Flux.just(api1, api2));

            String result = apiDiscoveryTools.listApis(toolContext);

            assertThat(result).contains("Users API", "Orders API");
            assertThat(result).contains("Manage users", "Manage orders");
            assertThat(result).contains("users", "orders");
            assertThat(result).contains("CRUD operations for users");
        }
    }

    @Nested
    @DisplayName("listEndpoints")
    class ListEndpoints {

        @Test
        @DisplayName("Should return error when API not found")
        void apiNotFound() {
            when(unifiedApiService.findByName("Unknown API")).thenReturn(Mono.empty());

            String result = apiDiscoveryTools.listEndpoints("Unknown API", toolContext);

            assertThat(result).contains("Error", "No API was found");
        }

        @Test
        @DisplayName("Should return error when user has no permission")
        void noPermission() {
            UnifiedApiDocument api = buildApi("1", "Restricted API", "Restricted", Collections.emptyList());
            when(unifiedApiService.findByName("Restricted API")).thenReturn(Mono.just(api));
            when(apiGroupLinkService.canAccess("test-user-id", "1")).thenReturn(Mono.just(false));

            String result = apiDiscoveryTools.listEndpoints("Restricted API", toolContext);

            assertThat(result).contains("permission");
        }

        @Test
        @DisplayName("Should list endpoints with details")
        void listEndpointsHappyPath() {
            UnifiedEndpoint getEndpoint = UnifiedEndpoint.builder()
                    .operationId("getUsers")
                    .method("GET")
                    .path("/users")
                    .summary("Get all users")
                    .parameters(List.of(
                            UnifiedParameter.builder().name("page").build(),
                            UnifiedParameter.builder().name("size").build()
                    ))
                    .build();

            UnifiedEndpoint postEndpoint = buildEndpoint("createUser", "POST", "/users", "Create user");

            UnifiedApiDocument api = buildApi("1", "Users API", "Users", List.of(getEndpoint, postEndpoint));
            when(unifiedApiService.findByName("Users API")).thenReturn(Mono.just(api));
            when(apiGroupLinkService.canAccess("test-user-id", "1")).thenReturn(Mono.just(true));

            String result = apiDiscoveryTools.listEndpoints("Users API", toolContext);

            assertThat(result).contains("getUsers", "createUser");
            assertThat(result).contains("GET", "POST");
            assertThat(result).contains("/users");
            assertThat(result).contains("page", "size");
        }

        @Test
        @DisplayName("Should handle empty endpoints list")
        void emptyEndpoints() {
            UnifiedApiDocument api = buildApi("1", "Empty API", "No endpoints", Collections.emptyList());
            when(unifiedApiService.findByName("Empty API")).thenReturn(Mono.just(api));
            when(apiGroupLinkService.canAccess("test-user-id", "1")).thenReturn(Mono.just(true));

            String result = apiDiscoveryTools.listEndpoints("Empty API", toolContext);

            assertThat(result).contains("no available endpoints");
        }

        @Nested
        @DisplayName("ReadOnly configuration")
        class ReadOnly {

            private UnifiedApiDocument testApi;

            @BeforeEach
            void setUpReadOnlyApi() {
                testApi = UnifiedApiDocument.builder()
                        .id("test-api-id")
                        .name("Test API")
                        .description("Test API for readOnly tests")
                        .endpoints(Arrays.asList(
                                buildEndpoint("getUsers", "GET", "/users", "Get all users"),
                                buildEndpoint("createUser", "POST", "/users", "Create a new user"),
                                buildEndpoint("getUserById", "GET", "/users/{id}", "Get user by ID"),
                                buildEndpoint("updateUser", "PUT", "/users/{id}", "Update user"),
                                buildEndpoint("deleteUser", "DELETE", "/users/{id}", "Delete user")
                        ))
                        .build();
            }

            @Test
            @DisplayName("Should list all endpoints when readOnly is false")
            void readOnlyFalse() {
                testApi.setApiConfiguration(ApiConfiguration.builder().readOnly(false).build());
                when(unifiedApiService.findByName("Test API")).thenReturn(Mono.just(testApi));
                when(apiGroupLinkService.canAccess(anyString(), anyString())).thenReturn(Mono.just(true));

                String result = apiDiscoveryTools.listEndpoints("Test API", toolContext);

                assertThat(result).contains("getUsers", "createUser", "getUserById", "updateUser", "deleteUser");
                assertThat(result).contains("Method: GET", "Method: POST", "Method: PUT", "Method: DELETE");
            }

            @Test
            @DisplayName("Should list all endpoints when apiConfiguration is null")
            void noConfiguration() {
                testApi.setApiConfiguration(null);
                when(unifiedApiService.findByName("Test API")).thenReturn(Mono.just(testApi));
                when(apiGroupLinkService.canAccess(anyString(), anyString())).thenReturn(Mono.just(true));

                String result = apiDiscoveryTools.listEndpoints("Test API", toolContext);

                assertThat(result).contains("getUsers", "createUser", "getUserById", "updateUser", "deleteUser");
            }

            @Test
            @DisplayName("Should list only GET endpoints when readOnly is true")
            void readOnlyTrue() {
                testApi.setApiConfiguration(ApiConfiguration.builder().readOnly(true).build());
                when(unifiedApiService.findByName("Test API")).thenReturn(Mono.just(testApi));
                when(apiGroupLinkService.canAccess(anyString(), anyString())).thenReturn(Mono.just(true));

                String result = apiDiscoveryTools.listEndpoints("Test API", toolContext);

                assertThat(result).contains("getUsers", "getUserById", "Method: GET");
                assertThat(result).doesNotContain("createUser", "updateUser", "deleteUser");
                assertThat(result).doesNotContain("Method: POST", "Method: PUT", "Method: DELETE");
            }

            @Test
            @DisplayName("Should return message when readOnly is true and no GET endpoints exist")
            void readOnlyTrueNoGetEndpoints() {
                UnifiedApiDocument apiNoGet = UnifiedApiDocument.builder()
                        .id("test-api-id")
                        .name("Test API")
                        .description("No GET endpoints")
                        .apiConfiguration(ApiConfiguration.builder().readOnly(true).build())
                        .endpoints(Arrays.asList(
                                buildEndpoint("createUser", "POST", "/users", "Create user"),
                                buildEndpoint("updateUser", "PUT", "/users/{id}", "Update user")
                        ))
                        .build();

                when(unifiedApiService.findByName("Test API")).thenReturn(Mono.just(apiNoGet));
                when(apiGroupLinkService.canAccess(anyString(), anyString())).thenReturn(Mono.just(true));

                String result = apiDiscoveryTools.listEndpoints("Test API", toolContext);

                assertThat(result).contains("no available endpoints");
                assertThat(result).contains("read-only");
            }

            @Test
            @DisplayName("Should handle case-insensitive method matching for GET")
            void caseInsensitiveGet() {
                UnifiedApiDocument apiMixedCase = UnifiedApiDocument.builder()
                        .id("test-api-id")
                        .name("Test API")
                        .description("Mixed case methods")
                        .apiConfiguration(ApiConfiguration.builder().readOnly(true).build())
                        .endpoints(Arrays.asList(
                                buildEndpoint("getUsers", "get", "/users", "Get all users"),
                                buildEndpoint("getUserById", "Get", "/users/{id}", "Get user by ID"),
                                buildEndpoint("createUser", "POST", "/users", "Create user")
                        ))
                        .build();

                when(unifiedApiService.findByName("Test API")).thenReturn(Mono.just(apiMixedCase));
                when(apiGroupLinkService.canAccess(anyString(), anyString())).thenReturn(Mono.just(true));

                String result = apiDiscoveryTools.listEndpoints("Test API", toolContext);

                assertThat(result).contains("getUsers", "getUserById");
                assertThat(result).doesNotContain("createUser");
            }
        }
    }

    @Nested
    @DisplayName("inspectEndpoint")
    class InspectEndpoint {

        @Test
        @DisplayName("Should return error when API not found")
        void apiNotFound() {
            when(unifiedApiService.findByName("Unknown")).thenReturn(Mono.empty());

            String result = apiDiscoveryTools.inspectEndpoint("Unknown", "op1", toolContext);

            assertThat(result).contains("Error", "No API found");
        }

        @Test
        @DisplayName("Should return error when user has no permission")
        void noPermission() {
            UnifiedApiDocument api = buildApi("1", "Restricted", "R", Collections.emptyList());
            when(unifiedApiService.findByName("Restricted")).thenReturn(Mono.just(api));
            when(apiGroupLinkService.canAccess("test-user-id", "1")).thenReturn(Mono.just(false));

            String result = apiDiscoveryTools.inspectEndpoint("Restricted", "op1", toolContext);

            assertThat(result).contains("permission");
        }

        @Test
        @DisplayName("Should return error when endpoint not found")
        void endpointNotFound() {
            UnifiedApiDocument api = buildApi("1", "Test API", "Test",
                    List.of(buildEndpoint("getUsers", "GET", "/users", "Get users")));
            when(unifiedApiService.findByName("Test API")).thenReturn(Mono.just(api));
            when(apiGroupLinkService.canAccess("test-user-id", "1")).thenReturn(Mono.just(true));

            String result = apiDiscoveryTools.inspectEndpoint("Test API", "nonExistent", toolContext);

            assertThat(result).contains("Error", "No endpoint found", "nonExistent");
        }

        @Test
        @DisplayName("Should return endpoint details and record inspection")
        void inspectEndpointHappyPath() {
            UnifiedEndpoint endpoint = UnifiedEndpoint.builder()
                    .operationId("getUsers")
                    .method("GET")
                    .path("/users")
                    .summary("Get all users")
                    .description("Returns a list of all users in the system")
                    .build();

            UnifiedApiDocument api = buildApi("1", "Test API", "Test", List.of(endpoint));
            when(unifiedApiService.findByName("Test API")).thenReturn(Mono.just(api));
            when(apiGroupLinkService.canAccess("test-user-id", "1")).thenReturn(Mono.just(true));

            String result = apiDiscoveryTools.inspectEndpoint("Test API", "getUsers", toolContext);

            assertThat(result).contains("getUsers", "GET", "/users");
            assertThat(result).contains("Get all users");
            assertThat(result).contains("Returns a list of all users");
            verify(inspectionPolicy).recordInspection("test-conversation-id", "Test API", "getUsers");
        }
    }
}
