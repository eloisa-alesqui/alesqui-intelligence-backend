package es.alesqui.postmangpt.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.alesqui.postmangpt.config.properties.PostmanCollectionProperties;
import es.alesqui.postmangpt.dto.CollectionInfo;
import es.alesqui.postmangpt.dto.EndpointInfo;
import es.alesqui.postmangpt.helper.PostmanEndpointExtractor;
import es.alesqui.postmangpt.model.Collection;
import es.alesqui.postmangpt.model.Info;
import es.alesqui.postmangpt.model.Item;
import es.alesqui.postmangpt.model.Description;
import es.alesqui.postmangpt.repository.PostmanCollectionRepository;
import es.alesqui.postmangpt.util.SafeLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for PostmanCollectionService.
 * 
 * This test class provides thorough coverage of all service methods including:
 * - Basic CRUD operations (create, read, update, delete) - File import
 * functionality with validation - JSON string import with error handling -
 * Endpoint extraction and statistics generation - Error scenarios and edge
 * cases - Reactive stream behavior verification
 * 
 * Test Structure: - Nested test classes for logical grouping - Comprehensive
 * setup with mock dependencies - Happy path and error scenario coverage -
 * Reactive stream testing with StepVerifier - Edge case validation and boundary
 * testing
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PostmanCollectionService Tests")
class PostmanCollectionServiceTest {

	@Mock
	private PostmanCollectionRepository repository;

	@Mock
	private ObjectMapper objectMapper;

	@Mock
	private PostmanEndpointExtractor endpointExtractor;

	@Mock
	private SafeLogger safeLogger;

	@Mock
	private PostmanCollectionProperties properties;

	@Mock
	private MultipartFile multipartFile;

	private PostmanCollectionService service;

	// Test data constants
	private static final String COLLECTION_ID = "test-collection-id";
	private static final String COLLECTION_NAME = "Test Collection";
	private static final String JSON_CONTENT = "{\"info\":{\"name\":\"Test Collection\"},\"item\":[]}";
	private static final String FILENAME = "test-collection.json";
	private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
	private static final long MAX_JSON_SIZE = 10 * 1024 * 1024; // 10MB

	@BeforeEach
	void setUp() {

		// Initialize service with mocked dependencies
		service = new PostmanCollectionService(repository, objectMapper, endpointExtractor, safeLogger, properties);

	}

	/**
	 * Helper method to create a test collection with basic structure.
	 */
	private Collection createTestCollection() {
		return createTestCollection(COLLECTION_ID, COLLECTION_NAME);
	}

	/**
	 * Helper method to create a test collection with custom ID and name.
	 */
	private Collection createTestCollection(String id, String name) {
		Collection collection = new Collection();
		collection.setId(id);

		Info info = new Info();
		info.setName(name);

		Description description = new Description();
		description.setContent("Test collection description");
		info.setDescription(description);

		collection.setInfo(info);

		// Add test items to make collection valid
		Item item = new Item();
		item.setName("Test Request");
		collection.setItem(Arrays.asList(item));

		return collection;
	}

	/**
	 * Helper method to create a collection without ID (for new collections).
	 */
	private Collection createNewCollection() {
		return createTestCollection(null, COLLECTION_NAME);
	}

	@Nested
	@DisplayName("Basic CRUD Operations")
	class CrudOperationsTest {

		@Test
		@DisplayName("Should retrieve all collections successfully")
		void shouldGetAllCollections() {
			// Given
			Collection collection1 = createTestCollection("id1", "Collection 1");
			Collection collection2 = createTestCollection("id2", "Collection 2");
			Flux<Collection> expectedFlux = Flux.just(collection1, collection2);

			when(repository.findAll()).thenReturn(expectedFlux);
			when(safeLogger.logOperationStartReactive(anyString(), any())).thenReturn(Mono.empty());

			// When
			Flux<Collection> result = service.getAllCollections();

			// Then
			StepVerifier.create(result).expectNext(collection1).expectNext(collection2).verifyComplete();

			verify(repository).findAll();
			verify(safeLogger).logOperationStartReactive(eq("Retrieve all collections"), isNull());
		}

		@Test
        @DisplayName("Should handle empty collection list")
        void shouldHandleEmptyCollectionList() {
            // Given
            when(repository.findAll()).thenReturn(Flux.empty());
            when(safeLogger.logOperationStartReactive(anyString(), any())).thenReturn(Mono.empty());

            // When
            Flux<Collection> result = service.getAllCollections();

            // Then
            StepVerifier.create(result)
                .verifyComplete();
        }

		@Test
		@DisplayName("Should retrieve collection by ID successfully")
		void shouldGetCollectionById() {
			// Given
			Collection expectedCollection = createTestCollection();
			when(repository.findById(COLLECTION_ID)).thenReturn(Mono.just(expectedCollection));
			when(safeLogger.logOperationStartReactive(anyString(), any())).thenReturn(Mono.empty());

			// When
			Mono<Collection> result = service.getCollectionById(COLLECTION_ID);

			// Then
			StepVerifier.create(result).expectNext(expectedCollection).verifyComplete();

			verify(repository).findById(COLLECTION_ID);
		}

		@Test
        @DisplayName("Should return empty when collection not found by ID")
        void shouldReturnEmptyWhenCollectionNotFoundById() {
            // Given
            when(repository.findById(COLLECTION_ID)).thenReturn(Mono.empty());
            when(safeLogger.logOperationStartReactive(anyString(), any())).thenReturn(Mono.empty());

            // When
            Mono<Collection> result = service.getCollectionById(COLLECTION_ID);

            // Then
            StepVerifier.create(result)
                .verifyComplete();
        }

		@Test
		@DisplayName("Should retrieve collection by name successfully")
		void shouldGetCollectionByName() {
			// Given
			Collection expectedCollection = createTestCollection();
			when(repository.findByInfoName(COLLECTION_NAME)).thenReturn(Mono.just(expectedCollection));
			when(safeLogger.logOperationStartReactive(anyString(), any())).thenReturn(Mono.empty());

			// When
			Mono<Collection> result = service.getCollectionByName(COLLECTION_NAME);

			// Then
			StepVerifier.create(result).expectNext(expectedCollection).verifyComplete();

			verify(repository).findByInfoName(COLLECTION_NAME);
		}

		@Test
		@DisplayName("Should save new collection successfully")
		void shouldSaveNewCollection() {
			// Given
			Collection newCollection = createNewCollection();
			Collection savedCollection = createTestCollection();

			when(repository.save(newCollection)).thenReturn(Mono.just(savedCollection));
			when(safeLogger.logOperationStartReactive(anyString(), any())).thenReturn(Mono.empty());

			// When
			Mono<Collection> result = service.saveCollection(newCollection);

			// Then
			StepVerifier.create(result).expectNext(savedCollection).verifyComplete();

			verify(repository).save(newCollection);
		}

		@Test
		@DisplayName("Should reject null collection")
		void shouldRejectNullCollection() {
			// When
			Mono<Collection> result = service.saveCollection(null);

			// Then
			StepVerifier.create(result).expectErrorSatisfies(throwable -> {
				assertThat(throwable).isInstanceOf(IllegalArgumentException.class);
				assertThat(throwable.getMessage()).isEqualTo("Collection cannot be null");
			}).verify();

			verify(repository, never()).save(any());
		}

		@Test
		@DisplayName("Should reject collection with null info")
		void shouldRejectCollectionWithNullInfo() {
			// Given
			Collection collection = new Collection();
			collection.setInfo(null);

			// When
			Mono<Collection> result = service.saveCollection(collection);

			// Then
			StepVerifier.create(result).expectErrorSatisfies(throwable -> {
				assertThat(throwable).isInstanceOf(IllegalArgumentException.class);
				assertThat(throwable.getMessage()).isEqualTo("Collection info cannot be null");
			}).verify();
		}

		@Test
        @DisplayName("Should delete collection successfully")
        void shouldDeleteCollection() {
            // Given
            when(repository.deleteById(COLLECTION_ID)).thenReturn(Mono.empty());
            when(safeLogger.logOperationStartReactive(anyString(), any())).thenReturn(Mono.empty());

            // When
            Mono<Void> result = service.deleteCollection(COLLECTION_ID);

            // Then
            StepVerifier.create(result)
                .verifyComplete();
                
            verify(repository).deleteById(COLLECTION_ID);
        }

		@Test
		@DisplayName("Should handle database errors during save")
		void shouldHandleDatabaseErrorsDuringSave() {
			// Given
			Collection collection = createNewCollection();
			DataAccessException dbError = new DataIntegrityViolationException("Database constraint violation");

			when(repository.save(collection)).thenReturn(Mono.error(dbError));
			when(safeLogger.logOperationStartReactive(anyString(), any())).thenReturn(Mono.empty());

			// When
			Mono<Collection> result = service.saveCollection(collection);

			// Then
			StepVerifier.create(result).expectError(DataAccessException.class).verify();
		}
	}

	@Nested
	@DisplayName("File Import Operations")
	class FileImportTest {

		@Test
		@DisplayName("Should import collection from file successfully")
		void shouldImportCollectionFromFile() throws IOException {
			// Given
			Collection parsedCollection = createNewCollection();
			Collection savedCollection = createTestCollection();
			InputStream mockInputStream = mock(InputStream.class);

			when(safeLogger.logOperationStartReactive(anyString(), any())).thenReturn(Mono.empty());

			when(multipartFile.getInputStream()).thenReturn(mockInputStream);
			when(multipartFile.getOriginalFilename()).thenReturn(FILENAME);
			when(objectMapper.readValue(any(InputStream.class), eq(Collection.class))).thenReturn(parsedCollection);
			when(repository.findByInfoName(COLLECTION_NAME)).thenReturn(Mono.empty());
			when(repository.save(parsedCollection)).thenReturn(Mono.just(savedCollection));

			// When
			Mono<Collection> result = service.importCollectionFromFile(multipartFile);

			// Then
			StepVerifier.create(result).expectNext(savedCollection).verifyComplete();

			verify(objectMapper).readValue(any(InputStream.class), eq(Collection.class));
			verify(repository).findByInfoName(COLLECTION_NAME);
			verify(repository).save(parsedCollection);
		}

		@Test
		@DisplayName("Should reject null file")
		void shouldRejectNullFile() {
			// When
			Mono<Collection> result = service.importCollectionFromFile(null);

			// Then
			StepVerifier.create(result).expectErrorSatisfies(throwable -> {
				assertThat(throwable).isInstanceOf(IllegalArgumentException.class);
				assertThat(throwable.getMessage()).isEqualTo("File cannot be null");
			}).verify();
		}

		@Test
		@DisplayName("Should reject empty file")
		void shouldRejectEmptyFile() {
			// Given
			MockMultipartFile emptyFile = new MockMultipartFile("file", "test.json", "application/json", new byte[0]);

			// When
			Mono<Collection> result = service.importCollectionFromFile(emptyFile);

			// Then
			StepVerifier.create(result).expectErrorSatisfies(throwable -> {
				assertThat(throwable).isInstanceOf(IllegalArgumentException.class);
				assertThat(throwable.getMessage()).isEqualTo("File cannot be empty");
			}).verify();
		}

		@Test
		@DisplayName("Should reject non-JSON file")
		void shouldRejectNonJsonFile() {
			// Given
			MockMultipartFile nonJsonFile = new MockMultipartFile("file", "test.txt", "text/plain",
					"content".getBytes());

			// When
			Mono<Collection> result = service.importCollectionFromFile(nonJsonFile);

			// Then
			StepVerifier.create(result).expectErrorSatisfies(throwable -> {
				assertThat(throwable).isInstanceOf(IllegalArgumentException.class);
				assertThat(throwable.getMessage()).isEqualTo("File must be a valid .JSON");
			}).verify();
		}

		@Test
		@DisplayName("Should reject oversized file")
		void shouldRejectOversizedFile() {
			// Given - Configure the mock to simulate an oversized file
			long oversizedFileSize = MAX_FILE_SIZE + 1; // Exceed the maximum allowed size

			when(multipartFile.isEmpty()).thenReturn(false);
			when(multipartFile.getOriginalFilename()).thenReturn(FILENAME);
			when(multipartFile.getSize()).thenReturn(oversizedFileSize);

			// Configure properties mock to return the max file size
			when(properties.getMaxFileSize()).thenReturn(MAX_FILE_SIZE);

			// When
			Mono<Collection> result = service.importCollectionFromFile(multipartFile);

			// Then
			StepVerifier.create(result).expectErrorSatisfies(throwable -> {
				assertThat(throwable).isInstanceOf(IllegalArgumentException.class);
				assertThat(throwable.getMessage()).contains("File too large");
			}).verify();

			// Verify that the service checked the file size
			verify(multipartFile).getSize();
			verify(properties).getMaxFileSize();
		}

		@Test
		@DisplayName("Should reject duplicate collection name")
		void shouldRejectDuplicateCollectionName() throws IOException {
			// Given - Setup file parsing to succeed
			Collection parsedCollection = createNewCollection();
			Collection existingCollection = createTestCollection();
			InputStream mockInputStream = mock(InputStream.class);

			// Mock file validation to pass
			when(multipartFile.isEmpty()).thenReturn(false);
			when(multipartFile.getOriginalFilename()).thenReturn(FILENAME);
			when(multipartFile.getSize()).thenReturn(1000L);
			when(multipartFile.getInputStream()).thenReturn(mockInputStream);
			when(properties.getMaxFileSize()).thenReturn(MAX_FILE_SIZE);

			// Mock JSON parsing to succeed
			when(objectMapper.readValue(any(InputStream.class), eq(Collection.class))).thenReturn(parsedCollection);

			// Mock logger calls to return empty Mono (important for reactive chain)
			when(safeLogger.logOperationStartReactive(anyString(), any())).thenReturn(Mono.empty());

			// Key: Mock the duplicate check to find existing collection
			when(repository.findByInfoName(COLLECTION_NAME)).thenReturn(Mono.just(existingCollection)); // This
																										// simulates
																										// duplicate
																										// found

			// When
			Mono<Collection> result = service.importCollectionFromFile(multipartFile);

			// Then
			StepVerifier.create(result).expectErrorSatisfies(throwable -> {
				assertThat(throwable).isInstanceOf(IllegalArgumentException.class);
				assertThat(throwable.getMessage()).isEqualTo("Collection with this name already exists");
			}).verify();

			// Verify that save was never called due to duplicate
			verify(repository, never()).save(any());
			// Verify that duplicate check was performed
			verify(repository).findByInfoName(COLLECTION_NAME);
		}

		@Test
		@DisplayName("Should handle JSON parsing errors")
		void shouldHandleJsonParsingErrors() throws IOException {
			// Given
			JsonProcessingException jsonError = mock(JsonProcessingException.class);
			InputStream mockInputStream = mock(InputStream.class);

			when(multipartFile.getInputStream()).thenReturn(mockInputStream);
			when(multipartFile.getOriginalFilename()).thenReturn(FILENAME);
			when(objectMapper.readValue(any(InputStream.class), eq(Collection.class))).thenThrow(jsonError);

			// When
			Mono<Collection> result = service.importCollectionFromFile(multipartFile);

			// Then
			StepVerifier.create(result).expectError(JsonProcessingException.class).verify();
		}

		@Test
		@DisplayName("Should reject collection without items")
		void shouldRejectCollectionWithoutItems() throws IOException {
			// Given
			Collection invalidCollection = createTestCollection();
			invalidCollection.setItem(null);
			InputStream mockInputStream = mock(InputStream.class);

			when(multipartFile.getInputStream()).thenReturn(mockInputStream);
			when(multipartFile.getOriginalFilename()).thenReturn(FILENAME);
			when(objectMapper.readValue(any(InputStream.class), eq(Collection.class))).thenReturn(invalidCollection);

			// When
			Mono<Collection> result = service.importCollectionFromFile(multipartFile);

			// Then
			StepVerifier.create(result).expectErrorSatisfies(throwable -> {
				assertThat(throwable).isInstanceOf(IllegalArgumentException.class);
				assertThat(throwable.getMessage()).isEqualTo("Collection must have at least one item/request");
			}).verify();
		}
	}

	@Nested
	@DisplayName("JSON Import Operations")
	class JsonImportTest {

		@Test
		@DisplayName("Should import collection from JSON string successfully")
		void shouldImportCollectionFromJson() throws IOException {
			// Given
			Collection parsedCollection = createNewCollection();
			Collection savedCollection = createTestCollection();

			when(safeLogger.logOperationStartReactive(anyString(), any())).thenReturn(Mono.empty());

			when(properties.getMaxJsonSize()).thenReturn(MAX_JSON_SIZE);
			when(objectMapper.readValue(JSON_CONTENT, Collection.class)).thenReturn(parsedCollection);
			when(repository.findByInfoName(COLLECTION_NAME)).thenReturn(Mono.empty());
			when(repository.save(parsedCollection)).thenReturn(Mono.just(savedCollection));

			// When
			Mono<Collection> result = service.importCollectionFromJson(JSON_CONTENT);

			// Then
			StepVerifier.create(result).expectNext(savedCollection).verifyComplete();

			verify(objectMapper).readValue(JSON_CONTENT, Collection.class);
		}

		@Test
		@DisplayName("Should reject null JSON content")
		void shouldRejectNullJsonContent() {
			// When
			Mono<Collection> result = service.importCollectionFromJson(null);

			// Then
			StepVerifier.create(result).expectErrorSatisfies(throwable -> {
				assertThat(throwable).isInstanceOf(IllegalArgumentException.class);
				assertThat(throwable.getMessage()).isEqualTo("JSON content cannot be null");
			}).verify();
		}

		@Test
		@DisplayName("Should reject empty JSON content")
		void shouldRejectEmptyJsonContent() {
			// When
			Mono<Collection> result = service.importCollectionFromJson("");

			// Then
			StepVerifier.create(result).expectErrorSatisfies(throwable -> {
				assertThat(throwable).isInstanceOf(IllegalArgumentException.class);
				assertThat(throwable.getMessage()).isEqualTo("JSON content cannot be empty");
			}).verify();
		}

		@Test
		@DisplayName("Should reject whitespace-only JSON content")
		void shouldRejectWhitespaceOnlyJsonContent() {
			// When
			Mono<Collection> result = service.importCollectionFromJson("   \n\t   ");

			// Then
			StepVerifier.create(result).expectErrorSatisfies(throwable -> {
				assertThat(throwable).isInstanceOf(IllegalArgumentException.class);
				assertThat(throwable.getMessage()).isEqualTo("JSON content cannot be empty");
			}).verify();
		}

		@Test
		@DisplayName("Should reject oversized JSON content")
		void shouldRejectOversizedJsonContent() {
			// Given
			String oversizedJson = "x".repeat((int) MAX_JSON_SIZE + 1);

			// When
			Mono<Collection> result = service.importCollectionFromJson(oversizedJson);

			// Then
			StepVerifier.create(result).expectErrorSatisfies(throwable -> {
				assertThat(throwable).isInstanceOf(IllegalArgumentException.class);
				assertThat(throwable.getMessage()).contains("JSON content too large");
			}).verify();
		}

	}

	@Nested
	@DisplayName("Endpoint Extraction")
	class EndpointExtractionTest {

		@Test
		@DisplayName("Should extract endpoints successfully")
		void shouldExtractEndpoints() {
			// Given
			Collection collection = createTestCollection();
			List<EndpointInfo> expectedEndpoints = Arrays.asList(
					EndpointInfo.builder().name("GET /users").method("GET").url("/users").build(),
					EndpointInfo.builder().name("POST /users").method("POST").url("/users").build());

			when(repository.findById(COLLECTION_ID)).thenReturn(Mono.just(collection));
			when(endpointExtractor.extractEndpoints(collection.getItem())).thenReturn(expectedEndpoints);
			when(safeLogger.logOperationStartReactive(anyString(), any())).thenReturn(Mono.empty());

			// When
			Mono<List<EndpointInfo>> result = service.getEndpoints(COLLECTION_ID);

			// Then
			StepVerifier.create(result).expectNext(expectedEndpoints).verifyComplete();

			verify(endpointExtractor).extractEndpoints(collection.getItem());
		}

		@Test
        @DisplayName("Should handle collection not found for endpoint extraction")
        void shouldHandleCollectionNotFoundForEndpointExtraction() {
            // Given
            when(repository.findById(COLLECTION_ID)).thenReturn(Mono.empty());
            when(safeLogger.logOperationStartReactive(anyString(), any())).thenReturn(Mono.empty());

            // When
            Mono<List<EndpointInfo>> result = service.getEndpoints(COLLECTION_ID);

            // Then
            StepVerifier.create(result)
            .expectErrorSatisfies(throwable -> {
                assertThat(throwable).isInstanceOf(IllegalArgumentException.class);
                assertThat(throwable.getMessage()).contains("Collection not found with ID");
            })
            .verify();
        }

		@Test
		@DisplayName("Should handle extraction errors gracefully")
		void shouldHandleExtractionErrorsGracefully() {
			// Given
			Collection collection = createTestCollection();
			RuntimeException extractionError = new RuntimeException("Extraction failed");

			when(repository.findById(COLLECTION_ID)).thenReturn(Mono.just(collection));
			when(endpointExtractor.extractEndpoints(collection.getItem())).thenThrow(extractionError);
			when(safeLogger.logOperationStartReactive(anyString(), any())).thenReturn(Mono.empty());

			// When
			Mono<List<EndpointInfo>> result = service.getEndpoints(COLLECTION_ID);

			// Then
			StepVerifier.create(result).expectError(RuntimeException.class).verify();
		}
	}

	@Nested
	@DisplayName("Collection Statistics")
	class CollectionStatsTest {

		@Test
		@DisplayName("Should generate collection statistics successfully")
		void shouldGenerateCollectionStats() {
			// Given
			Collection collection = createTestCollection();

			when(repository.findById(COLLECTION_ID)).thenReturn(Mono.just(collection));
			when(safeLogger.logOperationStartReactive(anyString(), any())).thenReturn(Mono.empty());

			// When
			Mono<CollectionInfo> result = service.getCollectionStats(COLLECTION_ID);

			// Then
			StepVerifier.create(result).assertNext(stats -> {
				assertThat(stats.getCollectionName()).isEqualTo(COLLECTION_NAME);
				assertThat(stats.getTotalItems()).isEqualTo(1);
				assertThat(stats.getDescription()).isEqualTo("Test collection description");
			}).verifyComplete();
		}

		@Test
		@DisplayName("Should handle collection without description")
		void shouldHandleCollectionWithoutDescription() {
			// Given
			Collection collection = createTestCollection();
			collection.getInfo().setDescription(null);

			when(repository.findById(COLLECTION_ID)).thenReturn(Mono.just(collection));
			when(safeLogger.logOperationStartReactive(anyString(), any())).thenReturn(Mono.empty());

			// When
			Mono<CollectionInfo> result = service.getCollectionStats(COLLECTION_ID);

			// Then
			StepVerifier.create(result).assertNext(stats -> {
				assertThat(stats.getDescription()).isEqualTo("No description available");
			}).verifyComplete();
		}

		@Test
		@DisplayName("Should handle collection with empty items")
		void shouldHandleCollectionWithEmptyItems() {
			// Given
			Collection collection = createTestCollection();
			collection.setItem(null);

			when(repository.findById(COLLECTION_ID)).thenReturn(Mono.just(collection));
			when(safeLogger.logOperationStartReactive(anyString(), any())).thenReturn(Mono.empty());

			// When
			Mono<CollectionInfo> result = service.getCollectionStats(COLLECTION_ID);

			// Then
			StepVerifier.create(result).assertNext(stats -> {
				assertThat(stats.getTotalItems()).isEqualTo(0);
			}).verifyComplete();
		}

	}

	@Nested
	@DisplayName("Edge Cases and Error Handling")
	class EdgeCasesTest {

		@Test
		@DisplayName("Should handle collection with unnamed info")
		void shouldHandleCollectionWithUnnamedInfo() {
			// Given
			Collection collection = createTestCollection();
			collection.getInfo().setName(null);

			when(repository.findById(COLLECTION_ID)).thenReturn(Mono.just(collection));
			when(safeLogger.logOperationStartReactive(anyString(), any())).thenReturn(Mono.empty());

			// When
			Mono<CollectionInfo> result = service.getCollectionStats(COLLECTION_ID);

			// Then
			StepVerifier.create(result).assertNext(stats -> {
				assertThat(stats.getCollectionName()).isEqualTo("Unnamed Collection");
			}).verifyComplete();
		}

		@Test
		@DisplayName("Should handle database connection errors")
		void shouldHandleDatabaseConnectionErrors() {
			// Given
			DataAccessException dbError = mock(DataAccessException.class);
			when(repository.findAll()).thenReturn(Flux.error(dbError));
			when(safeLogger.logOperationStartReactive(anyString(), any())).thenReturn(Mono.empty());

			// When
			Flux<Collection> result = service.getAllCollections();

			// Then
			StepVerifier.create(result).expectError(DataAccessException.class).verify();
		}

		@Test
		@DisplayName("Should handle concurrent access scenarios")
		void shouldHandleConcurrentAccessScenarios() throws JsonProcessingException {
			// Given
			Collection collection = createNewCollection();

			// Simulate race condition where collection is created between check and save
			when(repository.findByInfoName(COLLECTION_NAME)).thenReturn(Mono.empty())
					.thenReturn(Mono.just(createTestCollection()));
			when(repository.save(collection)).thenReturn(Mono.just(createTestCollection()));
			when(properties.getMaxJsonSize()).thenReturn(MAX_JSON_SIZE);
			when(safeLogger.logOperationStartReactive(anyString(), any())).thenReturn(Mono.empty());

			// When - First call should succeed
			Mono<Collection> result1 = service.importCollectionFromJson(JSON_CONTENT);

			// Configure for second call
			when(objectMapper.readValue(JSON_CONTENT, Collection.class)).thenReturn(collection);

			// Then
			StepVerifier.create(result1).expectNextCount(1).verifyComplete();
		}

		@Test
		@DisplayName("Should handle very large collection imports")
		void shouldHandleVeryLargeCollectionImports() throws IOException {
			// Given
			Collection largeCollection = createNewCollection();
			// Simulate large collection with many items
			Item[] items = new Item[1000];
			for (int i = 0; i < 1000; i++) {
				items[i] = new Item();
				items[i].setName("Request " + i);
			}
			largeCollection.setItem(Arrays.asList(items));

			when(properties.getMaxJsonSize()).thenReturn(10 * 1024 * 1024L); // 10MB
			when(objectMapper.readValue(JSON_CONTENT, Collection.class)).thenReturn(largeCollection);
			when(repository.findByInfoName(COLLECTION_NAME)).thenReturn(Mono.empty());
			when(repository.save(largeCollection)).thenReturn(Mono.just(createTestCollection()));
			when(safeLogger.logOperationStartReactive(anyString(), any())).thenReturn(Mono.empty());

			// When
			Mono<Collection> result = service.importCollectionFromJson(JSON_CONTENT);

			// Then
			StepVerifier.create(result).expectNextCount(1).verifyComplete();
		}
	}

	@Nested
	@DisplayName("Reactive Stream Behavior")
	class ReactiveStreamTest {

		@Test
		@DisplayName("Should maintain reactive chain integrity")
		void shouldMaintainReactiveChainIntegrity() {
			// Given
			Collection collection = createTestCollection();
			when(repository.findById(COLLECTION_ID)).thenReturn(Mono.just(collection));
			when(safeLogger.logOperationStartReactive(anyString(), any())).thenReturn(Mono.empty());

			// When
			Mono<Collection> result = service.getCollectionById(COLLECTION_ID).map(c -> {
				c.getInfo().setName("Modified Name");
				return c;
			}).flatMap(service::saveCollection);

			when(repository.save(any())).thenReturn(Mono.just(collection));

			// Then
			StepVerifier.create(result).expectNextCount(1).verifyComplete();
		}

		@Test
		@DisplayName("Should handle backpressure correctly")
		void shouldHandleBackpressureCorrectly() {
			// Given
			List<Collection> collections = Arrays.asList(createTestCollection("1", "Collection 1"),
					createTestCollection("2", "Collection 2"), createTestCollection("3", "Collection 3"));

			when(repository.findAll()).thenReturn(Flux.fromIterable(collections));
			when(safeLogger.logOperationStartReactive(anyString(), any())).thenReturn(Mono.empty());

			// When
			Flux<Collection> result = service.getAllCollections();

			// Then
			StepVerifier.create(result, 1) // Request only 1 item initially
					.expectNext(collections.get(0)).thenRequest(2) // Request remaining items
					.expectNext(collections.get(1)).expectNext(collections.get(2)).verifyComplete();
		}

		@Test
        @DisplayName("Should handle cancellation gracefully")
        void shouldHandleCancellationGracefully() {
            // Given
            when(repository.findAll()).thenReturn(Flux.never()); // Never emits
            when(safeLogger.logOperationStartReactive(anyString(), any())).thenReturn(Mono.empty());

            // When
            Flux<Collection> result = service.getAllCollections();

            // Then
            StepVerifier.create(result)
                .expectSubscription()
                .thenCancel()
                .verify();
        }
	}
}