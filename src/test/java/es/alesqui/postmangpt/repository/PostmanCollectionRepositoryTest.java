package es.alesqui.postmangpt.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.test.context.ActiveProfiles;

import es.alesqui.postmangpt.model.Collection;
import es.alesqui.postmangpt.model.Description;
import es.alesqui.postmangpt.model.Info;
import es.alesqui.postmangpt.model.Item;
import es.alesqui.postmangpt.model.Request;
import es.alesqui.postmangpt.model.Url;
import es.alesqui.postmangpt.model.Variable;
import es.alesqui.postmangpt.model.Version;
import es.alesqui.postmangpt.model.enums.RequestMethod;

@DataMongoTest
@ActiveProfiles("test")
class PostmanCollectionRepositoryTest {

	@Autowired
	private PostmanCollectionRepository repository;

	private Collection sampleCollection;
	private Info sampleInfo;

	@BeforeEach
	void setUp() {
		// Clean database before each test
		repository.deleteAll();

		// Create test data
		sampleInfo = Info.builder().name("Test API Collection")
				.description(Description.create("Collection for testing REST APIs"))
				.schema("https://schema.getpostman.com/json/collection/v2.1.0/collection.json")
				.postmanId(UUID.randomUUID().toString()).version(Version.builder().major(1).minor(0).patch(0).build())
				.build();

		sampleCollection = Collection.builder().info(sampleInfo).item(new ArrayList<>()).variable(new ArrayList<>())
				.build();
	}

	@AfterEach
	void tearDown() {
		repository.deleteAll();
	}

	// ========== BASIC TESTS ==========

	@Test
	void contextLoads() {
		assertThat(repository).isNotNull();
	}

	@Test
	void repositoryCanPerformBasicOperations() {
		long initialCount = repository.count();
		assertThat(initialCount).isEqualTo(0);
	}

	// ========== BASIC CRUD TESTS ==========

	@Test
	void shouldSaveCollection() {
		// When
		Collection saved = repository.save(sampleCollection);

		// Then
		assertThat(saved).isNotNull();
		assertThat(saved.getId()).isNotNull(); // MongoDB generates the ID
		assertThat(saved.getName()).isEqualTo("Test API Collection");
		assertThat(saved.getDescription()).isEqualTo("Collection for testing REST APIs");
		assertThat(saved.getPostmanId()).isNotNull();
		assertThat(saved.getSchema()).isEqualTo("https://schema.getpostman.com/json/collection/v2.1.0/collection.json");

		// Verify it was saved to database
		assertThat(repository.count()).isEqualTo(1);
	}

	@Test
	void shouldFindById() {
		// Given
		Collection saved = repository.save(sampleCollection);

		// When
		Optional<Collection> found = repository.findById(saved.getId());

		// Then
		assertThat(found).isPresent();
		Collection foundCollection = found.get();
		assertThat(foundCollection.getName()).isEqualTo("Test API Collection");
		assertThat(foundCollection.getDescription()).isEqualTo("Collection for testing REST APIs");
		assertThat(foundCollection.getPostmanId()).isEqualTo(sampleCollection.getPostmanId());
	}

	@Test
	void shouldReturnEmptyWhenFindByIdNotExists() {
		// When
		Optional<Collection> found = repository.findById("nonexistent-id");

		// Then
		assertThat(found).isEmpty();
	}

	@Test
	void shouldFindAll() {
		// Given - Create multiple collections
		Collection collection1 = Collection.create("API Tests", "Testing API endpoints");
		Collection collection2 = Collection.create("Integration Tests", "Integration testing collection");
		Collection collection3 = Collection.create("Unit Tests", "Unit testing collection");

		repository.saveAll(Arrays.asList(collection1, collection2, collection3));

		// When
		List<Collection> collections = repository.findAll();

		// Then
		assertThat(collections).hasSize(3);
		assertThat(collections).extracting(Collection::getName).containsExactlyInAnyOrder("API Tests",
				"Integration Tests", "Unit Tests");
	}

	@Test
	void shouldUpdateCollection() {
		// Given
		Collection saved = repository.save(sampleCollection);

		// When - Update using fluent API
		saved.withName("Updated Collection Name").withDescription("Updated description for testing");
		Collection updated = repository.save(saved);

		// Then
		assertThat(updated.getId()).isEqualTo(saved.getId());
		assertThat(updated.getName()).isEqualTo("Updated Collection Name");
		assertThat(updated.getDescription()).isEqualTo("Updated description for testing");

		// Verify in database
		Optional<Collection> found = repository.findById(saved.getId());
		assertThat(found).isPresent();
		assertThat(found.get().getName()).isEqualTo("Updated Collection Name");
	}

	@Test
	void shouldDeleteById() {
		// Given
		Collection saved = repository.save(sampleCollection);
		assertThat(repository.count()).isEqualTo(1);

		// When
		repository.deleteById(saved.getId());

		// Then
		assertThat(repository.count()).isEqualTo(0);
		Optional<Collection> found = repository.findById(saved.getId());
		assertThat(found).isEmpty();
	}

	@Test
	void shouldDeleteAll() {
		// Given
		repository.saveAll(Arrays.asList(Collection.create("Collection 1", "First collection"),
				Collection.create("Collection 2", "Second collection"),
				Collection.create("Collection 3", "Third collection")));
		assertThat(repository.count()).isEqualTo(3);

		// When
		repository.deleteAll();

		// Then
		assertThat(repository.count()).isEqualTo(0);
		List<Collection> collections = repository.findAll();
		assertThat(collections).isEmpty();
	}

	// ========== FACTORY METHODS TESTS ==========

	@Test
	void shouldCreateCollectionWithName() {
		// Given
		Collection collection = Collection.create("Simple Collection");

		// When
		Collection saved = repository.save(collection);

		// Then
		assertThat(saved.getName()).isEqualTo("Simple Collection");
		assertThat(saved.getPostmanId()).isNotNull();
		assertThat(saved.getSchema()).isEqualTo("https://schema.getpostman.com/json/collection/v2.1.0/collection.json");
		assertThat(saved.getInfo().getVersion().getMajor()).isEqualTo(1);
	}

	@Test
	void shouldCreateCollectionWithNameAndDescription() {
		// Given
		Collection collection = Collection.create("API Collection", "Collection for API testing");

		// When
		Collection saved = repository.save(collection);

		// Then
		assertThat(saved.getName()).isEqualTo("API Collection");
		assertThat(saved.getDescription()).isEqualTo("Collection for API testing");
		assertThat(saved.isValid()).isTrue();
	}

	// ========== FLUENT API TESTS ==========

	@Test
	void shouldUseFluentApiForBuilding() {
		// Given
		Collection collection = Collection.create("Base Collection").withDescription("Fluent API test")
				.addVariable("baseUrl", "https://api.example.com")
				.addVariable("apiKey", "secret-key", "API authentication key").addFolder("Authentication")
				.addFolder("Users", new ArrayList<>());

		// When
		Collection saved = repository.save(collection);

		// Then
		assertThat(saved.getName()).isEqualTo("Base Collection");
		assertThat(saved.getDescription()).isEqualTo("Fluent API test");
		assertThat(saved.getVariableCount()).isEqualTo(2);
		assertThat(saved.getItemCount()).isEqualTo(2);
		assertThat(saved.hasVariables()).isTrue();
		assertThat(saved.hasItems()).isTrue();
	}

	@Test
	void shouldHandleVariables() {
		// Given
		Collection collection = Collection.create("Variable Test").addVariable("env", "development")
				.addVariable("timeout", "5000").addVariable("retries", "3", "Number of retry attempts");

		// When
		Collection saved = repository.save(collection);

		// Then
		assertThat(saved.getVariableCount()).isEqualTo(3);
		assertThat(saved.hasVariables()).isTrue();

		Variable envVar = saved.findVariableByKey("env");
		assertThat(envVar).isNotNull();
		assertThat(envVar.getValue()).isEqualTo("development");

		Variable retriesVar = saved.findVariableByKey("retries");
		assertThat(retriesVar).isNotNull();
		assertThat(retriesVar.getDescription().getContent()).isEqualTo("Number of retry attempts");
	}

	@Test
	void shouldHandleItems() {
		// Given - Create items using builder pattern
		Item requestItem = Item.builder().name("Get Users")
				.request(Request.builder().method(RequestMethod.GET).url(Url.create("{{baseUrl}}/users")).build())
				.build();

		Collection collection = Collection.create("Items Test").addItem(requestItem).addFolder("Authentication");

		// When
		Collection saved = repository.save(collection);

		// Then
		assertThat(saved.getItemCount()).isEqualTo(2);
		assertThat(saved.hasItems()).isTrue();
		assertThat(saved.isEmpty()).isFalse();

		Item foundItem = saved.findItemByName("Get Users");
		assertThat(foundItem).isNotNull();
		assertThat(foundItem.getRequest()).isNotNull();
		assertThat(foundItem.getRequest().getMethod().getValue()).isEqualTo("GET");
	}

	// ========== QUERY METHODS TESTS ==========

	@Test
	void shouldProvideQueryMethods() {
		// Given
		Collection collection = Collection.create("Query Test", "Testing query methods").addVariable("var1", "value1")
				.addVariable("var2", "value2").addFolder("Folder1").addFolder("Folder2");

		// When
		Collection saved = repository.save(collection);

		// Then
		assertThat(saved.getName()).isEqualTo("Query Test");
		assertThat(saved.getDescription()).isEqualTo("Testing query methods");
		assertThat(saved.getPostmanId()).isNotNull();
		assertThat(saved.getSchema()).contains("v2.1.0");
		assertThat(saved.getItemCount()).isEqualTo(2);
		assertThat(saved.getVariableCount()).isEqualTo(2);
		assertThat(saved.getEventCount()).isEqualTo(0);
		assertThat(saved.hasAuth()).isFalse();
		assertThat(saved.hasProtocolProfileBehavior()).isFalse();
		assertThat(saved.hasVariables()).isTrue();
		assertThat(saved.hasEvents()).isFalse();
		assertThat(saved.hasItems()).isTrue();
		assertThat(saved.isEmpty()).isFalse();
	}

	@Test
	void shouldHandleEvents() {
		// Given
		Collection collection = Collection.create("Events Test")
				.addPreRequestScript("console.log('Pre-request script');")
				.addTestScript("pm.test('Status is 200', function() { pm.response.to.have.status(200); });");

		// When
		Collection saved = repository.save(collection);

		// Then
		assertThat(saved.getEventCount()).isEqualTo(2);
		assertThat(saved.hasEvents()).isTrue();
	}

	// ========== EDGE CASES TESTS ==========

	@Test
	void shouldHandleNullValues() {
		// Given
		Collection collection = new Collection();
		collection.setInfo(Info.create("Minimal Collection"));

		// When
		Collection saved = repository.save(collection);

		// Then
		assertThat(saved).isNotNull();
		assertThat(saved.getId()).isNotNull();
		assertThat(saved.getName()).isEqualTo("Minimal Collection");
		assertThat(saved.getDescription()).isNull();
		assertThat(saved.getItemCount()).isEqualTo(0);
		assertThat(saved.getVariableCount()).isEqualTo(0);
		assertThat(saved.isEmpty()).isTrue();
	}

	@Test
	void shouldHandleEmptyCollections() {
		// Given
		Collection collection = Collection.create("", "");

		// When
		Collection saved = repository.save(collection);

		// Then
		assertThat(saved.getName()).isEmpty();
		assertThat(saved.getDescription()).isEmpty();
		assertThat(saved.isEmpty()).isTrue();
	}

	@Test
	void shouldHandleLargeData() {
		// Given
		String largeName = "Collection with very long name: " + "A".repeat(500);
		String largeDescription = "Very detailed description: " + "B".repeat(2000);

		Collection collection = Collection.create(largeName, largeDescription);

		// When
		Collection saved = repository.save(collection);

		// Then
		assertThat(saved.getName()).hasSize(largeName.length());
		assertThat(saved.getDescription()).hasSize(largeDescription.length());
	}

	@Test
	void shouldHandleSpecialCharacters() {
		// Given
		Collection collection = Collection.create("Collection with émojis 🚀 and spëcial chars áéíóú",
				"Description with 中文, العربية, русский, and symbols: @#$%^&*()");

		// When
		Collection saved = repository.save(collection);

		// Then
		Optional<Collection> found = repository.findById(saved.getId());
		assertThat(found).isPresent();
		assertThat(found.get().getName()).contains("🚀");
		assertThat(found.get().getDescription()).contains("中文");
	}

	@Test
	void shouldMaintainDataIntegrity() {
		// Given
		Collection original = repository.save(sampleCollection);
		String originalId = original.getId();
		String originalPostmanId = original.getPostmanId();

		// When - Multiple operations
		original.withName("Modified Name").withDescription("Modified description");
		repository.save(original);

		Collection another = Collection.create("Another", "Another collection");
		repository.save(another);

		// Then
		List<Collection> all = repository.findAll();
		assertThat(all).hasSize(2);

		Optional<Collection> modified = repository.findById(originalId);
		assertThat(modified).isPresent();
		assertThat(modified.get().getName()).isEqualTo("Modified Name");
		assertThat(modified.get().getPostmanId()).isEqualTo(originalPostmanId); // Should not change
	}

	// ========== UTILITY METHODS TESTS ==========

	@Test
	void shouldHandleUtilityMethods() {
		// Given
		Collection collection = Collection.create("Utility Test").addVariable("var1", "value1")
				.addVariable("var2", "value2").addFolder("folder1").addFolder("folder2");

		// When
		Collection saved = repository.save(collection);

		// Then - Test utility methods
		assertThat(saved.removeVariable("var1")).isTrue();
		assertThat(saved.removeVariable("nonexistent")).isFalse();
		assertThat(saved.getVariableCount()).isEqualTo(1);

		assertThat(saved.removeItem("folder1")).isTrue();
		assertThat(saved.removeItem("nonexistent")).isFalse();
		assertThat(saved.getItemCount()).isEqualTo(1);

		saved.clearVariables();
		assertThat(saved.hasVariables()).isFalse();

		saved.clearItems();
		assertThat(saved.isEmpty()).isTrue();
	}

	@Test
	void shouldValidateCollection() {
		// Given
		Collection validCollection = Collection.create("Valid Collection", "Valid description");
		Collection invalidCollection = new Collection(); // Without info

		// When & Then
		assertThat(validCollection.isValid()).isTrue();
		assertThat(invalidCollection.isValid()).isFalse();
	}

	@Test
	void shouldCopyCollection() {
		// Given
		Collection original = Collection.create("Original", "Original description").addVariable("key", "value")
				.addFolder("folder");

		// When
		Collection copy = original.copy();

		// Then
		assertThat(copy).isNotSameAs(original);
		assertThat(copy.getName()).isEqualTo(original.getName());
		assertThat(copy.getDescription()).isEqualTo(original.getDescription());
		assertThat(copy.getVariableCount()).isEqualTo(original.getVariableCount());
		assertThat(copy.getItemCount()).isEqualTo(original.getItemCount());
	}

	@Test
	void shouldProvideCollectionStats() {
		// Given
		Collection collection = Collection.create("Stats Test").addVariable("var1", "value1")
				.addVariable("var2", "value2").addFolder("folder1").addFolder("folder2");

		// When
		Collection saved = repository.save(collection);
		Collection.CollectionStats stats = saved.getStats();

		// Then
		assertThat(stats.getTotalItems()).isEqualTo(2);
		assertThat(stats.getTotalVariables()).isEqualTo(2);
		assertThat(stats.getTotalFolders()).isEqualTo(2);
		assertThat(stats.getTotalRequests()).isEqualTo(0);
		assertThat(stats.isHasAuth()).isFalse();
		assertThat(stats.isHasProtocolProfileBehavior()).isFalse();
	}

	// ========== PERFORMANCE TESTS ==========

	@Test
	void shouldHandleBulkOperations() {
		// Given
		List<Collection> collections = IntStream.range(0, 50)
				.mapToObj(i -> Collection.create("Collection " + i, "Description " + i)).collect(Collectors.toList());

		// When
		long startTime = System.currentTimeMillis();
		List<Collection> saved = repository.saveAll(collections);
		long endTime = System.currentTimeMillis();

		// Then
		assertThat(saved).hasSize(50);
		assertThat(repository.count()).isEqualTo(50);

		// Performance assertion (adjust according to your needs)
		long duration = endTime - startTime;
		assertThat(duration).isLessThan(3000); // less than 3 seconds
	}

	@Test
	void shouldHandleComplexCollections() {
		// Given - Create a complex collection
		Collection complexCollection = Collection
				.create("Complex API Collection", "Full-featured API testing collection")
				.addVariable("baseUrl", "https://api.example.com").addVariable("apiKey", "secret-key")
				.addVariable("timeout", "5000").addPreRequestScript("pm.globals.set('timestamp', Date.now());")
				.addTestScript(
						"pm.test('Response time is less than 200ms', function () { pm.expect(pm.response.responseTime).to.be.below(200); });")
				.addFolder("Authentication").addFolder("Users").addFolder("Products");

		// When
		Collection saved = repository.save(complexCollection);

		// Then
		assertThat(saved.getName()).isEqualTo("Complex API Collection");
		assertThat(saved.getVariableCount()).isEqualTo(3);
		assertThat(saved.getEventCount()).isEqualTo(2);
		assertThat(saved.getItemCount()).isEqualTo(3);
		assertThat(saved.getTotalFolderCount()).isEqualTo(3);
		assertThat(saved.isValid()).isTrue();

		// Verify it can be retrieved correctly
		Optional<Collection> found = repository.findById(saved.getId());
		assertThat(found).isPresent();
		assertThat(found.get().getVariableCount()).isEqualTo(3);
		assertThat(found.get().getEventCount()).isEqualTo(2);
	}
}
