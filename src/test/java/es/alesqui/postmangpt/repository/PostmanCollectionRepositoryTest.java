package es.alesqui.postmangpt.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import es.alesqui.postmangpt.model.Collection;
import es.alesqui.postmangpt.model.Info;
import es.alesqui.postmangpt.model.Description;
import es.alesqui.postmangpt.model.Item;
import es.alesqui.postmangpt.model.Request;
import es.alesqui.postmangpt.model.Url;
import es.alesqui.postmangpt.model.Variable;
import es.alesqui.postmangpt.model.enums.RequestMethod;
import es.alesqui.postmangpt.config.EmbeddedMongoConfig;
import es.alesqui.postmangpt.model.Auth;
import es.alesqui.postmangpt.model.Event;
import es.alesqui.postmangpt.model.Script;
import es.alesqui.postmangpt.model.Body;
import es.alesqui.postmangpt.model.FormParameter;
import es.alesqui.postmangpt.model.GraphQLBody;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Integration tests for PostmanCollectionRepository. Tests all reactive MongoDB
 * operations and custom queries.
 */
@SpringBootTest(classes = EmbeddedMongoConfig.class)
@ActiveProfiles("test")
@DisplayName("PostmanCollectionRepository Tests")
class PostmanCollectionRepositoryTest {

	@Autowired
	private PostmanCollectionRepository repository;

	private Collection testCollection1;
	private Collection testCollection2;
	private Collection testCollection3;

	@BeforeEach
	void setUp() {
		// Clean database before each test
		repository.deleteAll().block(Duration.ofSeconds(5));

		// Create test collections
		testCollection1 = createTestCollection1();
		testCollection2 = createTestCollection2();
		testCollection3 = createTestCollection3();
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
	@DisplayName("Basic CRUD Operations")
	class BasicCrudOperationsTest {

		@Test
		@DisplayName("Should save and retrieve collection")
		void shouldSaveAndRetrieveCollection() {
			// When
			Mono<Collection> savedCollection = repository.save(testCollection1);

			// Then
			StepVerifier.create(savedCollection).assertNext(collection -> {
				assertThat(collection.getId()).isNotNull();
				assertThat(collection.getInfo().getName()).isEqualTo("Test API Collection");
			}).verifyComplete();
		}

		@Test
		@DisplayName("Should find collection by ID")
		void shouldFindCollectionById() {
			// Given
			Collection saved = repository.save(testCollection1).block();

			// When
			Mono<Collection> found = repository.findById(saved.getId());

			// Then
			StepVerifier.create(found).assertNext(collection -> {
				assertThat(collection.getId()).isEqualTo(saved.getId());
				assertThat(collection.getInfo().getName()).isEqualTo("Test API Collection");
			}).verifyComplete();
		}

		@Test
		@DisplayName("Should return empty when collection not found")
		void shouldReturnEmptyWhenCollectionNotFound() {
			// When
			Mono<Collection> found = repository.findById("nonexistent-id");

			// Then
			StepVerifier.create(found).verifyComplete();
		}

		@Test
		@DisplayName("Should find all collections")
		void shouldFindAllCollections() {
			// Given
			repository.saveAll(Arrays.asList(testCollection1, testCollection2, testCollection3))
					.blockLast(Duration.ofSeconds(5));

			// When
			Flux<Collection> allCollections = repository.findAll();

			// Then
			StepVerifier.create(allCollections).expectNextCount(3).verifyComplete();
		}

		@Test
		@DisplayName("Should delete collection by ID")
		void shouldDeleteCollectionById() {
			// Given
			Collection saved = repository.save(testCollection1).block();

			// When
			Mono<Void> deleteResult = repository.deleteById(saved.getId());

			// Then
			StepVerifier.create(deleteResult).verifyComplete();

			StepVerifier.create(repository.findById(saved.getId())).verifyComplete();
		}

		@Test
		@DisplayName("Should count collections")
		void shouldCountCollections() {
			// Given
			repository.saveAll(Arrays.asList(testCollection1, testCollection2)).blockLast(Duration.ofSeconds(5));

			// When
			Mono<Long> count = repository.count();

			// Then
			StepVerifier.create(count).expectNext(2L).verifyComplete();
		}
	}

	// ========================================
	// INFO-BASED QUERIES TESTS
	// ========================================

	@Nested
	@DisplayName("Info-based Queries")
	class InfoBasedQueriesTest {

		@BeforeEach
		void setUpCollections() {
			repository.saveAll(Arrays.asList(testCollection1, testCollection2, testCollection3))
					.blockLast(Duration.ofSeconds(5));
		}

		@Test
		@DisplayName("Should find collections by name containing (case insensitive)")
		void shouldFindByInfoNameContainingIgnoreCase() {
			// When
			Flux<Collection> results = repository.findByInfoNameContainingIgnoreCase("api");

			// Then
			StepVerifier.create(results)
					.assertNext(collection -> assertThat(collection.getInfo().getName()).containsIgnoringCase("api"))
					.expectNextCount(1) // Expecting GraphQL collection too
					.verifyComplete();
		}

		@Test
		@DisplayName("Should find collection by exact name")
		void shouldFindByInfoName() {
			// When
			Mono<Collection> result = repository.findByInfoName("Test API Collection");

			// Then
			StepVerifier.create(result)
					.assertNext(
							collection -> assertThat(collection.getInfo().getName()).isEqualTo("Test API Collection"))
					.verifyComplete();
		}

		@Test
		@DisplayName("Should find collections by schema")
		void shouldFindByInfoSchema() {
			// When
			Flux<Collection> results = repository
					.findByInfoSchema("https://schema.getpostman.com/json/collection/v2.1.0/collection.json");

			// Then
			StepVerifier.create(results).expectNextCount(3).verifyComplete();
		}

		@Test
		@DisplayName("Should find collections by description type")
		void shouldFindByInfoDescriptionType() {
			// When
			Flux<Collection> results = repository.findByInfoDescriptionType("text/markdown");

			// Then
			StepVerifier.create(results).assertNext(collection -> {
				assertThat(collection.getInfo().getDescription().getType()).isEqualTo("text/markdown");
				assertThat(collection.getInfo().getName()).isEqualTo("Test API Collection");
			}).assertNext(collection -> {
				assertThat(collection.getInfo().getDescription().getType()).isEqualTo("text/markdown");
				assertThat(collection.getInfo().getName()).isEqualTo("GraphQL API Collection");
			}).verifyComplete();
		}
	}

	// ========================================
	// COLLECTION STRUCTURE QUERIES TESTS
	// ========================================

	@Nested
	@DisplayName("Collection Structure Queries")
	class CollectionStructureQueriesTest {

		@BeforeEach
		void setUpCollections() {
			repository.saveAll(Arrays.asList(testCollection1, testCollection2, testCollection3))
					.blockLast(Duration.ofSeconds(5));
		}

		@Test
		@DisplayName("Should find collections by item name containing")
		void shouldFindByItemNameContainingIgnoreCase() {
			// When
			Flux<Collection> results = repository.findByItemNameContainingIgnoreCase("users");

			// Then
			StepVerifier.create(results).expectNextCount(1).verifyComplete();
		}

		@Test
		@DisplayName("Should find collections by request method")
		void shouldFindByRequestMethod() {
			// When
			Flux<Collection> results = repository.findByRequestMethod("POST");

			// Then
			StepVerifier.create(results).expectNextCount(2).verifyComplete();
		}

		@Test
		@DisplayName("Should find collections by request URL containing")
		void shouldFindByRequestUrlContaining() {
			// When
			Flux<Collection> results = repository.findByRequestUrlContaining("api.example.com");

			// Then
			StepVerifier.create(results).expectNextCount(1).verifyComplete();
		}

		@Test
		@DisplayName("Should find collections by variable key")
		void shouldFindByVariableKey() {
			// When
			Flux<Collection> results = repository.findByVariableKey("baseUrl");

			// Then
			StepVerifier.create(results).expectNextCount(1).verifyComplete();
		}
	}

	// ========================================
	// ADVANCED SEARCH TESTS
	// ========================================

	@Nested
	@DisplayName("Advanced Search and Filtering")
	class AdvancedSearchTest {

		@BeforeEach
		void setUpCollections() {
			repository.saveAll(Arrays.asList(testCollection1, testCollection2, testCollection3))
					.blockLast(Duration.ofSeconds(5));
		}

		@Test
		@DisplayName("Should find collections by description content containing")
		void shouldFindByInfoDescriptionContentContainingIgnoreCase() {
			// When
			Flux<Collection> results = repository.findByInfoDescriptionContentContainingIgnoreCase("REST");

			// Then
			StepVerifier.create(results).expectNextCount(1).verifyComplete();
		}

		@Test
		@DisplayName("Should search collections across multiple fields")
		void shouldSearchCollections() {
			// When
			Flux<Collection> results = repository.searchCollections("GraphQL");

			// Then
			StepVerifier.create(results).expectNextCount(1).verifyComplete();
		}

		@Test
		@DisplayName("Should find collections by name pattern and method")
		void shouldFindByNamePatternAndMethod() {
			// When
			Flux<Collection> results = repository.findByNamePatternAndMethod("API", "GET");

			// Then
			StepVerifier.create(results).assertNext(collection -> {
				// Assert the properties of the first matching collection
				assertNotNull(collection);
				assertEquals("Test API Collection", collection.getInfo().getName());
			}).assertNext(collection -> {
				// Assert the properties of the second matching collection
				assertNotNull(collection);
				assertEquals("GraphQL API Collection", collection.getInfo().getName());
			}).expectComplete().verify();
		}
	}

	// ========================================
	// METADATA AND UTILITY QUERIES TESTS
	// ========================================

	@Nested
	@DisplayName("Metadata and Utility Queries")
	class MetadataUtilityQueriesTest {

		@BeforeEach
		void setUpCollections() {
			repository.saveAll(Arrays.asList(testCollection1, testCollection2, testCollection3))
					.blockLast(Duration.ofSeconds(5));
		}

		@Test
		@DisplayName("Should count collections by request method")
		void shouldCountByRequestMethod() {
			// When
			Mono<Long> count = repository.countByRequestMethod("GET");

			// Then
			StepVerifier.create(count).expectNext(2L).verifyComplete();
		}

		@Test
		@DisplayName("Should find collections with authentication and log detailed info")
		void shouldFindCollectionsWithAuth() {
			// When
			Flux<Collection> results = repository.findCollectionsWithAuth();

			// Then
			StepVerifier.create(results).assertNext(collection -> {
				assertThat(collection.getInfo().getName()).isEqualTo("Test API Collection");
				assertThat(collection.getItem()).hasSize(2);

				// Verify that at least one item has authentication
				boolean hasAuth = collection.getItem().stream().anyMatch(item -> item.getRequest().getAuth() != null);
				assertThat(hasAuth).isTrue();

				// Verify specific auth type
				Item createUserItem = collection.getItem().stream().filter(item -> "Create User".equals(item.getName()))
						.findFirst().orElseThrow(() -> new AssertionError("Create User item not found"));

				assertThat(createUserItem.getRequest().getAuth()).isNotNull();
				assertThat(createUserItem.getRequest().getAuth().getType()).isEqualTo("bearer");
			}).verifyComplete();
		}

		@Test
		@DisplayName("Should find collections with scripts")
		void shouldFindCollectionsWithScripts() {
			// When
			Flux<Collection> results = repository.findCollectionsWithScripts();

			// Then
			StepVerifier.create(results).expectNextCount(1).verifyComplete();
		}

		@Test
		@DisplayName("Should find collections by item count between")
		void shouldFindByItemCountBetween() {
			// When
			Flux<Collection> results = repository.findByItemCountBetween(1, 3);

			// Then
			StepVerifier.create(results).expectNextCount(3).verifyComplete();
		}
	}

	// ========================================
	// POSTMAN-SPECIFIC QUERIES TESTS
	// ========================================

	@Nested
	@DisplayName("Postman-specific Queries")
	class PostmanSpecificQueriesTest {

		@BeforeEach
		void setUpCollections() {
			repository.saveAll(Arrays.asList(testCollection1, testCollection2, testCollection3))
					.blockLast(Duration.ofSeconds(5));
		}

		@Test
		@DisplayName("Should find collections with GraphQL")
		void shouldFindCollectionsWithGraphQL() {
			// When
			Flux<Collection> results = repository.findCollectionsWithGraphQL();

			// Then
			StepVerifier.create(results).expectNextCount(1).verifyComplete();
		}

		@Test
		@DisplayName("Should find collections with file uploads")
		void shouldFindCollectionsWithFileUploads() {
			// When
			Flux<Collection> results = repository.findCollectionsWithFileUploads();

			// Then
			StepVerifier.create(results).expectNextCount(1).verifyComplete();
		}
	}

	// ========================================
	// VALIDATION AND EXISTENCE CHECKS TESTS
	// ========================================

	@Nested
	@DisplayName("Validation and Existence Checks")
	class ValidationExistenceChecksTest {

		@BeforeEach
		void setUpCollections() {
			repository.save(testCollection1).block(Duration.ofSeconds(5));
		}

		@Test
		@DisplayName("Should check if collection exists by name")
		void shouldCheckExistsByInfoName() {
			// When
			Mono<Boolean> exists = repository.existsByInfoName("Test API Collection");
			Mono<Boolean> notExists = repository.existsByInfoName("Non-existent Collection");

			// Then
			StepVerifier.create(exists).expectNext(true).verifyComplete();

			StepVerifier.create(notExists).expectNext(false).verifyComplete();
		}

		@Test
		@DisplayName("Should find first collection ordered by ID desc")
		void shouldFindFirstByOrderByIdDesc() {
			// Given
			repository.saveAll(Arrays.asList(testCollection2, testCollection3)).blockLast(Duration.ofSeconds(5));

			// When
			Mono<Collection> result = repository.findFirstByOrderByIdDesc();

			// Then
			StepVerifier.create(result).assertNext(collection -> assertThat(collection).isNotNull()).verifyComplete();
		}
	}

	// ========================================
	// TEST DATA CREATION METHODS
	// ========================================

	private Collection createTestCollection1() {
		Info info = Info.builder().name("Test API Collection")
				.description(Description.builder().content("A comprehensive REST API collection for testing")
						.type("text/markdown").build())
				.schema("https://schema.getpostman.com/json/collection/v2.1.0/collection.json").build();

		Item getUsersItem = Item.builder().name("Get Users").request(Request.builder().method(RequestMethod.GET)
				.url(Url.builder().raw("https://api.example.com/users").build()).build()).build();

		Item createUserItem = Item.builder().name("Create User")
				.request(Request.builder().method(RequestMethod.POST)
						.url(Url.builder().raw("https://api.example.com/users").build())
						.auth(Auth.builder().type("bearer").build()).build())
				.event(Collections
						.singletonList(Event.builder().listen("test")
								.script(Script.builder().type("text/javascript")
										.exec(Arrays.asList("pm.test('Status code is 201', function () {",
												"    pm.response.to.have.status(201);", "});"))
										.build())
								.build()))
				.build();

		Variable baseUrlVar = Variable.builder().key("baseUrl").value("https://api.example.com").type("string").build();

		return Collection.builder().info(info).item(Arrays.asList(getUsersItem, createUserItem))
				.variable(Collections.singletonList(baseUrlVar)).build();
	}

	private Collection createTestCollection2() {
		Info info = Info.builder().name("File Upload Collection")
				.description(
						Description.builder().content("Collection for testing file uploads").type("text/plain").build())
				.schema("https://schema.getpostman.com/json/collection/v2.1.0/collection.json").build();

		Item uploadItem = Item.builder().name("Upload File").request(Request.builder().method(RequestMethod.POST)
				.url(Url.builder().raw("https://upload.example.com/files").build())
				.body(Body.builder().mode("formdata")
						.formdata(Collections.singletonList(
								FormParameter.builder().key("file").type("file").src("test-file.txt").build()))
						.build())
				.build()).build();

		return Collection.builder().info(info).item(Collections.singletonList(uploadItem)).build();
	}

	private Collection createTestCollection3() {
		Info info = Info.builder().name("GraphQL API Collection")
				.description(
						Description.builder().content("GraphQL queries and mutations").type("text/markdown").build())
				.schema("https://schema.getpostman.com/json/collection/v2.1.0/collection.json").build();

		Item graphqlItem = Item.builder().name("Get User Query")
				.request(Request.builder().method(RequestMethod.GET)
						.url(Url.builder().raw("https://graphql.example.com/query").build())
						.body(Body.builder().mode("graphql")
								.graphql(GraphQLBody.builder()
										.query("query GetUser($id: ID!) { user(id: $id) { id name email } }")
										.variables("{\"id\": \"123\"}").build())
								.build())
						.build())
				.build();

		return Collection.builder().info(info).item(Collections.singletonList(graphqlItem)).build();
	}
}
