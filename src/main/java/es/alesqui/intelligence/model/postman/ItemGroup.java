package es.alesqui.intelligence.model.postman;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

import org.springframework.data.mongodb.core.mapping.Field;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;

/**
 * One of the primary goals of Postman is to organize the development of APIs.
 * To this end, it is necessary to be able to group requests together. This can
 * be achieved using 'Folders'. A folder just is an ordered set of requests.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ItemGroup {

	/**
	 * A unique ID that is used to identify collections internally
	 */
	@Field("id")
	@JsonProperty("id")
	private String id;

	/**
	 * A human readable identifier for the current item group.
	 */
	@Field("name")
	@JsonProperty("name")
	private String name;

	/**
	 * A Description can be a raw text, or be an object, which holds the description
	 * along with its format.
	 */
	@Field("description")
	@JsonProperty("description")
	private Description description;

	/**
	 * A variable list that is associated with an entity.
	 */
	@Field("variable")
	@JsonProperty("variable")
	@Builder.Default
	private List<Variable> variable = new ArrayList<>();

	/**
	 * Postman allows you to configure scripts to run when specific events occur.
	 * These scripts are stored here, and can be referenced in the collection by
	 * their ID.
	 */
	@Field("event")
	@JsonProperty("event")
	@Builder.Default
	private List<Event> event = new ArrayList<>();

	/**
	 * Items are entities which contain an actual HTTP request, and sample responses
	 * attached to it. Folders may contain many items.
	 */
	@Field("item")
	@JsonProperty("item")
	@Builder.Default
	private List<Object> item = new ArrayList<>();

	/**
	 * Represents authentication helpers provided by Postman
	 */
	@Field("auth")
	@JsonProperty("auth")
	private Auth auth;

	/**
	 * Set of configurations used to alter the usual behavior of sending the request
	 */
	@Field("protocolProfileBehavior")
	@JsonProperty("protocolProfileBehavior")
	private ProtocolProfileBehavior protocolProfileBehavior;

	/**
	 * Constructor for basic item group with name only
	 * 
	 * @param name Group name
	 */
	public ItemGroup(String name) {
		this.name = name;
		this.variable = new ArrayList<>();
		this.event = new ArrayList<>();
		this.item = new ArrayList<>();
	}

	/**
	 * Constructor for item group with name and description
	 * 
	 * @param name        Group name
	 * @param description Group description
	 */
	public ItemGroup(String name, String description) {
		this.name = name;
		this.description = new Description(description);
		this.variable = new ArrayList<>();
		this.event = new ArrayList<>();
		this.item = new ArrayList<>();
	}

	/**
	 * Creates a simple item group with name
	 * 
	 * @param name Group name
	 * @return ItemGroup instance
	 */
	public static ItemGroup of(String name) {
		return new ItemGroup(name);
	}

	/**
	 * Creates an item group with name and description
	 * 
	 * @param name        Group name
	 * @param description Group description
	 * @return ItemGroup instance
	 */
	public static ItemGroup of(String name, String description) {
		return new ItemGroup(name, description);
	}

	/**
	 * Creates a folder (alias for ItemGroup)
	 * 
	 * @param name Folder name
	 * @return ItemGroup instance
	 */
	public static ItemGroup folder(String name) {
		return ItemGroup.of(name);
	}

	/**
	 * Creates a folder with description
	 * 
	 * @param name        Folder name
	 * @param description Folder description
	 * @return ItemGroup instance
	 */
	public static ItemGroup folder(String name, String description) {
		return ItemGroup.of(name, description);
	}

	// Fluent API methods

	/**
	 * Sets the description and returns this instance
	 * 
	 * @param description Description text
	 * @return this instance for method chaining
	 */
	public ItemGroup withDescription(String description) {
		this.description = new Description(description);
		return this;
	}

	/**
	 * Sets the description object and returns this instance
	 * 
	 * @param description Description object
	 * @return this instance for method chaining
	 */
	public ItemGroup withDescription(Description description) {
		this.description = description;
		return this;
	}

	/**
	 * Sets the authentication and returns this instance
	 * 
	 * @param auth Authentication configuration
	 * @return this instance for method chaining
	 */
	public ItemGroup withAuth(Auth auth) {
		this.auth = auth;
		return this;
	}

	/**
	 * Adds an item and returns this instance
	 * 
	 * @param item Item to add
	 * @return this instance for method chaining
	 */
	public ItemGroup addItem(Item item) {
		if (this.item == null) {
			this.item = new ArrayList<>();
		}
		this.item.add(item);
		return this;
	}

	/**
	 * Adds an item group (subfolder) and returns this instance
	 * 
	 * @param itemGroup ItemGroup to add
	 * @return this instance for method chaining
	 */
	public ItemGroup addItemGroup(ItemGroup itemGroup) {
		if (this.item == null) {
			this.item = new ArrayList<>();
		}
		this.item.add(itemGroup);
		return this;
	}

	/**
	 * Adds a subfolder and returns this instance
	 * 
	 * @param folder Subfolder to add
	 * @return this instance for method chaining
	 */
	public ItemGroup addFolder(ItemGroup folder) {
		return addItemGroup(folder);
	}

	/**
	 * Adds multiple items and returns this instance
	 * 
	 * @param items Items to add
	 * @return this instance for method chaining
	 */
	public ItemGroup addItems(Item... items) {
		if (this.item == null) {
			this.item = new ArrayList<>();
		}
		for (Item item : items) {
			this.item.add(item);
		}
		return this;
	}

	/**
	 * Adds a variable and returns this instance
	 * 
	 * @param variable Variable to add
	 * @return this instance for method chaining
	 */
	public ItemGroup addVariable(Variable variable) {
		if (this.variable == null) {
			this.variable = new ArrayList<>();
		}
		this.variable.add(variable);
		return this;
	}

	/**
	 * Adds a variable with key and value
	 * 
	 * @param key   Variable key
	 * @param value Variable value
	 * @return this instance for method chaining
	 */
	public ItemGroup addVariable(String key, String value) {
		return addVariable(Variable.of(key, value));
	}

	/**
	 * Adds an event and returns this instance
	 * 
	 * @param event Event to add
	 * @return this instance for method chaining
	 */
	public ItemGroup addEvent(Event event) {
		if (this.event == null) {
			this.event = new ArrayList<>();
		}
		this.event.add(event);
		return this;
	}

	/**
	 * Adds a pre-request script
	 * 
	 * @param script Script content
	 * @return this instance for method chaining
	 */
	public ItemGroup addPreRequestScript(String script) {
		return addEvent(Event.preRequest(script));
	}

	/**
	 * Adds a test script
	 * 
	 * @param script Script content
	 * @return this instance for method chaining
	 */
	public ItemGroup addTestScript(String script) {
		return addEvent(Event.test(script));
	}

	/**
	 * Sets the protocol profile behavior
	 * 
	 * @param behavior Protocol profile behavior
	 * @return this instance for method chaining
	 */
	public ItemGroup withProtocolProfileBehavior(ProtocolProfileBehavior behavior) {
		this.protocolProfileBehavior = behavior;
		return this;
	}

	// Utility methods

	/**
	 * Checks if this group has items
	 * 
	 * @return true if has items
	 */
	public boolean hasItems() {
		return item != null && !item.isEmpty();
	}

	/**
	 * Checks if this group has variables
	 * 
	 * @return true if has variables
	 */
	public boolean hasVariables() {
		return variable != null && !variable.isEmpty();
	}

	/**
	 * Checks if this group has events
	 * 
	 * @return true if has events
	 */
	public boolean hasEvents() {
		return event != null && !event.isEmpty();
	}

	/**
	 * Checks if this group has authentication
	 * 
	 * @return true if has authentication
	 */
	public boolean hasAuth() {
		return auth != null;
	}

	/**
	 * Gets the number of items in this group
	 * 
	 * @return Number of items
	 */
	public int getItemCount() {
		return item != null ? item.size() : 0;
	}

	/**
	 * Gets all items (both Item and ItemGroup instances)
	 * 
	 * @return List of items
	 */
	public List<Object> getAllItems() {
		return item != null ? new ArrayList<>(item) : new ArrayList<>();
	}

	/**
	 * Gets only Item instances (filters out ItemGroup)
	 * 
	 * @return List of Item instances
	 */
	public List<Item> getItems() {
		List<Item> items = new ArrayList<>();
		if (item != null) {
			for (Object obj : item) {
				if (obj instanceof Item) {
					items.add((Item) obj);
				}
			}
		}
		return items;
	}

	/**
	 * Gets only ItemGroup instances (subfolders)
	 * 
	 * @return List of ItemGroup instances
	 */
	public List<ItemGroup> getSubfolders() {
		List<ItemGroup> subfolders = new ArrayList<>();
		if (item != null) {
			for (Object obj : item) {
				if (obj instanceof ItemGroup) {
					subfolders.add((ItemGroup) obj);
				}
			}
		}
		return subfolders;
	}
}