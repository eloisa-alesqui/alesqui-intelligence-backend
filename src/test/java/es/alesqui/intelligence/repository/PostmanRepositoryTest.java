package es.alesqui.intelligence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import es.alesqui.intelligence.config.EmbeddedMongoConfig;
import es.alesqui.intelligence.model.api_spec.postman.Auth;
import es.alesqui.intelligence.model.api_spec.postman.Body;
import es.alesqui.intelligence.model.api_spec.postman.Collection;
import es.alesqui.intelligence.model.api_spec.postman.Description;
import es.alesqui.intelligence.model.api_spec.postman.Event;
import es.alesqui.intelligence.model.api_spec.postman.FormParameter;
import es.alesqui.intelligence.model.api_spec.postman.GraphQLBody;
import es.alesqui.intelligence.model.api_spec.postman.Info;
import es.alesqui.intelligence.model.api_spec.postman.Item;
import es.alesqui.intelligence.model.api_spec.postman.PostmanDocument;
import es.alesqui.intelligence.model.api_spec.postman.Request;
import es.alesqui.intelligence.model.api_spec.postman.Script;
import es.alesqui.intelligence.model.api_spec.postman.Url;
import es.alesqui.intelligence.model.api_spec.postman.Variable;
import es.alesqui.intelligence.model.api_spec.postman.enums.RequestMethod;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Integration tests for PostmanRepository.
 * 
 * UPDATED: This test class now covers the new repository that manages PostmanDocument
 * objects (wrapper around Collection with metadata) instead of raw Collection objects.
 * 
 * Tests all reactive MongoDB operations for document management including:
 * - Basic CRUD operations for PostmanDocument
 * - Document metadata validation and persistence
 * - Collection wrapper functionality
 * - Reactive stream behavior with documents
 * - Document-specific queries and operations
 * 
 * The repository now provides simpler operations focused on document management,
 * with complex queries handled at the service layer.
 */
@SpringBootTest(classes = EmbeddedMongoConfig.class)
@ActiveProfiles("test")
@DisplayName("PostmanRepository Tests - Document Management")
@Disabled
class PostmanRepositoryTest {

  @Autowired
  private PostmanRepository repository;

  private PostmanDocument testDocument1;
  private PostmanDocument testDocument2;
  private PostmanDocument testDocument3;

  @BeforeEach
  void setUp() {
      // Clean database before each test
      repository.deleteAll().block(Duration.ofSeconds(5));

      // Create test documents
      testDocument1 = createTestDocument1();
      testDocument2 = createTestDocument2();
      testDocument3 = createTestDocument3();
  }

  @AfterEach
  void tearDown() {
      // Clean database after each test
      repository.deleteAll().block(Duration.ofSeconds(5));
  }

  // ========================================
  // BASIC CRUD OPERATIONS TESTS
  // ========================================

  @Nested
  @DisplayName("Basic CRUD Operations - Document Management")
  class BasicCrudOperationsTest {

      @Test
      @DisplayName("Should save and retrieve collection document")
      void shouldSaveAndRetrieveCollectionDocument() {
          // When
          Mono<PostmanDocument> savedDocument = repository.save(testDocument1);

          // Then
          StepVerifier.create(savedDocument)
              .assertNext(document -> {
                  assertThat(document.getId()).isNotNull();
                  assertThat(document.getName()).isEqualTo("Test API Collection Document");
                  assertThat(document.getTeam()).isEqualTo("Backend Team");
                  assertThat(document.getCreatedBy()).isEqualTo("john.doe");
                  assertThat(document.getCollection()).isNotNull();
                  assertThat(document.getCollection().getInfo().getName()).isEqualTo("Test API Collection");
                  assertThat(document.getCreatedAt()).isNotNull();
                  assertThat(document.getUpdatedAt()).isNotNull();
              })
              .verifyComplete();
      }

      @Test
      @DisplayName("Should find collection document by ID")
      void shouldFindCollectionDocumentById() {
          // Given
          PostmanDocument saved = repository.save(testDocument1).block();

          // When
          Mono<PostmanDocument> found = repository.findById(saved.getId());

          // Then
          StepVerifier.create(found)
              .assertNext(document -> {
                  assertThat(document.getId()).isEqualTo(saved.getId());
                  assertThat(document.getName()).isEqualTo("Test API Collection Document");
                  assertThat(document.getTeam()).isEqualTo("Backend Team");
                  assertThat(document.getCreatedBy()).isEqualTo("john.doe");
                  assertThat(document.getCollection().getInfo().getName()).isEqualTo("Test API Collection");
              })
              .verifyComplete();
      }

      @Test
      @DisplayName("Should return empty when document not found")
      void shouldReturnEmptyWhenDocumentNotFound() {
          // When
          Mono<PostmanDocument> found = repository.findById("nonexistent-id");

          // Then
          StepVerifier.create(found)
              .verifyComplete();
      }

      @Test
      @DisplayName("Should find all collection documents")
      void shouldFindAllCollectionDocuments() {
          // Given
          repository.saveAll(Arrays.asList(testDocument1, testDocument2, testDocument3))
              .blockLast(Duration.ofSeconds(5));

          // When
          Flux<PostmanDocument> allDocuments = repository.findAll();

          // Then
          StepVerifier.create(allDocuments)
              .expectNextCount(3)
              .verifyComplete();
      }

      @Test
      @DisplayName("Should delete collection document by ID")
      void shouldDeleteCollectionDocumentById() {
          // Given
          PostmanDocument saved = repository.save(testDocument1).block();

          // When
          Mono<Void> deleteResult = repository.deleteById(saved.getId());

          // Then
          StepVerifier.create(deleteResult)
              .verifyComplete();

          StepVerifier.create(repository.findById(saved.getId()))
              .verifyComplete();
      }

      @Test
      @DisplayName("Should count collection documents")
      void shouldCountCollectionDocuments() {
          // Given
          repository.saveAll(Arrays.asList(testDocument1, testDocument2))
              .blockLast(Duration.ofSeconds(5));

          // When
          Mono<Long> count = repository.count();

          // Then
          StepVerifier.create(count)
              .expectNext(2L)
              .verifyComplete();
      }

      @Test
      @DisplayName("Should check if document exists by ID")
      void shouldCheckIfDocumentExistsById() {
          // Given
          PostmanDocument saved = repository.save(testDocument1).block();

          // When
          Mono<Boolean> exists = repository.existsById(saved.getId());
          Mono<Boolean> notExists = repository.existsById("nonexistent-id");

          // Then
          StepVerifier.create(exists)
              .expectNext(true)
              .verifyComplete();

          StepVerifier.create(notExists)
              .expectNext(false)
              .verifyComplete();
      }
  }

  // ========================================
  // DOCUMENT METADATA TESTS
  // ========================================

  @Nested
  @DisplayName("Document Metadata Management")
  class DocumentMetadataTest {

      @Test
      @DisplayName("Should persist and retrieve document metadata correctly")
      void shouldPersistAndRetrieveDocumentMetadata() {
          // Given
          PostmanDocument document = testDocument1;
          document.setDescription("Updated description for testing");
          document.setActive(false);

          // When
          PostmanDocument saved = repository.save(document).block();
          PostmanDocument retrieved = repository.findById(saved.getId()).block();

          // Then
          assertThat(retrieved).isNotNull();
          assertThat(retrieved.getName()).isEqualTo("Test API Collection Document");
          assertThat(retrieved.getDescription()).isEqualTo("Updated description for testing");
          assertThat(retrieved.getTeam()).isEqualTo("Backend Team");
          assertThat(retrieved.getCreatedBy()).isEqualTo("john.doe");
          assertThat(retrieved.isActive()).isFalse();
          assertThat(retrieved.getCreatedAt()).isNotNull();
          assertThat(retrieved.getUpdatedAt()).isNotNull();
      }

      @Test
      @DisplayName("Should handle documents with minimal metadata")
      void shouldHandleDocumentsWithMinimalMetadata() {
          // Given
          PostmanDocument minimalDocument = PostmanDocument.builder()
              .name("Minimal Document")
              .collection(createTestCollection1())
              .team("Test Team")
              .createdBy("test.user")
              .active(true)
              .createdAt(LocalDateTime.now())
              .updatedAt(LocalDateTime.now())
              .build();

          // When
          PostmanDocument saved = repository.save(minimalDocument).block();

          // Then
          assertThat(saved).isNotNull();
          assertThat(saved.getId()).isNotNull();
          assertThat(saved.getName()).isEqualTo("Minimal Document");
          assertThat(saved.getDescription()).isNull();
          assertThat(saved.getCollection()).isNotNull();
      }

      @Test
      @DisplayName("Should handle documents with null optional fields")
      void shouldHandleDocumentsWithNullOptionalFields() {
          // Given
          PostmanDocument document = PostmanDocument.builder()
              .name("Document with Nulls")
              .description(null) // Optional field
              .collection(createTestCollection1())
              .team("Test Team")
              .createdBy("test.user")
              .active(true)
              .createdAt(LocalDateTime.now())
              .updatedAt(LocalDateTime.now())
              .build();

          // When
          Mono<PostmanDocument> saveResult = repository.save(document);

          // Then
          StepVerifier.create(saveResult)
              .assertNext(saved -> {
                  assertThat(saved.getId()).isNotNull();
                  assertThat(saved.getName()).isEqualTo("Document with Nulls");
                  assertThat(saved.getDescription()).isNull();
                  assertThat(saved.getCollection()).isNotNull();
              })
              .verifyComplete();
      }
  }

  // ========================================
  // COLLECTION WRAPPER TESTS
  // ========================================

  @Nested
  @DisplayName("Collection Wrapper Functionality")
  class CollectionWrapperTest {

      @Test
      @DisplayName("Should preserve collection structure within document")
      void shouldPreserveCollectionStructureWithinDocument() {
          // Given
          repository.save(testDocument1).block();

          // When
          PostmanDocument retrieved = repository.findAll().blockFirst();

          // Then
          assertThat(retrieved).isNotNull();
          assertThat(retrieved.getCollection()).isNotNull();
          
          Collection collection = retrieved.getCollection();
          assertThat(collection.getInfo()).isNotNull();
          assertThat(collection.getInfo().getName()).isEqualTo("Test API Collection");
          assertThat(collection.getItem()).hasSize(2);
          assertThat(collection.getVariable()).hasSize(1);
          
          // Verify specific items
          Item getUsersItem = collection.getItem().stream()
              .filter(item -> "Get Users".equals(item.getName()))
              .findFirst()
              .orElseThrow(() -> new AssertionError("Get Users item not found"));
          
          assertThat(getUsersItem.getRequest().getMethod()).isEqualTo(RequestMethod.GET);
          assertThat(getUsersItem.getRequest().getUrlAsObject().getRaw()).contains("api.example.com/users");
      }

      @Test
      @DisplayName("Should handle complex collection structures")
      void shouldHandleComplexCollectionStructures() {
          // Given - Document with GraphQL collection
          repository.save(testDocument3).block();

          // When
          PostmanDocument retrieved = repository.findAll()
              .filter(doc -> "GraphQL API Collection Document".equals(doc.getName()))
              .blockFirst();

          // Then
          assertThat(retrieved).isNotNull();
          assertThat(retrieved.getCollection()).isNotNull();
          
          Collection collection = retrieved.getCollection();
          assertThat(collection.getInfo().getName()).isEqualTo("GraphQL API Collection");
          assertThat(collection.getItem()).hasSize(1);
          
          Item graphqlItem = collection.getItem().get(0);
          assertThat(graphqlItem.getName()).isEqualTo("Get User Query");
          assertThat(graphqlItem.getRequest().getBody()).isNotNull();
          assertThat(graphqlItem.getRequest().getBody().getMode()).isEqualTo("graphql");
          assertThat(graphqlItem.getRequest().getBody().getGraphql()).isNotNull();
      }

      @Test
      @DisplayName("Should handle collections with authentication")
      void shouldHandleCollectionsWithAuthentication() {
          // Given
          repository.save(testDocument1).block();

          // When
          PostmanDocument retrieved = repository.findAll().blockFirst();

          // Then
          assertThat(retrieved).isNotNull();
          Collection collection = retrieved.getCollection();
          
          // Find item with authentication
          Item createUserItem = collection.getItem().stream()
              .filter(item -> "Create User".equals(item.getName()))
              .findFirst()
              .orElseThrow(() -> new AssertionError("Create User item not found"));
          
          assertThat(createUserItem.getRequest().getAuth()).isNotNull();
          assertThat(createUserItem.getRequest().getAuth().getType()).isEqualTo("bearer");
      }
  }

  // ========================================
  // REACTIVE STREAM BEHAVIOR TESTS
  // ========================================

  @Nested
  @DisplayName("Reactive Stream Behavior")
  class ReactiveStreamBehaviorTest {

      @Test
      @DisplayName("Should handle backpressure correctly")
      void shouldHandleBackpressureCorrectly() {
          // Given
          repository.saveAll(Arrays.asList(testDocument1, testDocument2, testDocument3))
              .blockLast(Duration.ofSeconds(5));

          // When
          Flux<PostmanDocument> result = repository.findAll();

          // Then
          StepVerifier.create(result, 1) // Request only 1 item initially
              .expectNextCount(1)
              .thenRequest(2) // Request remaining items
              .expectNextCount(2)
              .verifyComplete();
      }

      @Test
      @DisplayName("Should handle cancellation gracefully")
      void shouldHandleCancellationGracefully() {
          // Given
          repository.saveAll(Arrays.asList(testDocument1, testDocument2, testDocument3))
              .blockLast(Duration.ofSeconds(5));

          // When
          Flux<PostmanDocument> result = repository.findAll();

          // Then
          StepVerifier.create(result)
              .expectSubscription()
              .expectNextCount(1)
              .thenCancel()
              .verify();
      }

      @Test
      @DisplayName("Should handle concurrent operations")
      void shouldHandleConcurrentOperations() {
          // Given
          Mono<PostmanDocument> save1 = repository.save(testDocument1);
          Mono<PostmanDocument> save2 = repository.save(testDocument2);
          Mono<PostmanDocument> save3 = repository.save(testDocument3);

          // When
          Flux<PostmanDocument> concurrentSaves = Flux.merge(save1, save2, save3);

          // Then
          StepVerifier.create(concurrentSaves)
              .expectNextCount(3)
              .verifyComplete();

          // Verify all documents were saved
          StepVerifier.create(repository.count())
              .expectNext(3L)
              .verifyComplete();
      }
  }

  // ========================================
  // ERROR HANDLING TESTS
  // ========================================

  @Nested
  @DisplayName("Error Handling and Edge Cases")
  class ErrorHandlingTest {

      @Test
      @DisplayName("Should handle save operation with duplicate IDs")
      void shouldHandleSaveOperationWithDuplicateIds() {
          // Given
          PostmanDocument saved = repository.save(testDocument1).block();
          
          // Create another document with same ID
          PostmanDocument duplicate = PostmanDocument.builder()
              .id(saved.getId()) // Same ID
              .name("Duplicate Document")
              .collection(createTestCollection2())
              .team("Another Team")
              .createdBy("another.user")
              .active(true)
              .createdAt(LocalDateTime.now())
              .updatedAt(LocalDateTime.now())
              .build();

          // When - Save should update the existing document
          PostmanDocument updated = repository.save(duplicate).block();

          // Then
          assertThat(updated.getId()).isEqualTo(saved.getId());
          assertThat(updated.getName()).isEqualTo("Duplicate Document");
          
          // Verify only one document exists
          StepVerifier.create(repository.count())
              .expectNext(1L)
              .verifyComplete();
      }

      @Test
      @DisplayName("Should handle empty flux operations")
      void shouldHandleEmptyFluxOperations() {
          // When - No documents in database
          Flux<PostmanDocument> emptyResult = repository.findAll();

          // Then
          StepVerifier.create(emptyResult)
              .verifyComplete();
      }

      @Test
      @DisplayName("Should handle delete operations on non-existent documents")
      void shouldHandleDeleteOperationsOnNonExistentDocuments() {
          // When
          Mono<Void> deleteResult = repository.deleteById("non-existent-id");

          // Then - Should complete without error
          StepVerifier.create(deleteResult)
              .verifyComplete();
      }
  }

  // ========================================
  // TEST DATA CREATION METHODS
  // ========================================

  private PostmanDocument createTestDocument1() {
      return PostmanDocument.builder()
          .name("Test API Collection Document")
          .description("A comprehensive REST API collection document for testing")
          .collection(createTestCollection1())
          .team("Backend Team")
          .createdBy("john.doe")
          .active(true)
          .createdAt(LocalDateTime.now())
          .updatedAt(LocalDateTime.now())
          .build();
  }

  private PostmanDocument createTestDocument2() {
      return PostmanDocument.builder()
          .name("File Upload Collection Document")
          .description("Collection document for testing file uploads")
          .collection(createTestCollection2())
          .team("Frontend Team")
          .createdBy("jane.smith")
          .active(true)
          .createdAt(LocalDateTime.now())
          .updatedAt(LocalDateTime.now())
          .build();
  }

  private PostmanDocument createTestDocument3() {
      return PostmanDocument.builder()
          .name("GraphQL API Collection Document")
          .description("GraphQL queries and mutations document")
          .collection(createTestCollection3())
          .team("Full Stack Team")
          .createdBy("bob.johnson")
          .active(true)
          .createdAt(LocalDateTime.now())
          .updatedAt(LocalDateTime.now())
          .build();
  }

  private Collection createTestCollection1() {
      Info info = Info.builder()
          .name("Test API Collection")
          .description(Description.builder()
              .content("A comprehensive REST API collection for testing")
              .type("text/markdown")
              .build())
          .schema("https://schema.getpostman.com/json/collection/v2.1.0/collection.json")
          .build();

      Item getUsersItem = Item.builder()
          .name("Get Users")
          .request(Request.builder()
              .method(RequestMethod.GET)
              .url(Url.builder()
                  .raw("https://api.example.com/users")
                  .build())
              .build())
          .build();

      Item createUserItem = Item.builder()
          .name("Create User")
          .request(Request.builder()
              .method(RequestMethod.POST)
              .url(Url.builder()
                  .raw("https://api.example.com/users")
                  .build())
              .auth(Auth.builder()
                  .type("bearer")
                  .build())
              .build())
          .event(Collections.singletonList(
              Event.builder()
                  .listen("test")
                  .script(Script.builder()
                      .type("text/javascript")
                      .exec(Arrays.asList(
                          "pm.test('Status code is 201', function () {",
                          "    pm.response.to.have.status(201);",
                          "});"
                      ))
                      .build())
                  .build()))
          .build();

      Variable baseUrlVar = Variable.builder()
          .key("baseUrl")
          .value("https://api.example.com")
          .type("string")
          .build();

      return Collection.builder()
          .info(info)
          .item(Arrays.asList(getUsersItem, createUserItem))
          .variable(Collections.singletonList(baseUrlVar))
          .build();
  }

  private Collection createTestCollection2() {
      Info info = Info.builder()
          .name("File Upload Collection")
          .description(Description.builder()
              .content("Collection for testing file uploads")
              .type("text/plain")
              .build())
          .schema("https://schema.getpostman.com/json/collection/v2.1.0/collection.json")
          .build();

      Item uploadItem = Item.builder()
          .name("Upload File")
          .request(Request.builder()
              .method(RequestMethod.POST)
              .url(Url.builder()
                  .raw("https://upload.example.com/files")
                  .build())
              .body(Body.builder()
                  .mode("formdata")
                  .formdata(Collections.singletonList(
                      FormParameter.builder()
                          .key("file")
                          .type("file")
                          .src("test-file.txt")
                          .build()))
                  .build())
              .build())
          .build();

      return Collection.builder()
          .info(info)
          .item(Collections.singletonList(uploadItem))
          .build();
  }

  private Collection createTestCollection3() {
      Info info = Info.builder()
          .name("GraphQL API Collection")
          .description(Description.builder()
              .content("GraphQL queries and mutations")
              .type("text/markdown")
              .build())
          .schema("https://schema.getpostman.com/json/collection/v2.1.0/collection.json")
          .build();

      Item graphqlItem = Item.builder()
          .name("Get User Query")
          .request(Request.builder()
              .method(RequestMethod.GET)
              .url(Url.builder()
                  .raw("https://graphql.example.com/query")
                  .build())
              .body(Body.builder()
                  .mode("graphql")
                  .graphql(GraphQLBody.builder()
                      .query("query GetUser($id: ID!) { user(id: $id) { id name email } }")
                      .variables("{\"id\": \"123\"}")
                      .build())
                  .build())
              .build())
          .build();

      return Collection.builder()
          .info(info)
          .item(Collections.singletonList(graphqlItem))
          .build();
  }
}