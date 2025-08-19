package es.alesqui.intelligence.model.postman;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.mongodb.core.mapping.Field;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import es.alesqui.intelligence.model.postman.enums.RequestMethod;


/**
 * Items are the basic unit for a Postman collection. You can think of them as
 * corresponding to a single API endpoint. Each Item has one request and may
 * have multiple API responses associated with it. Items can also represent
 * folders that contain other items.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Item {

	/**
	 * A unique ID that is used to identify collections internally
	 */
	@Field("id")
	@JsonProperty("id")
	private String id;

	/**
	 * A human readable identifier for the current item.
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
	 * The request object represents an HTTP request. This field is null for folder
	 * items.
	 */
	@Field("request")
	@JsonProperty("request")
	private Request request;

	/**
	 * A response represents an HTTP response from an API endpoint.
	 */
	@Field("response")
	@JsonProperty("response")
	@Builder.Default
	private List<Response> response = new ArrayList<>();

	/**
	 * List of items contained in this item (for folders). This field is null for
	 * request items.
	 */
	@Field("item")
	@JsonProperty("item")
	private List<Item> item;

	/**
	 * Set of configurations used to alter the usual behavior of sending the request
	 */
	@Field("protocolProfileBehavior")
	@JsonProperty("protocolProfileBehavior")
	private ProtocolProfileBehavior protocolProfileBehavior;

	/**
	 * Constructor for basic item with name only
	 * 
	 * @param name Item name
	 */
	public Item(String name) {
		this.name = name;
		this.id = UUID.randomUUID().toString();
		this.variable = new ArrayList<>();
		this.event = new ArrayList<>();
		this.response = new ArrayList<>();
	}

	/**
	 * Constructor for item with name and request
	 * 
	 * @param name    Item name
	 * @param request HTTP request
	 */
	public Item(String name, Request request) {
		this.name = name;
		this.id = UUID.randomUUID().toString();
		this.request = request;
		this.variable = new ArrayList<>();
		this.event = new ArrayList<>();
		this.response = new ArrayList<>();
	}

	/**
	 * Constructor for complete item
	 * 
	 * @param name        Item name
	 * @param description Item description
	 * @param request     HTTP request
	 */
	public Item(String name, String description, Request request) {
		this.name = name;
		this.id = UUID.randomUUID().toString();
		this.description = new Description(description);
		this.request = request;
		this.variable = new ArrayList<>();
		this.event = new ArrayList<>();
		this.response = new ArrayList<>();
	}

	/**
	 * Constructor for folder item
	 * 
	 * @param name  Folder name
	 * @param items Items in the folder
	 */
	public Item(String name, List<Item> items) {
		this.name = name;
		this.id = UUID.randomUUID().toString();
		this.item = items != null ? items : new ArrayList<>();
		this.variable = new ArrayList<>();
		this.event = new ArrayList<>();
		this.response = new ArrayList<>();
	}

	// Factory methods for requests

	/**
	 * Creates a simple item with name and request
	 * 
	 * @param name    Item name
	 * @param request HTTP request
	 * @return Item instance
	 */
	public static Item of(String name, Request request) {
		return new Item(name, request);
	}

	/**
	 * Creates an item with name, description and request
	 * 
	 * @param name        Item name
	 * @param description Item description
	 * @param request     HTTP request
	 * @return Item instance
	 */
	public static Item of(String name, String description, Request request) {
		return new Item(name, description, request);
	}

	/**
	 * Creates a folder item
	 * 
	 * @param name Folder name
	 * @return Folder item instance
	 */
	public static Item folder(String name) {
		return Item.builder().name(name).id(UUID.randomUUID().toString()).item(new ArrayList<>())
				.variable(new ArrayList<>()).event(new ArrayList<>()).response(new ArrayList<>()).build();
	}

	/**
	 * Creates a folder item with description
	 * 
	 * @param name        Folder name
	 * @param description Folder description
	 * @return Folder item instance
	 */
	public static Item folder(String name, String description) {
		return Item.builder().name(name).id(UUID.randomUUID().toString()).description(Description.create(description))
				.item(new ArrayList<>()).variable(new ArrayList<>()).event(new ArrayList<>())
				.response(new ArrayList<>()).build();
	}

	/**
	 * Creates a folder item with items
	 * 
	 * @param name  Folder name
	 * @param items Items in the folder
	 * @return Folder item instance
	 */
	public static Item folder(String name, List<Item> items) {
		return Item.builder().name(name).id(UUID.randomUUID().toString())
				.item(items != null ? items : new ArrayList<>()).variable(new ArrayList<>()).event(new ArrayList<>())
				.response(new ArrayList<>()).build();
	}

	/**
	 * Creates a folder item with description and items
	 * 
	 * @param name        Folder name
	 * @param description Folder description
	 * @param items       Items in the folder
	 * @return Folder item instance
	 */
	public static Item folder(String name, String description, List<Item> items) {
		return Item.builder().name(name).id(UUID.randomUUID().toString()).description(Description.create(description))
				.item(items != null ? items : new ArrayList<>()).variable(new ArrayList<>()).event(new ArrayList<>())
				.response(new ArrayList<>()).build();
	}

	// Factory methods for HTTP requests

	/**
	 * Creates a GET request item
	 * 
	 * @param name Item name
	 * @param url  Request URL
	 * @return Item instance with GET request
	 */
	public static Item get(String name, String url) {
		return Item.of(name, Request.get(url));
	}

	/**
	 * Creates a POST request item
	 * 
	 * @param name Item name
	 * @param url  Request URL
	 * @return Item instance with POST request
	 */
	public static Item post(String name, String url) {
		return Item.of(name, Request.post(url));
	}

	/**
	 * Creates a PUT request item
	 * 
	 * @param name Item name
	 * @param url  Request URL
	 * @return Item instance with PUT request
	 */
	public static Item put(String name, String url) {
		return Item.of(name, Request.put(url));
	}

	/**
	 * Creates a DELETE request item
	 * 
	 * @param name Item name
	 * @param url  Request URL
	 * @return Item instance with DELETE request
	 */
	public static Item delete(String name, String url) {
		return Item.of(name, Request.delete(url));
	}

	/**
	 * Creates a PATCH request item
	 * 
	 * @param name Item name
	 * @param url  Request URL
	 * @return Item instance with PATCH request
	 */
	public static Item patch(String name, String url) {
		return Item.of(name, Request.patch(url));
	}

	/**
	 * Creates a HEAD request item
	 * 
	 * @param name Item name
	 * @param url  Request URL
	 * @return Item instance with HEAD request
	 */
	public static Item head(String name, String url) {
		return Item.of(name, Request.head(url));
	}

	/**
	 * Creates an OPTIONS request item
	 * 
	 * @param name Item name
	 * @param url  Request URL
	 * @return Item instance with OPTIONS request
	 */
	public static Item options(String name, String url) {
		return Item.of(name, Request.options(url));
	}

	// Fluent API methods

	/**
	 * Sets the ID and returns this instance
	 * 
	 * @param id Item ID
	 * @return this instance for method chaining
	 */
	public Item withId(String id) {
		this.id = id;
		return this;
	}

	/**
	 * Sets the description and returns this instance
	 * 
	 * @param description Description text
	 * @return this instance for method chaining
	 */
	public Item withDescription(String description) {
		this.description = Description.create(description);
		return this;
	}

	/**
	 * Sets the description object and returns this instance
	 * 
	 * @param description Description object
	 * @return this instance for method chaining
	 */
	public Item withDescription(Description description) {
		this.description = description;
		return this;
	}

	/**
	 * Sets the request and returns this instance
	 * 
	 * @param request Request object
	 * @return this instance for method chaining
	 */
	public Item withRequest(Request request) {
		this.request = request;
		return this;
	}

	/**
	 * Adds a variable and returns this instance
	 * 
	 * @param variable Variable to add
	 * @return this instance for method chaining
	 */
	public Item addVariable(Variable variable) {
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
	public Item addVariable(String key, String value) {
		return addVariable(Variable.of(key, value));
	}

	/**
	 * Adds multiple variables
	 * 
	 * @param variables Variables to add
	 * @return this instance for method chaining
	 */
	public Item addVariables(List<Variable> variables) {
		if (variables != null) {
			if (this.variable == null) {
				this.variable = new ArrayList<>();
			}
			this.variable.addAll(variables);
		}
		return this;
	}

	/**
	 * Adds an event and returns this instance
	 * 
	 * @param event Event to add
	 * @return this instance for method chaining
	 */
	public Item addEvent(Event event) {
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
	public Item addPreRequestScript(String script) {
		return addEvent(Event.preRequest(script));
	}

	/**
	 * Adds a test script
	 * 
	 * @param script Script content
	 * @return this instance for method chaining
	 */
	public Item addTestScript(String script) {
		return addEvent(Event.test(script));
	}

	/**
	 * Adds a response and returns this instance
	 * 
	 * @param response Response to add
	 * @return this instance for method chaining
	 */
	public Item addResponse(Response response) {
		if (this.response == null) {
			this.response = new ArrayList<>();
		}
		this.response.add(response);
		return this;
	}

	/**
	 * Adds multiple responses
	 * 
	 * @param responses Responses to add
	 * @return this instance for method chaining
	 */
	public Item addResponses(List<Response> responses) {
		if (responses != null) {
			if (this.response == null) {
				this.response = new ArrayList<>();
			}
			this.response.addAll(responses);
		}
		return this;
	}

	/**
	 * Adds an item to this folder item
	 * 
	 * @param item Item to add
	 * @return this instance for method chaining
	 */
	public Item addItem(Item item) {
		if (this.item == null) {
			this.item = new ArrayList<>();
		}
		this.item.add(item);
		return this;
	}

	/**
	 * Adds multiple items to this folder item
	 * 
	 * @param items Items to add
	 * @return this instance for method chaining
	 */
	public Item addItems(List<Item> items) {
		if (items != null) {
			if (this.item == null) {
				this.item = new ArrayList<>();
			}
			this.item.addAll(items);
		}
		return this;
	}

	/**
	 * Adds a request item to this folder
	 * 
	 * @param name    Request name
	 * @param request Request object
	 * @return this instance for method chaining
	 */
	public Item addRequest(String name, Request request) {
		return addItem(Item.of(name, request));
	}

	/**
	 * Adds a subfolder to this folder
	 * 
	 * @param name Subfolder name
	 * @return this instance for method chaining
	 */
	public Item addFolder(String name) {
		return addItem(Item.folder(name));
	}

	/**
	 * Sets the protocol profile behavior
	 * 
	 * @param behavior Protocol profile behavior
	 * @return this instance for method chaining
	 */
	public Item withProtocolProfileBehavior(ProtocolProfileBehavior behavior) {
		this.protocolProfileBehavior = behavior;
		return this;
	}

	// Query methods

	/**
	 * Checks if this item has a request
	 * 
	 * @return true if request is not null
	 */
	public boolean hasRequest() {
		return request != null;
	}

	/**
	 * Checks if this item is a folder
	 * 
	 * @return true if item list is not null
	 */
	public boolean isFolder() {
		return item != null;
	}

	/**
	 * Checks if this item is a request item
	 * 
	 * @return true if has request and is not a folder
	 */
	public boolean isRequest() {
		return hasRequest() && !isFolder();
	}

	/**
	 * Checks if this item has responses
	 * 
	 * @return true if has responses
	 */
	public boolean hasResponses() {
		return response != null && !response.isEmpty();
	}

	/**
	 * Checks if this item has variables
	 * 
	 * @return true if has variables
	 */
	public boolean hasVariables() {
		return variable != null && !variable.isEmpty();
	}

	/**
	 * Checks if this item has events
	 * 
	 * @return true if has events
	 */
	public boolean hasEvents() {
		return event != null && !event.isEmpty();
	}

	/**
	 * Checks if this folder has items
	 * 
	 * @return true if folder has items
	 */
	public boolean hasItems() {
		return item != null && !item.isEmpty();
	}

	/**
	 * Checks if this folder is empty
	 * 
	 * @return true if folder is empty
	 */
	public boolean isEmpty() {
		return isFolder() && !hasItems();
	}

	/**
	 * Gets the HTTP method of the request
	 * 
	 * @return HTTP method or null if no request
	 */
	public String getHttpMethod() {
		return hasRequest() ? request.getMethod().getValue() : null;
	}

	/**
	 * Gets the URL of the request
	 * 
	 * @return URL or null if no request
	 */
	public String getUrl() {
		return hasRequest() ? request.getUrlAsString() : null;
	}

	/**
	 * Gets the number of responses
	 * 
	 * @return Number of responses
	 */
	public int getResponseCount() {
		return response != null ? response.size() : 0;
	}

	/**
	 * Gets the number of items in this folder
	 * 
	 * @return Number of items
	 */
	public int getItemCount() {
		return item != null ? item.size() : 0;
	}

	/**
	 * Gets the number of variables
	 * 
	 * @return Number of variables
	 */
	public int getVariableCount() {
		return variable != null ? variable.size() : 0;
	}

	/**
	 * Gets the number of events
	 * 
	 * @return Number of events
	 */
	public int getEventCount() {
		return event != null ? event.size() : 0;
	}

	/**
	 * Finds an item by name in this folder
	 * 
	 * @param name Item name
	 * @return Item if found, null otherwise
	 */
	public Item findItemByName(String name) {
		if (!isFolder() || name == null)
			return null;

		return item.stream().filter(i -> name.equals(i.getName())).findFirst().orElse(null);
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
	 * Gets all request items in this folder (non-recursive)
	 * 
	 * @return List of request items
	 */
	public List<Item> getRequestItems() {
		if (!isFolder())
			return new ArrayList<>();

		return item.stream().filter(Item::isRequest).toList();
	}

	/**
	 * Gets all folder items in this folder (non-recursive)
	 * 
	 * @return List of folder items
	 */
	public List<Item> getFolderItems() {
		if (!isFolder())
			return new ArrayList<>();

		return item.stream().filter(Item::isFolder).toList();
	}

	/**
	 * Gets all items recursively
	 * 
	 * @return List of all items
	 */
	public List<Item> getAllItemsRecursive() {
		List<Item> allItems = new ArrayList<>();
		if (isFolder() && hasItems()) {
			collectItemsRecursive(item, allItems);
		}
		return allItems;
	}

	private void collectItemsRecursive(List<Item> items, List<Item> collector) {
		for (Item item : items) {
			collector.add(item);
			if (item.isFolder() && item.hasItems()) {
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
		return getAllItemsRecursive().stream().filter(Item::isRequest).toList();
	}

	/**
	 * Counts total requests in this folder (recursive)
	 * 
	 * @return Total number of requests
	 */
	public int getTotalRequestCount() {
		return getAllRequestItemsRecursive().size();
	}

	/**
	 * Counts total folders in this folder (recursive)
	 * 
	 * @return Total number of folders
	 */
	public int getTotalFolderCount() {
		return getAllItemsRecursive().stream().mapToInt(i -> i.isFolder() ? 1 : 0).sum();
	}

	// Utility methods

	/**
	 * Removes an item from this folder
	 * 
	 * @param itemName Name of the item to remove
	 * @return true if item was removed
	 */
	public boolean removeItem(String itemName) {
		if (!isFolder() || itemName == null)
			return false;

		return item.removeIf(i -> itemName.equals(i.getName()));
	}

	/**
	 * Removes a variable
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
	 * Clears all items from this folder
	 * 
	 * @return this instance for method chaining
	 */
	public Item clearItems() {
		if (this.item != null) {
			this.item.clear();
		}
		return this;
	}

	/**
	 * Clears all variables
	 * 
	 * @return this instance for method chaining
	 */
	public Item clearVariables() {
		if (this.variable != null) {
			this.variable.clear();
		}
		return this;
	}

	/**
	 * Clears all events
	 * 
	 * @return this instance for method chaining
	 */
	public Item clearEvents() {
		if (this.event != null) {
			this.event.clear();
		}
		return this;
	}

	/**
	 * Clears all responses
	 * 
	 * @return this instance for method chaining
	 */
	public Item clearResponses() {
		if (this.response != null) {
			this.response.clear();
		}
		return this;
	}

	/**
	 * Creates a copy of this item
	 * 
	 * @return Copy of this item
	 */
	public Item copy() {
		Item.ItemBuilder builder = Item.builder().id(this.id).name(this.name)
				.description(this.description != null ? this.description.copy() : null)
				.request(this.request != null ? this.request.copy() : null)
				.protocolProfileBehavior(this.protocolProfileBehavior);

		// Copy variables
		if (this.variable != null) {
			List<Variable> copiedVariables = new ArrayList<>();
			for (Variable var : this.variable) {
				copiedVariables.add(var != null ? var.copy() : null);
			}
			builder.variable(copiedVariables);
		}

		// Copy events
		if (this.event != null) {
			List<Event> copiedEvents = new ArrayList<>();
			for (Event event : this.event) {
				copiedEvents.add(event != null ? event.copy() : null);
			}
			builder.event(copiedEvents);
		}

		// Copy responses
		if (this.response != null) {
			List<Response> copiedResponses = new ArrayList<>();
			for (Response response : this.response) {
				copiedResponses.add(response != null ? response.copy() : null);
			}
			builder.response(copiedResponses);
		}

		// Copy items (for folders)
		if (this.item != null) {
			List<Item> copiedItems = new ArrayList<>();
			for (Item item : this.item) {
				copiedItems.add(item != null ? item.copy() : null);
			}
			builder.item(copiedItems);
		}

		return builder.build();
	}

	/**
	 * Validates the item structure
	 * 
	 * @return true if item is valid
	 */
	public boolean isValid() {
		// Must have a name
		if (name == null || name.trim().isEmpty()) {
			return false;
		}

		// If it's a request item, must have a request
		if (!isFolder() && !hasRequest()) {
			return false;
		}

		// If it's a folder, item list should not be null
		if (isFolder() && item == null) {
			return false;
		}

		return true;
	}

	/**
	 * Gets item summary
	 * 
	 * @return Item summary as string
	 */
	public String getSummary() {
		if (isFolder()) {
			return String.format(
					"Folder: %s\n" + "Items: %d (Requests: %d, Folders: %d)\n" + "Variables: %d\n" + "Events: %d", name,
					getItemCount(), getTotalRequestCount(), getTotalFolderCount(), getVariableCount(), getEventCount());
		} else {
			return String.format(
					"Request: %s\n" + "Method: %s\n" + "URL: %s\n" + "Responses: %d\n" + "Variables: %d\n"
							+ "Events: %d",
					name, getHttpMethod(), getUrl(), getResponseCount(), getVariableCount(), getEventCount());
		}
	}

	/**
	 * Sets default values for missing fields
	 * 
	 * @return this instance for method chaining
	 */
	public Item withDefaults() {
		if (this.id == null) {
			this.id = UUID.randomUUID().toString();
		}

		if (this.variable == null) {
			this.variable = new ArrayList<>();
		}

		if (this.event == null) {
			this.event = new ArrayList<>();
		}

		if (this.response == null) {
			this.response = new ArrayList<>();
		}

		// If it's a folder and item is null, initialize it
		if (isFolder() && this.item == null) {
			this.item = new ArrayList<>();
		}

		return this;
	}

	/**
	 * Sorts items in this folder alphabetically by name
	 * 
	 * @return this instance for method chaining
	 */
	public Item sortItems() {
		if (isFolder() && hasItems()) {
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
	public Item sortVariables() {
		if (hasVariables()) {
			variable.sort((a, b) -> {
				String keyA = a.getKey() != null ? a.getKey() : "";
				String keyB = b.getKey() != null ? b.getKey() : "";
				return keyA.compareToIgnoreCase(keyB);
			});
		}
		return this;
	}

	/**
	 * Gets the item type as string
	 * 
	 * @return "folder" or "request"
	 */
	public String getType() {
		return isFolder() ? "folder" : "request";
	}

	/**
	 * Gets the depth level of this item (0 for root level)
	 * 
	 * @return Depth level
	 */
	public int getDepth() {
		// This would need to be calculated from the parent context
		// For now, return 0 as default
		return 0;
	}

	/**
	 * Converts to string representation
	 */
	@Override
	public String toString() {
		if (isFolder()) {
			return String.format("Folder[%s] (%d items)", name, getItemCount());
		} else {
			return String.format("Request[%s] %s %s", name, getHttpMethod(), getUrl());
		}
	}

	// Static utility methods

	/**
	 * Creates a SOAP request item
	 * 
	 * @param name       Request name
	 * @param soapAction SOAP action
	 * @param endpoint   SOAP endpoint URL
	 * @param soapBody   SOAP request body
	 * @return SOAP request item
	 */
	public static Item soapRequest(String name, String soapAction, String endpoint, String soapBody) {
		Request soapRequest = Request.builder().method(RequestMethod.POST).url(endpoint)
				.header(List.of(Header.of("Content-Type", "text/xml; charset=utf-8"),
						Header.of("SOAPAction", soapAction != null ? soapAction : "")))
				.body(Body.builder().mode("raw").raw(soapBody).options(BodyOptions.xml()).build()).build();

		return Item.builder().name(name).id(UUID.randomUUID().toString()).request(soapRequest)
				.description(Description.create("SOAP request - Action: " + soapAction)).variable(new ArrayList<>())
				.event(new ArrayList<>()).response(new ArrayList<>()).build();
	}

	/**
	 * Creates a REST request item with JSON body
	 * 
	 * @param name     Request name
	 * @param method   HTTP method
	 * @param url      Request URL
	 * @param jsonBody JSON request body
	 * @return REST request item
	 */
	public static Item restJsonRequest(String name, String method, String url, String jsonBody) {
		Request restRequest = Request.builder().method(parseRequestMethod(method)).url(url)
				.header(List.of(Header.of("Content-Type", "application/json")))
				.body(Body.builder().mode("raw").raw(jsonBody).options(BodyOptions.json()).build()).build();

		return Item.builder().name(name).id(UUID.randomUUID().toString()).request(restRequest)
				.description(Description.create("REST " + method.toUpperCase() + " request"))
				.variable(new ArrayList<>()).event(new ArrayList<>()).response(new ArrayList<>()).build();
	}

	/**
	 * Creates a GraphQL request item
	 * 
	 * @param name  Request name
	 * @param url   GraphQL endpoint URL
	 * @param query GraphQL query
	 * @return GraphQL request item
	 */
	public static Item graphQLRequest(String name, String url, String query) {
		String graphQLBody = String.format("{\"query\": \"%s\"}", query.replace("\"", "\\\"").replace("\n", "\\n"));

		Request graphQLRequest = Request.builder().method(RequestMethod.POST).url(url)
				.header(List.of(Header.of("Content-Type", "application/json")))
				.body(Body.builder().mode("raw").raw(graphQLBody).options(BodyOptions.json()).build()).build();

		return Item.builder().name(name).id(UUID.randomUUID().toString()).request(graphQLRequest)
				.description(Description.create("GraphQL request")).variable(new ArrayList<>()).event(new ArrayList<>())
				.response(new ArrayList<>()).build();
	}

	/**
	 * Creates an item with authentication
	 * 
	 * @param name    Request name
	 * @param request Request object
	 * @param auth    Authentication configuration
	 * @return Item with authentication
	 */
	public static Item withAuth(String name, Request request, Auth auth) {
		Request authenticatedRequest = request.copy().withAuth(auth);

		return Item.builder().name(name).id(UUID.randomUUID().toString()).request(authenticatedRequest)
				.variable(new ArrayList<>()).event(new ArrayList<>()).response(new ArrayList<>()).build();
	}

	/**
	 * Creates an item with basic authentication
	 * 
	 * @param name     Request name
	 * @param request  Request object
	 * @param username Username
	 * @param password Password
	 * @return Item with basic auth
	 */
	public static Item withBasicAuth(String name, Request request, String username, String password) {
		Auth basicAuth = Auth.basic(username, password);
		return withAuth(name, request, basicAuth);
	}

	/**
	 * Creates an item with bearer token authentication
	 * 
	 * @param name    Request name
	 * @param request Request object
	 * @param token   Bearer token
	 * @return Item with bearer auth
	 */
	public static Item withBearerAuth(String name, Request request, String token) {
		Auth bearerAuth = Auth.bearer(token);
		return withAuth(name, request, bearerAuth);
	}

	/**
	 * Creates an item with API key authentication
	 * 
	 * @param name     Request name
	 * @param request  Request object
	 * @param keyName  API key name
	 * @param keyValue API key value
	 * @param location Where to place the key (header, query)
	 * @return Item with API key auth
	 */
	public static Item withApiKeyAuth(String name, Request request, String keyName, String keyValue, String location) {
		Auth apiKeyAuth = Auth.apiKey(keyName, keyValue, location);
		return withAuth(name, request, apiKeyAuth);
	}

	/**
	 * Creates a collection of items from a list of URLs
	 * 
	 * @param baseUrl   Base URL
	 * @param method    HTTP method
	 * @param endpoints List of endpoint paths
	 * @return List of items
	 */
	public static List<Item> fromEndpoints(String baseUrl, String method, List<String> endpoints) {
		if (endpoints == null || endpoints.isEmpty()) {
			return new ArrayList<>();
		}

		return endpoints.stream().map(endpoint -> {
			// Build full URL with proper slash handling
			String fullUrl = buildFullUrl(baseUrl, endpoint);

			// Create readable item name from endpoint
			String itemName = createItemName(endpoint);

			// Build the request with proper RequestMethod enum
			Request request = Request.builder().method(parseRequestMethod(method)).url(fullUrl).build();

			// Build and return the Item using builder pattern
			return Item.builder().name(method.toUpperCase() + " " + itemName).id(UUID.randomUUID().toString())
					.request(request)
					.description(Description.create("Generated " + method.toUpperCase() + " request for " + endpoint))
					.variable(new ArrayList<>()).event(new ArrayList<>()).response(new ArrayList<>()).build();
		}).collect(Collectors.toList());
	}

	/**
	 * Creates a folder structure from a flat list of items based on their names
	 * 
	 * @param items     List of items to organize
	 * @param separator Separator to split item names (e.g., " - ", "/", ".")
	 * @return List of organized items with folder structure
	 */
	public static List<Item> createFolderStructure(List<Item> items, String separator) {
		if (items == null || items.isEmpty()) {
			return new ArrayList<>();
		}

		java.util.Map<String, List<Item>> folderMap = new java.util.HashMap<>();
		List<Item> rootItems = new ArrayList<>();

		for (Item item : items) {
			if (item.getName() != null && item.getName().contains(separator)) {
				String[] parts = item.getName().split(java.util.regex.Pattern.quote(separator), 2);
				String folderName = parts[0].trim();
				String itemName = parts[1].trim();

				// Create a copy of the item with the new name
				Item newItem = item.copy().withId(UUID.randomUUID().toString());
				newItem.setName(itemName);

				folderMap.computeIfAbsent(folderName, k -> new ArrayList<>()).add(newItem);
			} else {
				rootItems.add(item);
			}
		}

		// Create folders
		List<Item> result = new ArrayList<>();
		for (java.util.Map.Entry<String, List<Item>> entry : folderMap.entrySet()) {
			Item folder = Item.folder(entry.getKey(), entry.getValue());
			result.add(folder);
		}

		// Add root items
		result.addAll(rootItems);

		return result;
	}

	/**
	 * Creates a test item with pre-request and test scripts
	 * 
	 * @param name             Item name
	 * @param request          Request object
	 * @param preRequestScript Pre-request script
	 * @param testScript       Test script
	 * @return Item with scripts
	 */
	public static Item withScripts(String name, Request request, String preRequestScript, String testScript) {
		Item item = Item.of(name, request);

		if (preRequestScript != null && !preRequestScript.trim().isEmpty()) {
			item.addPreRequestScript(preRequestScript);
		}

		if (testScript != null && !testScript.trim().isEmpty()) {
			item.addTestScript(testScript);
		}

		return item;
	}

	/**
	 * Creates an item with environment variables
	 * 
	 * @param name      Item name
	 * @param request   Request object
	 * @param variables Map of variable key-value pairs
	 * @return Item with variables
	 */
	public static Item withVariables(String name, Request request, java.util.Map<String, String> variables) {
		Item item = Item.of(name, request);

		if (variables != null && !variables.isEmpty()) {
			for (java.util.Map.Entry<String, String> entry : variables.entrySet()) {
				item.addVariable(entry.getKey(), entry.getValue());
			}
		}

		return item;
	}

	/**
	 * Creates a mock item for testing purposes
	 * 
	 * @param name   Item name
	 * @param method HTTP method
	 * @param path   URL path
	 * @return Mock item
	 */
	public static Item mock(String name, String method, String path) {
		return Item.builder().name(name).id(UUID.randomUUID().toString())
				.request(Request.builder().method(parseRequestMethod(method.toUpperCase()))
						.url("{{baseUrl}}" + (path.startsWith("/") ? path : "/" + path)).build())
				.description(Description.create("Mock " + method.toUpperCase() + " request"))
				.variable(new ArrayList<>()).event(new ArrayList<>()).response(new ArrayList<>()).build();
	}

	/**
	 * Creates a health check item
	 * 
	 * @param baseUrl Base URL for health check
	 * @return Health check item
	 */
	public static Item healthCheck(String baseUrl) {
		String healthUrl = baseUrl + (baseUrl.endsWith("/") ? "health" : "/health");

		String testScript = "pm.test(\"Status code is 200\", function () {\n" + "    pm.response.to.have.status(200);\n"
				+ "});\n\n" + "pm.test(\"Response time is less than 1000ms\", function () {\n"
				+ "    pm.expect(pm.response.responseTime).to.be.below(1000);\n" + "});";

		Event testEvent = Event.builder().listen("test")
				.script(Script.builder().type("text/javascript").exec(List.of(testScript.split("\n"))).build()).build();

		return Item.builder().name("Health Check").id(UUID.randomUUID().toString()).request(Request.get(healthUrl))
				.description(Description.create("Health check endpoint")).variable(new ArrayList<>())
				.event(List.of(testEvent)).response(new ArrayList<>()).build();
	}

	/**
	 * Validates a list of items
	 * 
	 * @param items List of items to validate
	 * @return List of validation errors
	 */
	public static List<String> validateItems(List<Item> items) {
		List<String> errors = new ArrayList<>();

		if (items == null) {
			errors.add("Items list is null");
			return errors;
		}

		for (int i = 0; i < items.size(); i++) {
			Item item = items.get(i);
			if (item == null) {
				errors.add("Item at index " + i + " is null");
				continue;
			}

			if (!item.isValid()) {
				errors.add("Item at index " + i + " (" + item.getName() + ") is invalid");
			}

			// Check for duplicate names
			for (int j = i + 1; j < items.size(); j++) {
				Item otherItem = items.get(j);
				if (otherItem != null && item.getName() != null && item.getName().equals(otherItem.getName())) {
					errors.add("Duplicate item name found: " + item.getName());
				}
			}
		}

		return errors;
	}

	/**
	 * Merges multiple item lists into one
	 * 
	 * @param itemLists Variable number of item lists
	 * @return Merged list of items
	 */
	@SafeVarargs
	public static List<Item> merge(List<Item>... itemLists) {
		List<Item> merged = new ArrayList<>();

		for (List<Item> itemList : itemLists) {
			if (itemList != null) {
				merged.addAll(itemList);
			}
		}

		return merged;
	}

	/**
	 * Filters items by type
	 * 
	 * @param items    List of items to filter
	 * @param isFolder true to get folders, false to get requests
	 * @return Filtered list of items
	 */
	public static List<Item> filterByType(List<Item> items, boolean isFolder) {
		if (items == null) {
			return new ArrayList<>();
		}

		return items.stream().filter(item -> item != null && (isFolder ? item.isFolder() : item.isRequest())).toList();
	}

	/**
	 * Gets statistics for a list of items
	 * 
	 * @param items List of items
	 * @return Item statistics
	 */
	public static ItemStats getStats(List<Item> items) {
		if (items == null) {
			return ItemStats.empty();
		}

		int totalItems = items.size();
		int requestCount = 0;
		int folderCount = 0;
		int totalVariables = 0;
		int totalEvents = 0;
		int totalResponses = 0;

		for (Item item : items) {
			if (item != null) {
				if (item.isRequest()) {
					requestCount++;
				} else if (item.isFolder()) {
					folderCount++;
				}

				totalVariables += item.getVariableCount();
				totalEvents += item.getEventCount();
				totalResponses += item.getResponseCount();
			}
		}

		return ItemStats.builder().totalItems(totalItems).requestCount(requestCount).folderCount(folderCount)
				.totalVariables(totalVariables).totalEvents(totalEvents).totalResponses(totalResponses).build();
	}

	/**
	 * Item statistics helper class
	 */
	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class ItemStats {
		private int totalItems;
		private int requestCount;
		private int folderCount;
		private int totalVariables;
		private int totalEvents;
		private int totalResponses;

		public static ItemStats empty() {
			return new ItemStats();
		}

		@Override
		public String toString() {
			return String.format(
					"Item Stats:\n" + "- Total Items: %d\n" + "- Requests: %d\n" + "- Folders: %d\n"
							+ "- Variables: %d\n" + "- Events: %d\n" + "- Responses: %d",
					totalItems, requestCount, folderCount, totalVariables, totalEvents, totalResponses);
		}
	}

	/**
	 * Helper method to convert String to RequestMethod enum
	 * 
	 * @param method HTTP method as String (e.g., "GET", "POST", "put")
	 * @return RequestMethod enum value, defaults to GET if invalid
	 */
	private static RequestMethod parseRequestMethod(String method) {
		// Return GET if method is null or empty
		if (method == null || method.trim().isEmpty()) {
			return RequestMethod.GET;
		}

		try {
			// Convert to uppercase and trim whitespace for consistency
			return RequestMethod.valueOf(method.toUpperCase().trim());
		} catch (IllegalArgumentException e) {
			// Log warning if needed and return default
			System.err.println("Invalid HTTP method: " + method + ". Using GET as default.");
			return RequestMethod.GET;
		}
	}

	/**
	 * Helper method to build full URL with proper slash handling
	 * 
	 * @param baseUrl  Base URL
	 * @param endpoint Endpoint path
	 * @return Full URL
	 */
	private static String buildFullUrl(String baseUrl, String endpoint) {
		if (baseUrl == null)
			baseUrl = "";
		if (endpoint == null)
			endpoint = "";

		boolean baseEndsWithSlash = baseUrl.endsWith("/");
		boolean endpointStartsWithSlash = endpoint.startsWith("/");

		if (baseEndsWithSlash && endpointStartsWithSlash) {
			return baseUrl + endpoint.substring(1);
		} else if (!baseEndsWithSlash && !endpointStartsWithSlash && !endpoint.isEmpty()) {
			return baseUrl + "/" + endpoint;
		} else {
			return baseUrl + endpoint;
		}
	}

	/**
	 * Helper method to create readable item name from endpoint
	 * 
	 * @param endpoint Endpoint path
	 * @return Readable item name
	 */
	private static String createItemName(String endpoint) {
		if (endpoint == null || endpoint.trim().isEmpty()) {
			return "Root";
		}

		String itemName = endpoint.replaceAll("^/+", "").replaceAll("/", " - ");
		return itemName.isEmpty() ? "Root" : itemName;
	}

}