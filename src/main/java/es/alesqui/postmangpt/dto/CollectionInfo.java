package es.alesqui.postmangpt.dto;

/**
 * Class for collection statistics. Contains basic information about a
 * collection including name, item count, and description.
 */
public class CollectionInfo {
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
		public CollectionInfo build() {
			CollectionInfo stats = new CollectionInfo();
			stats.collectionName = this.collectionName;
			stats.totalItems = this.totalItems;
			stats.description = this.description;
			return stats;
		}
	}
}