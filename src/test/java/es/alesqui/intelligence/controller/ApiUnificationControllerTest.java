package es.alesqui.intelligence.controller;

import es.alesqui.intelligence.config.TestSecurityConfig;
import es.alesqui.intelligence.model.api_spec.postman.PostmanDocument;
import es.alesqui.intelligence.model.api_spec.swagger.SwaggerDocument;
import es.alesqui.intelligence.model.api_spec.unified.ApiConfiguration;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedApiDocument;
import es.alesqui.intelligence.security.JwtService;
import es.alesqui.intelligence.service.PostmanService;
import es.alesqui.intelligence.service.SwaggerService;
import es.alesqui.intelligence.service.UnifiedApiService;
import es.alesqui.intelligence.service.access.ApiGroupLinkService;
import es.alesqui.intelligence.service.identity.UserService;
import es.alesqui.intelligence.service.unification.ApiUnificationService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

/**
 * Controller-layer tests for ApiUnificationController.
 * Uses TestSecurityConfig to bypass the JWT filter.
 * userService mocks bypass the ReactiveSecurityContext dependency.
 */
@WebFluxTest(ApiUnificationController.class)
@Import(TestSecurityConfig.class)
class ApiUnificationControllerTest {

    @Autowired
    WebTestClient webClient;

    @MockitoBean ApiUnificationService apiUnificationService;
    @MockitoBean SwaggerService swaggerService;
    @MockitoBean PostmanService postmanService;
    @MockitoBean UnifiedApiService unifiedApiService;
    @MockitoBean ApiGroupLinkService apiGroupLinkService;
    @MockitoBean UserService userService;

    // Required by JwtAuthenticationWebFilter (@Component WebFilter auto-scanned)
    @MockitoBean JwtService jwtService;
    @MockitoBean ReactiveUserDetailsService reactiveUserDetailsService;

    private UnifiedApiDocument mockDoc;
    private SwaggerDocument mockSwagger;
    private PostmanDocument mockPostman;
    private ApiConfiguration mockConfig;

    @BeforeEach
    void setUp() {
        mockDoc = UnifiedApiDocument.builder()
                .id("api-1")
                .name("Pet Store API")
                .description("Pet store")
                .active(true)
                .team("platform")
                .build();

        mockSwagger = SwaggerDocument.builder()
                .id("sw-1")
                .name("Pet Store API")
                .team("platform")
                .build();

        mockPostman = PostmanDocument.builder()
                .id("pm-1")
                .name("Pet Store API")
                .team("platform")
                .build();

        mockConfig = ApiConfiguration.builder()
                .baseUrl("https://api.example.com")
                .timeoutSeconds(30)
                .build();
    }

    // ──────────────────────────── unify ────────────────────────────

    @Test
    void unify_withSwaggerAndPostman_returns200() {
        given(swaggerService.findByName("Pet Store API")).willReturn(Mono.just(mockSwagger));
        given(postmanService.findByName("Pet Store API")).willReturn(Mono.just(mockPostman));
        given(apiUnificationService.unifyApiDocuments(eq(mockSwagger), eq(mockPostman))).willReturn(mockDoc);
        given(apiUnificationService.save(eq(mockDoc), any(ServerHttpRequest.class))).willReturn(Mono.just(mockDoc));

        webClient.post()
                .uri("/api/unification/unify?apiName=Pet Store API")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> org.assertj.core.api.Assertions.assertThat(body).contains("saved successfully"));
    }

    @Test
    void unify_swaggerOnly_returns200() {
        given(swaggerService.findByName("Pet Store API")).willReturn(Mono.just(mockSwagger));
        given(postmanService.findByName("Pet Store API")).willReturn(Mono.empty());
        given(apiUnificationService.unifyApiDocuments(eq(mockSwagger), eq(null))).willReturn(mockDoc);
        given(apiUnificationService.save(eq(mockDoc), any(ServerHttpRequest.class))).willReturn(Mono.just(mockDoc));

        webClient.post()
                .uri("/api/unification/unify?apiName=Pet Store API")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> org.assertj.core.api.Assertions.assertThat(body).contains("saved successfully"));
    }

    @Test
    void unify_swaggerNotFound_returns500() {
        given(swaggerService.findByName("Missing API")).willReturn(Mono.empty());

        webClient.post()
                .uri("/api/unification/unify?apiName=Missing API")
                .exchange()
                .expectStatus().is5xxServerError()
                .expectBody(String.class)
                .value(body -> org.assertj.core.api.Assertions.assertThat(body).contains("Error during unification"));
    }

    // ──────────────────────────── updateConfiguration ────────────────────────────

    @Test
    void updateConfiguration_permitted_returns200() {
        UnifiedApiDocument updated = UnifiedApiDocument.builder()
                .id("api-1")
                .name("Pet Store API")
                .apiConfiguration(mockConfig)
                .build();

        given(unifiedApiService.findByName("Pet Store API")).willReturn(Mono.just(mockDoc));
        given(userService.getCurrentUsername()).willReturn(Mono.just("admin"));
        given(apiGroupLinkService.canModifyApi("admin", "api-1")).willReturn(Mono.just(true));
        given(unifiedApiService.updateApiConfiguration(eq("Pet Store API"), any(ApiConfiguration.class), any(ServerHttpRequest.class)))
                .willReturn(Mono.just(updated));

        webClient.put()
                .uri("/api/unification/Pet Store API/configuration")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mockConfig)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("Pet Store API");
    }

    @Test
    void updateConfiguration_forbidden_returns403() {
        given(unifiedApiService.findByName("Pet Store API")).willReturn(Mono.just(mockDoc));
        given(userService.getCurrentUsername()).willReturn(Mono.just("user"));
        given(apiGroupLinkService.canModifyApi("user", "api-1")).willReturn(Mono.just(false));

        webClient.put()
                .uri("/api/unification/Pet Store API/configuration")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mockConfig)
                .exchange()
                .expectStatus().isForbidden();
    }

    // ──────────────────────────── updateStatus ────────────────────────────

    @Test
    void updateStatus_permitted_returns200() {
        UnifiedApiDocument updated = UnifiedApiDocument.builder()
                .id("api-1")
                .name("Pet Store API")
                .active(false)
                .build();

        given(userService.getCurrentUsername()).willReturn(Mono.just("admin"));
        given(apiGroupLinkService.canModifyApi("admin", "api-1")).willReturn(Mono.just(true));
        given(unifiedApiService.updateApiStatus(eq("api-1"), anyBoolean(), any(ServerHttpRequest.class)))
                .willReturn(Mono.just(updated));

        webClient.patch()
                .uri("/api/unification/api-1/status")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"active\": false}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("api-1");
    }

    @Test
    void updateStatus_forbidden_returns403() {
        given(userService.getCurrentUsername()).willReturn(Mono.just("user"));
        given(apiGroupLinkService.canModifyApi("user", "api-1")).willReturn(Mono.just(false));

        webClient.patch()
                .uri("/api/unification/api-1/status")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"active\": true}")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void updateStatus_notFound_returns404() {
        given(userService.getCurrentUsername()).willReturn(Mono.just("admin"));
        given(apiGroupLinkService.canModifyApi("admin", "api-1")).willReturn(Mono.just(true));
        given(unifiedApiService.updateApiStatus(eq("api-1"), anyBoolean(), any(ServerHttpRequest.class)))
                .willReturn(Mono.empty());

        webClient.patch()
                .uri("/api/unification/api-1/status")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"active\": true}")
                .exchange()
                .expectStatus().isNotFound();
    }

    // ──────────────────────────── findAll ────────────────────────────

    @Test
    void findAll_returns200() {
        UnifiedApiDocument doc2 = UnifiedApiDocument.builder()
                .id("api-2")
                .name("Another API")
                .active(true)
                .build();

        given(userService.getCurrentUserId()).willReturn(Mono.just("user-1"));
        given(apiGroupLinkService.listVisibleApis("user-1")).willReturn(Flux.just(mockDoc, doc2));

        webClient.get()
                .uri("/api/unification")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(UnifiedApiDocument.class)
                .hasSize(2);
    }

    // ──────────────────────────── findById ────────────────────────────

    @Test
    void findById_found_returns200() {
        given(unifiedApiService.findById("api-1")).willReturn(Mono.just(mockDoc));

        webClient.get()
                .uri("/api/unification/api-1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("Pet Store API")
                .jsonPath("$.id").isEqualTo("api-1");
    }

    @Test
    void findById_notFound_returns404() {
        given(unifiedApiService.findById("missing")).willReturn(Mono.empty());

        webClient.get()
                .uri("/api/unification/missing")
                .exchange()
                .expectStatus().isNotFound();
    }

    // ──────────────────────────── findByName ────────────────────────────

    @Test
    void findByName_found_returns200() {
        given(unifiedApiService.findByName("Pet")).willReturn(Mono.just(mockDoc));

        webClient.get()
                .uri("/api/unification/by-name?name=Pet")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("Pet Store API");
    }

    // ──────────────────────────── deleteApi ────────────────────────────

    @Test
    void deleteApi_permitted_returns204() {
        given(userService.getCurrentUsername()).willReturn(Mono.just("admin"));
        given(apiGroupLinkService.canModifyApi("admin", "api-1")).willReturn(Mono.just(true));
        given(unifiedApiService.deleteApi(eq("api-1"), any(ServerHttpRequest.class))).willReturn(Mono.empty());

        webClient.delete()
                .uri("/api/unification/api-1")
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void deleteApi_forbidden_returns403() {
        given(userService.getCurrentUsername()).willReturn(Mono.just("user"));
        given(apiGroupLinkService.canModifyApi("user", "api-1")).willReturn(Mono.just(false));

        webClient.delete()
                .uri("/api/unification/api-1")
                .exchange()
                .expectStatus().isForbidden();
    }
}
