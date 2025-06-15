package es.alesqui.postmangpt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;

import es.alesqui.postmangpt.model.Collection;
import es.alesqui.postmangpt.model.Info;
import es.alesqui.postmangpt.model.Item;
import es.alesqui.postmangpt.repository.PostmanCollectionRepository;

@ExtendWith(MockitoExtension.class)
class PostmanCollectionServiceTest {

	@Mock
	private PostmanCollectionRepository repository;

	@Mock
	private ObjectMapper objectMapper;

	@Mock
	private MultipartFile multipartFile;

	@InjectMocks
	private PostmanCollectionService service;

	private Collection sampleCollection;

	@BeforeEach
	void setUp() {
		// Crear colección de prueba
		sampleCollection = new Collection();

		Info info = Info.create("Test Collection", "Test Description");
		sampleCollection.setInfo(info);

		Item item = new Item();
		item.setName("Test Request");
		sampleCollection.setItem(Arrays.asList(item));
	}

	@Test
	void getAllCollections_ShouldReturnAllCollections() {
		// Given
		List<Collection> collections = Arrays.asList(sampleCollection);
		when(repository.findAll()).thenReturn(collections);

		// When
		List<Collection> result = service.getAllCollections();

		// Then
		assertThat(result).hasSize(1);
		assertThat(result.get(0).getInfo().getName()).isEqualTo("Test Collection");
		verify(repository).findAll();
	}

	@Test
	void getCollectionById_ShouldReturnCollection_WhenExists() {
		// Given
		String id = "test-id";
		when(repository.findById(id)).thenReturn(Optional.of(sampleCollection));

		// When
		Optional<Collection> result = service.getCollectionById(id);

		// Then
		assertThat(result).isPresent();
		assertThat(result.get().getInfo().getName()).isEqualTo("Test Collection");
		verify(repository).findById(id);
	}

	@Test
	void getCollectionById_ShouldReturnEmpty_WhenNotExists() {
		// Given
		String id = "non-existent-id";
		when(repository.findById(id)).thenReturn(Optional.empty());

		// When
		Optional<Collection> result = service.getCollectionById(id);

		// Then
		assertThat(result).isEmpty();
		verify(repository).findById(id);
	}

	@Test
    void saveCollection_ShouldSaveAndReturnCollection() {
        // Given
        when(repository.save(any(Collection.class))).thenReturn(sampleCollection);

        // When
        Collection result = service.saveCollection(sampleCollection);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getInfo().getName()).isEqualTo("Test Collection");
        verify(repository).save(sampleCollection);
    }

	@Test
	void deleteCollection_ShouldCallRepositoryDelete() {
		// Given
		String id = "test-id";

		// When
		service.deleteCollection(id);

		// Then
		verify(repository).deleteById(id);
	}

	@Test
	void importCollectionFromFile_ShouldImportSuccessfully() throws IOException {
		// Given
		String filename = "test.json";
		String jsonContent = "{\"info\":{\"name\":\"Test Collection\"}}";

		when(multipartFile.isEmpty()).thenReturn(false);
		when(multipartFile.getOriginalFilename()).thenReturn(filename);
		when(multipartFile.getInputStream()).thenReturn(new ByteArrayInputStream(jsonContent.getBytes()));
		when(objectMapper.readValue(any(InputStream.class), eq(Collection.class))).thenReturn(sampleCollection);
		when(repository.findByInfoName(anyString())).thenReturn(Optional.empty());
		when(repository.save(any(Collection.class))).thenReturn(sampleCollection);

		// When
		Collection result = service.importCollectionFromFile(multipartFile);

		// Then
		assertThat(result).isNotNull();
		assertThat(result.getInfo().getName()).isEqualTo("Test Collection");
		verify(repository).save(any(Collection.class));
	}

	@Test
    void importCollectionFromFile_ShouldThrowException_WhenFileIsEmpty() {
        // Given
        when(multipartFile.isEmpty()).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> service.importCollectionFromFile(multipartFile))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("File is empty");
    }

	@Test
    void importCollectionFromFile_ShouldThrowException_WhenFileIsNotJson() {
        // Given
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getOriginalFilename()).thenReturn("test.txt");

        // When & Then
        assertThatThrownBy(() -> service.importCollectionFromFile(multipartFile))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("File must be a valid JSON");
    }

	@Test
	void importCollectionFromFile_ShouldThrowException_WhenCollectionAlreadyExists() throws IOException {
		// Given
		String filename = "test.json";
		String jsonContent = "{\"info\":{\"name\":\"Test Collection\"}}";

		when(multipartFile.isEmpty()).thenReturn(false);
		when(multipartFile.getOriginalFilename()).thenReturn(filename);
		when(multipartFile.getInputStream()).thenReturn(new ByteArrayInputStream(jsonContent.getBytes()));
		when(objectMapper.readValue(any(InputStream.class), eq(Collection.class))).thenReturn(sampleCollection);
		when(repository.findByInfoName("Test Collection")).thenReturn(Optional.of(sampleCollection));

		// When & Then
		assertThatThrownBy(() -> service.importCollectionFromFile(multipartFile))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("Collection with this name already exists");
	}

	@Test
	void getCollectionStats_ShouldReturnCorrectStats() {
		// Given
		String collectionId = "test-id";
		when(repository.findById(collectionId)).thenReturn(Optional.of(sampleCollection));

		// When
		PostmanCollectionService.CollectionStats stats = service.getCollectionStats(collectionId);

		// Then
		assertThat(stats.getCollectionName()).isEqualTo("Test Collection");
		assertThat(stats.getTotalItems()).isEqualTo(1);
		assertThat(stats.getDescription()).isEqualTo("Test Description");
	}

	@Test
	void getCollectionStats_ShouldThrowException_WhenCollectionNotFound() {
		// Given
		String collectionId = "non-existent-id";
		when(repository.findById(collectionId)).thenReturn(Optional.empty());

		// When & Then
		assertThatThrownBy(() -> service.getCollectionStats(collectionId)).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Collection not found");
	}
}
