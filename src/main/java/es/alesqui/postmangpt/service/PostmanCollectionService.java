package es.alesqui.postmangpt.service;

import es.alesqui.postmangpt.model.Collection;
import es.alesqui.postmangpt.repository.PostmanCollectionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Service
public class PostmanCollectionService {

	private static final Logger logger = LoggerFactory.getLogger(PostmanCollectionService.class);

	@Autowired
	private PostmanCollectionRepository repository;

	@Autowired
	private ObjectMapper objectMapper;

	/**
	 * Retrieves all collections from the database.
	 * 
	 * @return List of all collections
	 */
	public List<Collection> getAllCollections() {
		logger.info("Retrieving all collections");
		return repository.findAll();
	}

	/**
	 * Retrieves a collection by its unique identifier.
	 * 
	 * @param id the unique identifier of the collection
	 * @return Optional containing the collection if found, empty otherwise
	 */
	public Optional<Collection> getCollectionById(String id) {
		logger.info("Retrieving collection with ID: {}", id);
		return repository.findById(id);
	}

	/**
	 * Retrieves a collection by its name.
	 * 
	 * @param name the name of the collection to search for
	 * @return Optional containing the collection if found, empty otherwise
	 */
	public Optional<Collection> getCollectionByName(String name) {
		logger.info("Retrieving collection with name: {}", name);
		return repository.findByInfoName(name);
	}

	/**
	 * Saves a collection to the database.
	 * 
	 * @param collection the collection to save
	 * @return the saved collection with generated ID if new
	 */
	public Collection saveCollection(Collection collection) {
		logger.info("Saving collection: {}", collection.getInfo().getName());
		return repository.save(collection);
	}

	/**
	 * Deletes a collection by its unique identifier.
	 * 
	 * @param id the unique identifier of the collection to delete
	 */
	public void deleteCollection(String id) {
		logger.info("Deleting collection with ID: {}", id);
		repository.deleteById(id);
	}

	/**
	 * Imports a collection from a JSON file.
	 * 
	 * @param file the multipart file containing the JSON collection
	 * @return the imported and saved collection
	 * @throws IOException              if there's an error reading or parsing the
	 *                                  file
	 * @throws IllegalArgumentException if the file is empty, not JSON, or
	 *                                  collection already exists
	 */
	public Collection importCollectionFromFile(MultipartFile file) throws IOException {
		logger.info("Importing collection from file: {}", file.getOriginalFilename());

		if (file.isEmpty()) {
			throw new IllegalArgumentException("File is empty");
		}

		if (!file.getOriginalFilename().endsWith(".json")) {
			throw new IllegalArgumentException("File must be a valid JSON");
		}

		try {
			// Parse JSON to Collection
			Collection collection = objectMapper.readValue(file.getInputStream(), Collection.class);

			// Validate basic structure
			validateCollection(collection);

			// Check if a collection with the same name already exists
			Optional<Collection> existing = getCollectionByName(collection.getInfo().getName());
			if (existing.isPresent()) {
				logger.warn("Collection with name already exists: {}", collection.getInfo().getName());
				throw new IllegalArgumentException("Collection with this name already exists");
			}

			// Save collection
			Collection savedCollection = saveCollection(collection);
			logger.info("Collection imported successfully: {}", savedCollection.getInfo().getName());

			return savedCollection;

		} catch (IOException e) {
			logger.error("Error parsing JSON file: {}", e.getMessage());
			throw new IOException("Error processing JSON file: " + e.getMessage());
		}
	}

	/**
	 * Imports a collection from a JSON string.
	 * 
	 * @param jsonContent the JSON string containing the collection data
	 * @return the imported and saved collection
	 * @throws IOException              if there's an error parsing the JSON
	 * @throws IllegalArgumentException if the collection already exists
	 */
	public Collection importCollectionFromJson(String jsonContent) throws IOException {
		logger.info("Importing collection from JSON string");

		try {
			Collection collection = objectMapper.readValue(jsonContent, Collection.class);
			validateCollection(collection);

			// Check for duplicates
			Optional<Collection> existing = getCollectionByName(collection.getInfo().getName());
			if (existing.isPresent()) {
				throw new IllegalArgumentException("Collection with this name already exists");
			}

			return saveCollection(collection);

		} catch (IOException e) {
			logger.error("Error parsing JSON: {}", e.getMessage());
			throw new IOException("Error processing JSON: " + e.getMessage());
		}
	}

	/**
	 * Validates the structure of a collection to ensure it meets minimum
	 * requirements.
	 * 
	 * @param collection the collection to validate
	 * @throws IllegalArgumentException if the collection structure is invalid
	 */
	private void validateCollection(Collection collection) {
		if (collection == null) {
			throw new IllegalArgumentException("Collection cannot be null");
		}

		if (collection.getInfo() == null) {
			throw new IllegalArgumentException("Collection must have basic information");
		}

		if (collection.getInfo().getName() == null || collection.getInfo().getName().trim().isEmpty()) {
			throw new IllegalArgumentException("Collection must have a name");
		}

		if (collection.getItem() == null || collection.getItem().isEmpty()) {
			throw new IllegalArgumentException("Collection must have at least one item/request");
		}

		logger.info("Collection validated successfully: {} with {} items", collection.getInfo().getName(),
				collection.getItem().size());
	}

	/**
	 * Retrieves statistics for a specific collection.
	 * 
	 * @param collectionId the unique identifier of the collection
	 * @return CollectionStats object containing collection statistics
	 * @throws IllegalArgumentException if the collection is not found
	 */
	public CollectionStats getCollectionStats(String collectionId) {
		Optional<Collection> collection = getCollectionById(collectionId);

		if (collection.isEmpty()) {
			throw new IllegalArgumentException("Collection not found");
		}

		Collection col = collection.get();

		return CollectionStats.builder().collectionName(col.getInfo().getName()).totalItems(col.getItem().size())
				.description(col.getInfo().getDescription().getContent()).build();
	}

	/**
	 * Inner class for collection statistics. Contains basic information about a
	 * collection including name, item count, and description.
	 */
	public static class CollectionStats {
		private String collectionName;
		private int totalItems;
		private String description;

		/**
		 * Creates a new builder for CollectionStats.
		 * 
		 * @return a new CollectionStatsBuilder instance
		 */
		public static CollectionStatsBuilder builder() {
			return new CollectionStatsBuilder();
		}

		/**
		 * Gets the collection name.
		 * 
		 * @return the collection name
		 */
		public String getCollectionName() {
			return collectionName;
		}

		/**
		 * Gets the total number of items in the collection.
		 * 
		 * @return the total number of items
		 */
		public int getTotalItems() {
			return totalItems;
		}

		/**
		 * Gets the collection description.
		 * 
		 * @return the collection description
		 */
		public String getDescription() {
			return description;
		}

		/**
		 * Builder pattern implementation for CollectionStats.
		 */
		public static class CollectionStatsBuilder {
			private String collectionName;
			private int totalItems;
			private String description;

			/**
			 * Sets the collection name.
			 * 
			 * @param collectionName the collection name
			 * @return this builder instance
			 */
			public CollectionStatsBuilder collectionName(String collectionName) {
				this.collectionName = collectionName;
				return this;
			}

			/**
			 * Sets the total number of items.
			 * 
			 * @param totalItems the total number of items
			 * @return this builder instance
			 */
			public CollectionStatsBuilder totalItems(int totalItems) {
				this.totalItems = totalItems;
				return this;
			}

			/**
			 * Sets the collection description.
			 * 
			 * @param description the collection description
			 * @return this builder instance
			 */
			public CollectionStatsBuilder description(String description) {
				this.description = description;
				return this;
			}

			/**
			 * Builds and returns a new CollectionStats instance.
			 * 
			 * @return a new CollectionStats instance with the configured values
			 */
			public CollectionStats build() {
				CollectionStats stats = new CollectionStats();
				stats.collectionName = this.collectionName;
				stats.totalItems = this.totalItems;
				stats.description = this.description;
				return stats;
			}
		}
	}
}