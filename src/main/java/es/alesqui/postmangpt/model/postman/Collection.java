package es.alesqui.postmangpt.model.postman;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Postman Collection - Root object that contains all collection data
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Collection {
	
	@Field("_class")
	@JsonProperty("_class")
	@Builder.Default
	private String _class = "es.alesqui.postmangpt.model.postman.Collection";

	/**
	 * Collection information (name, description, version, etc.)
	 */
	@Field("info")
	@JsonProperty("info")
	private Info info;

	/**
	 * List of items (requests, folders) in the collection
	 */
	@Field("item")
	@JsonProperty("item")
	@Builder.Default
	private List<Item> item = new ArrayList<>();

	/**
	 * List of events that can be executed for the collection
	 */
	@Field("event")
	@JsonProperty("event")
	private List<Event> event;

	/**
	 * List of variables defined at collection level
	 */
	@Field("variable")
	@JsonProperty("variable")
	private List<Variable> variable;

	/**
	 * Authentication configuration for the collection
	 */
	@Field("auth")
	@JsonProperty("auth")
	private Auth auth;

	/**
	 * Protocol profile behavior configuration
	 */
	@Field("protocolProfileBehavior")
	@JsonProperty("protocolProfileBehavior")
	private ProtocolProfileBehavior protocolProfileBehavior;

	// Factory methods

	/**
	 * Creates a new empty collection with basic info
	 * 
	 * @param name Collection name
	 * @return New Collection instance
	 */
	public static Collection create(String name) {
		return Collection.builder()
				.info(Info.builder().name(name).postmanId(UUID.randomUUID().toString())
						.schema("https://schema.getpostman.com/json/collection/v2.1.0/collection.json")
						.version(Version.builder().major(1).minor(0).patch(0).build()).build())
				.build();
	}

	/**
	 * Creates a new collection with name and description
	 * 
	 * @param name        Collection name
	 * @param description Collection description
	 * @return New Collection instance
	 */
	public static Collection create(String name, String description) {
		return Collection.builder()
				.info(Info.builder().name(name).description(Description.create(description))
						.postmanId(UUID.randomUUID().toString())
						.schema("https://schema.getpostman.com/json/collection/v2.1.0/collection.json")
						.version(Version.builder().major(1).minor(0).patch(0).build()).build())
				.build();
	}

	/**
	 * Creates a new collection with complete info
	 * 
	 * @param info Collection info
	 * @return New Collection instance
	 */
	public static Collection create(Info info) {
		return Collection.builder().info(info).build();
	}
	

	// Fluent API methods

	/**
	 * Sets the collection info
	 * 
	 * @param info Collection info
	 * @return this instance for method chaining
	 */
	public Collection withInfo(Info info) {
		this.info = info;
		return this;
	}

	/**
	 * Sets the collection name
	 * 
	 * @param name Collection name
	 * @return this instance for method chaining
	 */
	public Collection withName(String name) {
		if (this.info == null) {
			this.info = Info.builder().build();
		}
		this.info.setName(name);
		return this;
	}

	/**
	 * Sets the collection description
	 * 
	 * @param description Collection description
	 * @return this instance for method chaining
	 */
	public Collection withDescription(String description) {
		if (this.info == null) {
			this.info = Info.builder().build();
		}
		this.info.setDescription(Description.create(description));
		return this;
	}

	/**
	 * Sets the collection authentication
	 * 
	 * @param auth Authentication configuration
	 * @return this instance for method chaining
	 */
	public Collection withAuth(Auth auth) {
		this.auth = auth;
		return this;
	}

	/**
	 * Sets the protocol profile behavior
	 * 
	 * @param behavior Protocol profile behavior
	 * @return this instance for method chaining
	 */
	public Collection withProtocolProfileBehavior(ProtocolProfileBehavior behavior) {
		this.protocolProfileBehavior = behavior;
		return this;
	}

	/**
	 * Adds an item to the collection
	 * 
	 * @param item Item to add
	 * @return this instance for method chaining
	 */
	public Collection addItem(Item item) {
		if (this.item == null) {
			this.item = new ArrayList<>();
		}
		this.item.add(item);
		return this;
	}

	/**
	 * Adds multiple items to the collection
	 * 
	 * @param items Items to add
	 * @return this instance for method chaining
	 */
	public Collection addItems(List<Item> items) {
		if (items != null) {
			if (this.item == null) {
				this.item = new ArrayList<>();
			}
			this.item.addAll(items);
		}
		return this;
	}

	/**
	 * Adds a request item to the collection
	 * 
	 * @param name    Request name
	 * @param request Request object
	 * @return this instance for method chaining
	 */
	public Collection addRequest(String name, Request request) {
		Item item = Item.builder().name(name).request(request).build();
		return addItem(item);
	}

	/**
	 * Adds a folder to the collection
	 * 
	 * @param name Folder name
	 * @return this instance for method chaining
	 */
	public Collection addFolder(String name) {
		Item folder = Item.builder().name(name).item(new ArrayList<>()).build();
		return addItem(folder);
	}

	/**
	 * Adds a folder with items to the collection
	 * 
	 * @param name  Folder name
	 * @param items Items in the folder
	 * @return this instance for method chaining
	 */
	public Collection addFolder(String name, List<Item> items) {
		Item folder = Item.builder().name(name).item(items != null ? items : new ArrayList<>()).build();
		return addItem(folder);
	}

	/**
	 * Adds a variable to the collection
	 * 
	 * @param variable Variable to add
	 * @return this instance for method chaining
	 */
	public Collection addVariable(Variable variable) {
		if (this.variable == null) {
			this.variable = new ArrayList<>();
		}
		this.variable.add(variable);
		return this;
	}

	/**
	 * Adds a variable to the collection
	 * 
	 * @param key   Variable key
	 * @param value Variable value
	 * @return this instance for method chaining
	 */
	public Collection addVariable(String key, String value) {
		Variable var = Variable.builder().key(key).value(value).build();
		return addVariable(var);
	}

	/**
	 * Adds a variable to the collection with description
	 * 
	 * @param key         Variable key
	 * @param value       Variable value
	 * @param description Variable description
	 * @return this instance for method chaining
	 */
	public Collection addVariable(String key, String value, String description) {
		Variable var = Variable.builder().key(key).value(value).description(Description.create(description)).build();
		return addVariable(var);
	}

	/**
	 * Adds multiple variables to the collection
	 * 
	 * @param variables Variables to add
	 * @return this instance for method chaining
	 */
	public Collection addVariables(List<Variable> variables) {
		if (variables != null) {
			if (this.variable == null) {
				this.variable = new ArrayList<>();
			}
			this.variable.addAll(variables);
		}
		return this;
	}

	/**
	 * Adds an event to the collection
	 * 
	 * @param event Event to add
	 * @return this instance for method chaining
	 */
	public Collection addEvent(Event event) {
		if (this.event == null) {
			this.event = new ArrayList<>();
		}
		this.event.add(event);
		return this;
	}

	/**
	 * Adds a pre-request script to the collection
	 * 
	 * @param script Script content
	 * @return this instance for method chaining
	 */
	public Collection addPreRequestScript(String script) {
		Event preRequestEvent = Event.builder().listen("prerequest")
				.script(Script.builder().type("text/javascript").exec(List.of(script.split("\n"))).build()).build();
		return addEvent(preRequestEvent);
	}

	/**
	 * Adds a test script to the collection
	 * 
	 * @param script Script content
	 * @return this instance for method chaining
	 */
	public Collection addTestScript(String script) {
		Event testEvent = Event.builder().listen("test")
				.script(Script.builder().type("text/javascript").exec(List.of(script.split("\n"))).build()).build();
		return addEvent(testEvent);
	}

	// Query methods

	/**
	 * Gets the collection name
	 * 
	 * @return Collection name
	 */
	public String getName() {
		return info != null ? info.getName() : null;
	}

	/**
	 * Gets the collection description
	 * 
	 * @return Collection description
	 */
	public String getDescription() {
		return info != null && info.getDescription() != null ? info.getDescription().getContent() : null;
	}

	/**
	 * Gets the Postman collection ID (from info.postmanId)
	 * 
	 * @return Postman collection ID
	 */
	public String getPostmanId() {
		return info != null ? info.getPostmanId() : null;
	}

	/**
	 * Gets the collection schema
	 * 
	 * @return Collection schema
	 */
	public String getSchema() {
		return info != null ? info.getSchema() : null;
	}

	/**
	 * Gets the number of items in the collection
	 * 
	 * @return Number of items
	 */
	public int getItemCount() {
		return item != null ? item.size() : 0;
	}

	/**
	 * Gets the number of variables in the collection
	 * 
	 * @return Number of variables
	 */
	public int getVariableCount() {
		return variable != null ? variable.size() : 0;
	}

	/**
	 * Gets the number of events in the collection
	 * 
	 * @return Number of events
	 */
	public int getEventCount() {
		return event != null ? event.size() : 0;
	}

	/**
	 * Checks if the collection has authentication configured
	 * 
	 * @return true if auth is configured
	 */
	public boolean hasAuth() {
		return auth != null;
	}

	/**
	 * Checks if the collection has protocol profile behavior configured
	 * 
	 * @return true if protocol profile behavior is configured
	 */
	public boolean hasProtocolProfileBehavior() {
		return protocolProfileBehavior != null;
	}

	/**
	 * Checks if the collection has variables
	 * 
	 * @return true if variables are defined
	 */
	public boolean hasVariables() {
		return variable != null && !variable.isEmpty();
	}

	/**
	 * Checks if the collection has events
	 * 
	 * @return true if events are defined
	 */
	public boolean hasEvents() {
		return event != null && !event.isEmpty();
	}

	/**
	 * Checks if the collection has items
	 * 
	 * @return true if items are defined
	 */
	public boolean hasItems() {
		return item != null && !item.isEmpty();
	}

	/**
	 * Checks if the collection is empty (no items)
	 * 
	 * @return true if collection is empty
	 */
	public boolean isEmpty() {
		return !hasItems();
	}

	/**
	 * Finds an item by name
	 * 
	 * @param name Item name
	 * @return Item if found, null otherwise
	 */
	public Item findItemByName(String name) {
		if (item == null || name == null)
			return null;

		return item.stream().filter(i -> name.equals(i.getName())).findFirst().orElse(null);
	}

	/**
	 * Finds all items with a specific name pattern
	 * 
	 * @param namePattern Name pattern (regex)
	 * @return List of matching items
	 */
	public List<Item> findItemsByNamePattern(String namePattern) {
		if (item == null || namePattern == null)
			return new ArrayList<>();

		return item.stream().filter(i -> i.getName() != null && i.getName().matches(namePattern)).toList();
	}

	/**
	 * Finds a variable by key
	 * 
	 * @param key Variable key
	 * @return Variable if found, null otherwise
	 */
	public Variable findVariableByKey(String key) {
		if (variable == null || key == null)
			return null;

		return variable.stream().filter(v -> key.equals(v.getKey())).findFirst().orElse(null);
	}

	/**
	 * Gets all request items (non-folder items)
	 * 
	 * @return List of request items
	 */
	public List<Item> getRequestItems() {
		if (item == null)
			return new ArrayList<>();

		return item.stream().filter(i -> i.getRequest() != null).toList();
	}

	/**
	 * Gets all folder items
	 * 
	 * @return List of folder items
	 */
	public List<Item> getFolderItems() {
		if (item == null)
			return new ArrayList<>();

		return item.stream().filter(i -> i.getItem() != null).toList();
	}

	/**
	 * Gets all items recursively (including items in folders)
	 * 
	 * @return List of all items
	 */
	public List<Item> getAllItemsRecursive() {
		List<Item> allItems = new ArrayList<>();
		if (item != null) {
			collectItemsRecursive(item, allItems);
		}
		return allItems;
	}

	private void collectItemsRecursive(List<Item> items, List<Item> collector) {
		for (Item item : items) {
			collector.add(item);
			if (item.getItem() != null) {
				collectItemsRecursive(item.getItem(), collector);
			}
		}
	}

	/**
	 * Gets all request items recursively
	 * 
	 * @return List of all request items
	 */
	public List<Item> getAllRequestItemsRecursive() {
		return getAllItemsRecursive().stream().filter(i -> i.getRequest() != null).toList();
	}

	/**
	 * Counts total requests in the collection (including in folders)
	 * 
	 * @return Total number of requests
	 */
	public int getTotalRequestCount() {
		return getAllRequestItemsRecursive().size();
	}

	/**
	 * Counts total folders in the collection (including nested folders)
	 * 
	 * @return Total number of folders
	 */
	public int getTotalFolderCount() {
		return getAllItemsRecursive().stream().mapToInt(i -> i.getItem() != null ? 1 : 0).sum();
	}

	// Utility methods

	/**
	 * Removes an item from the collection
	 * 
	 * @param itemName Name of the item to remove
	 * @return true if item was removed
	 */
	public boolean removeItem(String itemName) {
		if (item == null || itemName == null)
			return false;

		return item.removeIf(i -> itemName.equals(i.getName()));
	}

	/**
	 * Removes a variable from the collection
	 * 
	 * @param variableKey Key of the variable to remove
	 * @return true if variable was removed
	 */
	public boolean removeVariable(String variableKey) {
		if (variable == null || variableKey == null)
			return false;

		return variable.removeIf(v -> variableKey.equals(v.getKey()));
	}

	/**
	 * Clears all items from the collection
	 * 
	 * @return this instance for method chaining
	 */
	public Collection clearItems() {
		if (this.item != null) {
			this.item.clear();
		}
		return this;
	}

	/**
	 * Clears all variables from the collection
	 * 
	 * @return this instance for method chaining
	 */
	public Collection clearVariables() {
		if (this.variable != null) {
			this.variable.clear();
		}
		return this;
	}

	/**
	 * Clears all events from the collection
	 * 
	 * @return this instance for method chaining
	 */
	public Collection clearEvents() {
		if (this.event != null) {
			this.event.clear();
		}
		return this;
	}

	/**
	 * Creates a copy of this collection
	 * 
	 * @return Copy of this collection
	 */
	public Collection copy() {
		return Collection.builder().info(this.info != null ? this.info.copy() : null)
				.item(this.item != null ? this.item.stream().map(Item::copy).collect(Collectors.toList()) : null)
				.variable(
						this.variable != null ? this.variable.stream().map(Variable::copy).collect(Collectors.toList())
								: null)
				.event(this.event != null ? this.event.stream().map(Event::copy).collect(Collectors.toList()) : null)
				.auth(this.auth != null ? this.auth.copy() : null).protocolProfileBehavior(this.protocolProfileBehavior)
				.build();
	}

	/**
	 * Validates the collection structure
	 * 
	 * @return true if collection is valid
	 */
	public boolean isValid() {
		return info != null && info.isValid();
	}

	/**
	 * Gets collection summary
	 * 
	 * @return Collection summary as string
	 */
	public String getSummary() {
		return String.format(
				"Collection: %s\n" + "Description: %s\n" + "Items: %d (Requests: %d, Folders: %d)\n" + "Variables: %d\n"
						+ "Events: %d\n" + "Auth: %s\n" + "Schema: %s",
				getName(), getDescription() != null ? getDescription() : "None", getItemCount(), getTotalRequestCount(),
				getTotalFolderCount(), getVariableCount(), getEventCount(), hasAuth() ? "Yes" : "No", getSchema());
	}

	/**
	 * Sets default values for missing fields
	 * 
	 * @return this instance for method chaining
	 */
	public Collection withDefaults() {
		if (this.info == null) {
			this.info = Info.builder().name("Unnamed Collection")
					.schema("https://schema.getpostman.com/json/collection/v2.1.0/collection.json")
					.postmanId(UUID.randomUUID().toString()).version(Version.builder().major(1).minor(0).patch(0).build()).build();
		} else {
			this.info.withDefaults();
		}

		if (this.item == null) {
			this.item = new ArrayList<>();
		}

		return this;
	}

	/**
	 * Organizes items by grouping requests into folders based on a criteria
	 * 
	 * @param groupBy Function to determine folder name for each item
	 * @return this instance for method chaining
	 */
	public Collection organizeIntoFolders(java.util.function.Function<Item, String> groupBy) {
		if (item == null || item.isEmpty())
			return this;

		java.util.Map<String, List<Item>> groups = new java.util.HashMap<>();
		List<Item> ungrouped = new ArrayList<>();

		for (Item currentItem : item) {
			if (currentItem.getRequest() != null) {
				String folderName = groupBy.apply(currentItem);
				if (folderName != null && !folderName.trim().isEmpty()) {
					groups.computeIfAbsent(folderName, k -> new ArrayList<>()).add(currentItem);
				} else {
					ungrouped.add(currentItem);
				}
			} else {
				ungrouped.add(currentItem); // Keep existing folders
			}
		}

		// Clear current items and rebuild with folders
		this.item.clear();

		// Add grouped items as folders
		for (java.util.Map.Entry<String, List<Item>> entry : groups.entrySet()) {
			addFolder(entry.getKey(), entry.getValue());
		}

		// Add ungrouped items
		this.item.addAll(ungrouped);

		return this;
	}

	/**
	 * Sorts items alphabetically by name
	 * 
	 * @return this instance for method chaining
	 */
	public Collection sortItems() {
		if (item != null) {
			item.sort((a, b) -> {
				String nameA = a.getName() != null ? a.getName() : "";
				String nameB = b.getName() != null ? b.getName() : "";
				return nameA.compareToIgnoreCase(nameB);
			});
		}
		return this;
	}

	/**
	 * Sorts variables alphabetically by key
	 * 
	 * @return this instance for method chaining
	 */
	public Collection sortVariables() {
		if (variable != null) {
			variable.sort((a, b) -> {
				String keyA = a.getKey() != null ? a.getKey() : "";
				String keyB = b.getKey() != null ? b.getKey() : "";
				return keyA.compareToIgnoreCase(keyB);
			});
		}
		return this;
	}

	/**
	 * Gets statistics about the collection
	 * 
	 * @return Collection statistics
	 */
	public CollectionStats getStats() {
		return CollectionStats.builder().totalItems(getItemCount()).totalRequests(getTotalRequestCount())
				.totalFolders(getTotalFolderCount()).totalVariables(getVariableCount()).totalEvents(getEventCount())
				.hasAuth(hasAuth()).hasProtocolProfileBehavior(hasProtocolProfileBehavior()).build();
	}

	/**
	 * Collection statistics helper class
	 */
	@Data
	@Builder
	public static class CollectionStats {
		private int totalItems;
		private int totalRequests;
		private int totalFolders;
		private int totalVariables;
		private int totalEvents;
		private boolean hasAuth;
		private boolean hasProtocolProfileBehavior;

		@Override
		public String toString() {
			return String.format("Collection Stats:\n" + "- Items: %d\n" + "- Requests: %d\n" + "- Folders: %d\n"
					+ "- Variables: %d\n" + "- Events: %d\n" + "- Has Auth: %s\n" + "- Has Protocol Behavior: %s",
					totalItems, totalRequests, totalFolders, totalVariables, totalEvents, hasAuth ? "Yes" : "No",
					hasProtocolProfileBehavior ? "Yes" : "No");
		}
	}
}