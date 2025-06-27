package es.alesqui.postmangpt.helper;

import es.alesqui.postmangpt.dto.EndpointInfo;
import es.alesqui.postmangpt.model.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Helper class responsible for extracting and converting Postman collection
 * items into EndpointInfo objects.
 * 
 * This class encapsulates all the complex logic for parsing Postman collection
 * structure, handling different URL formats, extracting parameters, and
 * building comprehensive endpoint metadata.
 * 
 * Separated from PostmanCollectionService to maintain single responsibility and
 * improve code maintainability.
 */
@Slf4j
@Component
public class PostmanEndpointExtractor {

	// ================================================================================================
	// CONFIGURATION CONSTANTS
	// ================================================================================================

	private static final class Config {
		// Regex patterns for parameter extraction
		static final Pattern PATH_PARAM_PATTERN = Pattern
				.compile("\\{\\{([^}]+)\\}\\}|:([a-zA-Z_][a-zA-Z0-9_]*)|\\{([^}]+)\\}");

		// Common endpoint categorization patterns
		static final Map<String, String> ENDPOINT_CATEGORIES = Map.of("auth|login|signin|signup", "authentication",
				"user|profile|account", "user-management", "admin|management", "admin", "order|purchase|payment",
				"commerce", "report|analytics|stats", "reporting");

		static final int MAX_DESCRIPTION_LENGTH = 500;
		static final String DEFAULT_HTTP_METHOD = "GET";
	}

	// ================================================================================================
	// PUBLIC EXTRACTION METHODS
	// ================================================================================================

	/**
	 * Extracts all endpoints from a collection's items recursively.
	 * 
	 * @param items List of collection items to process
	 * @return List of extracted EndpointInfo objects
	 */
	public List<EndpointInfo> extractEndpoints(List<Item> items) {
		log.debug("Starting endpoint extraction from {} items", items != null ? items.size() : 0);

		if (items == null || items.isEmpty()) {
			return List.of();
		}

		List<EndpointInfo> endpoints = new ArrayList<>();
		processItems(items, endpoints, "");

		log.info("Successfully extracted {} endpoints", endpoints.size());
		return endpoints;
	}

	/**
	 * Converts a single Postman item to EndpointInfo with full context.
	 * 
	 * @param item       The Postman collection item
	 * @param folderPath The folder context path
	 * @return EndpointInfo object or null if conversion fails
	 */
	public EndpointInfo convertItemToEndpoint(Item item, String folderPath) {
		if (item == null || item.getRequest() == null) {
			log.warn("Cannot convert null item or item without request");
			return null;
		}

		try {
			log.trace("Converting item: {} in folder: {}", item.getName(), folderPath);

			Request request = item.getRequest();

			return EndpointInfo.builder().name(buildEndpointName(item.getName(), folderPath))
					.method(extractHttpMethod(request)).url(extractUrl(request)).description(extractDescription(item))
					.pathParameters(extractPathParameters(request)).queryParameters(extractQueryParameters(request))
					.headers(extractHeaders(request)).requestBodyExample(extractRequestBodyExample(request))
					.responseExample(extractResponseExample(item)).tags(extractTags(item, folderPath)).matchScore(null) // Will
																														// be
																														// calculated
																														// during
																														// matching
					.build();

		} catch (Exception e) {
			log.error("Failed to convert item '{}' to EndpointInfo", item.getName(), e);
			return null;
		}
	}

	// ================================================================================================
	// PRIVATE RECURSIVE PROCESSING
	// ================================================================================================

	/**
	 * Recursively processes items, handling both requests and nested folders.
	 */
	private void processItems(List<Item> items, List<EndpointInfo> endpoints, String folderPath) {
		for (Item item : items) {
			if (item.getRequest() != null) {
				// This is a request item
				EndpointInfo endpoint = convertItemToEndpoint(item, folderPath);
				if (endpoint != null) {
					endpoints.add(endpoint);
					log.trace("Added endpoint: {} {}", endpoint.getMethod(), endpoint.getUrl());
				}
			} else if (item.getItem() != null && !item.getItem().isEmpty()) {
				// This is a folder - recurse into it
				String newFolderPath = buildFolderPath(folderPath, item.getName());
				log.trace("Processing folder: {}", newFolderPath);
				processItems(item.getItem(), endpoints, newFolderPath);
			}
		}
	}

	/**
	 * Builds folder path for nested items.
	 */
	private String buildFolderPath(String currentPath, String folderName) {
		if (StringUtils.isBlank(currentPath)) {
			return folderName;
		}
		return currentPath + "/" + folderName;
	}

	// ================================================================================================
	// PRIVATE EXTRACTION METHODS - BASIC INFO
	// ================================================================================================

	/**
	 * Builds comprehensive endpoint name including folder context.
	 */
	private String buildEndpointName(String itemName, String folderPath) {
		if (StringUtils.isBlank(folderPath)) {
			return itemName;
		}
		return folderPath + " > " + itemName;
	}

	/**
	 * Extracts HTTP method with fallback to default.
	 */
	private String extractHttpMethod(Request request) {
		if (request.getMethod() != null) {
			return request.getMethod().getValue();
		}

		log.trace("No HTTP method found, using default: {}", Config.DEFAULT_HTTP_METHOD);
		return Config.DEFAULT_HTTP_METHOD;
	}

	/**
	 * Extracts comprehensive description from multiple sources.
	 */
	private String extractDescription(Item item) {
		String description = "";

		// Try request description first
		if (item.getRequest() != null && item.getRequest().getDescription() != null) {
			description = extractDescriptionContent(item.getRequest().getDescription());
		}

		// Fallback to item description
		if (StringUtils.isBlank(description) && item.getDescription() != null) {
			description = extractDescriptionContent(item.getDescription());
		}

		// Limit description length
		if (StringUtils.isNotBlank(description) && description.length() > Config.MAX_DESCRIPTION_LENGTH) {
			description = StringUtils.abbreviate(description, Config.MAX_DESCRIPTION_LENGTH);
		}

		return description;
	}

	/**
	 * Extracts content from Description object (handles both string and object
	 * formats).
	 */
	private String extractDescriptionContent(Object description) {
		if (description == null) {
			return "";
		}

		if (description instanceof String) {
			return (String) description;
		}

		// Handle Description object with content field
		try {
			if (description instanceof Description) {
				Description desc = (Description) description;
				return desc.getContent() != null ? desc.getContent() : "";
			}
		} catch (Exception e) {
			log.trace("Could not extract description content", e);
		}

		return description.toString();
	}

	// ================================================================================================
	// PRIVATE EXTRACTION METHODS - URL PROCESSING
	// ================================================================================================

	/**
	 * Extracts and constructs complete URL from various Postman formats.
	 */
	private String extractUrl(Request request) {
		if (request.getUrl() == null) {
			log.warn("Request has no URL");
			return "";
		}

		try {
			// Handle string URL format
			if (request.getUrl() instanceof String) {
				return (String) request.getUrl();
			}

			// Handle Postman URL object format
			if (request.getUrl() instanceof Url) {
				return buildUrlFromObject((Url) request.getUrl());
			}

			// Fallback to toString
			return request.getUrl().toString();

		} catch (Exception e) {
			log.error("Failed to extract URL from request", e);
			return "";
		}
	}

	/**
	 * Builds URL string from Postman URL object with all components.
	 */
	private String buildUrlFromObject(Url urlObj) {
		StringBuilder urlBuilder = new StringBuilder();

		try {
			// Add protocol
			if (StringUtils.isNotBlank(urlObj.getProtocol())) {
				urlBuilder.append(urlObj.getProtocol()).append("://");
			}

			// Add host (can be string or array)
			appendHost(urlBuilder, urlObj.getHost());

			// Add port
			if (urlObj.getPort() != null) {
				urlBuilder.append(":").append(urlObj.getPort());
			}

			// Add path (can be string or array)
			appendPath(urlBuilder, urlObj.getPath());

			// Add query parameters
			appendQueryParameters(urlBuilder, urlObj.getQuery());

		} catch (Exception e) {
			log.error("Error building URL from object", e);
		}

		return urlBuilder.toString();
	}

	/**
	 * Appends host to URL builder (handles both string and array formats).
	 */
	private void appendHost(StringBuilder urlBuilder, Object host) {
		if (host == null)
			return;

		if (host instanceof List) {
			@SuppressWarnings("unchecked")
			List<String> hostParts = (List<String>) host;
			urlBuilder.append(String.join(".", hostParts));
		} else {
			urlBuilder.append(host.toString());
		}
	}

	/**
	 * Appends path to URL builder (handles both string and array formats).
	 */
	private void appendPath(StringBuilder urlBuilder, Object path) {
		if (path == null)
			return;

		if (path instanceof List) {
			@SuppressWarnings("unchecked")
			List<String> pathParts = (List<String>) path;
			if (!pathParts.isEmpty()) {
				urlBuilder.append("/").append(String.join("/", pathParts));
			}
		} else {
			String pathStr = path.toString();
			if (StringUtils.isNotBlank(pathStr)) {
				if (!pathStr.startsWith("/")) {
					urlBuilder.append("/");
				}
				urlBuilder.append(pathStr);
			}
		}
	}

	/**
	 * Appends query parameters to URL builder using QueryParam utility methods.
	 */
	private void appendQueryParameters(StringBuilder urlBuilder, List<QueryParam> queryParams) {
		if (queryParams == null || queryParams.isEmpty())
			return;

		String queryString = QueryParam.toQueryString(queryParams);
		if (StringUtils.isNotBlank(queryString)) {
			urlBuilder.append("?").append(queryString);
		}
	}

	// ================================================================================================
	// PRIVATE EXTRACTION METHODS - PARAMETERS
	// ================================================================================================

	/**
	 * Extracts path parameters from URL using comprehensive regex patterns.
	 */
	private List<String> extractPathParameters(Request request) {
		String url = extractUrl(request);
		if (StringUtils.isBlank(url)) {
			return List.of();
		}

		List<String> pathParams = new ArrayList<>();
		Matcher matcher = Config.PATH_PARAM_PATTERN.matcher(url);

		while (matcher.find()) {
			String param = matcher.group(1) != null ? matcher.group(1)
					: matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
			if (StringUtils.isNotBlank(param)) {
				pathParams.add(param);
			}
		}

		log.trace("Extracted {} path parameters from URL: {}", pathParams.size(), url);
		return pathParams;
	}

	/**
	 * Extracts query parameters with descriptions from URL object.
	 */
	private Map<String, String> extractQueryParameters(Request request) {
		Map<String, String> queryParams = new HashMap<>();

		if (request.getUrl() instanceof Url) {
			Url urlObj = (Url) request.getUrl();
			if (urlObj.getQuery() != null) {
				for (QueryParam queryParam : urlObj.getQuery()) { // Cambio aquí también
					if (StringUtils.isNotBlank(queryParam.getKey())) {
						String description = buildQueryParamDescription(queryParam);
						queryParams.put(queryParam.getKey(), description);
					}
				}
			}
		}

		log.trace("Extracted {} query parameters", queryParams.size());
		return queryParams;
	}

	/**
	 * Builds comprehensive description for query parameter.
	 */
	private String buildQueryParamDescription(QueryParam queryParam) {
		StringBuilder desc = new StringBuilder();

		// Add description from Description object
		if (queryParam.getDescription() != null && StringUtils.isNotBlank(queryParam.getDescription().getContent())) {
			desc.append(queryParam.getDescription().getContent());
		}

		// Add default value if available
		if (StringUtils.isNotBlank(queryParam.getValue())) {
			if (desc.length() > 0)
				desc.append(" ");
			desc.append("(default: ").append(queryParam.getValue()).append(")");
		}

		// Add disabled status if parameter is disabled
		if (queryParam.isDisabled()) {
			if (desc.length() > 0)
				desc.append(" ");
			desc.append("[disabled]");
		}

		return desc.length() > 0 ? desc.toString() : "Query parameter";
	}

	/**
	 * Extracts headers with their values from request.
	 */
	private Map<String, String> extractHeaders(Request request) {
		Map<String, String> headers = new HashMap<>();

		if (request.getHeader() != null) {
			for (Header header : request.getHeader()) {
				if (StringUtils.isNotBlank(header.getKey())) {
					String value = StringUtils.isNotBlank(header.getValue()) ? header.getValue() : "";
					headers.put(header.getKey(), value);
				}
			}
		}

		log.trace("Extracted {} headers", headers.size());
		return headers;
	}

	// ================================================================================================
	// PRIVATE EXTRACTION METHODS - BODY AND RESPONSES
	// ================================================================================================

	/**
	 * Extracts request body example handling different body types.
	 */
	private String extractRequestBodyExample(Request request) {
		if (request.getBody() == null) {
			return null;
		}

		Body body = request.getBody();

		// Handle raw body (most common for JSON/XML)
		if (StringUtils.isNotBlank(body.getRaw())) {
			return body.getRaw();
		}

		// Handle form-data
		if (body.getFormdata() != null && !body.getFormdata().isEmpty()) {
			return buildFormDataExample(body.getFormdata());
		}

		// Handle urlencoded
		if (body.getUrlencoded() != null && !body.getUrlencoded().isEmpty()) {
			return buildUrlencodedExample(body.getUrlencoded());
		}

		return null;
	}

	/**
	 * Builds form-data example string.
	 */
	private String buildFormDataExample(List<FormParameter> formdata) {
		return formdata.stream().filter(param -> StringUtils.isNotBlank(param.getKey()))
				.map(param -> param.getKey() + "=" + (StringUtils.isNotBlank(param.getValue()) ? param.getValue() : ""))
				.collect(Collectors.joining("&"));
	}

	/**
	 * Builds URL-encoded example string.
	 */
	private String buildUrlencodedExample(List<UrlEncodedParameter> urlencoded) {
		return urlencoded.stream().filter(param -> StringUtils.isNotBlank(param.getKey()))
				.map(param -> param.getKey() + "=" + (StringUtils.isNotBlank(param.getValue()) ? param.getValue() : ""))
				.collect(Collectors.joining("&"));
	}

	/**
	 * Extracts response example from item responses.
	 */
	private String extractResponseExample(Item item) {
		if (item.getResponse() == null || item.getResponse().isEmpty()) {
			return null;
		}

		// Get first successful response (200-299) or first response
		Response response = item.getResponse().stream().filter(r -> r.getCode() >= 200 && r.getCode() < 300).findFirst()
				.orElse(item.getResponse().get(0));

		return StringUtils.isNotBlank(response.getBody()) ? response.getBody() : null;
	}

	// ================================================================================================
	// PRIVATE EXTRACTION METHODS - TAGS AND CATEGORIZATION
	// ================================================================================================

	/**
	 * Extracts and generates tags for endpoint categorization.
	 */
	private List<String> extractTags(Item item, String folderPath) {
		Set<String> tags = new HashSet<>();

		// Add folder-based tags
		addFolderTags(tags, folderPath);

		// Add HTTP method tag
		addMethodTag(tags, item.getRequest());

		// Add URL-pattern based tags
		addUrlPatternTags(tags, extractUrl(item.getRequest()));

		// Add endpoint category tags
		addCategoryTags(tags, item.getName(), extractUrl(item.getRequest()));

		return new ArrayList<>(tags);
	}

	/**
	 * Adds tags based on folder structure.
	 */
	private void addFolderTags(Set<String> tags, String folderPath) {
		if (StringUtils.isNotBlank(folderPath)) {
			String[] folders = folderPath.split("/");
			for (String folder : folders) {
				if (StringUtils.isNotBlank(folder)) {
					tags.add(normalizeTag(folder));
				}
			}
		}
	}

	/**
	 * Adds HTTP method as tag.
	 */
	private void addMethodTag(Set<String> tags, Request request) {
		if (request != null && request.getMethod() != null) {
			tags.add(request.getMethod().getValue().toLowerCase());
		}
	}

	/**
	 * Adds tags based on URL patterns.
	 */
	private void addUrlPatternTags(Set<String> tags, String url) {
		if (StringUtils.isBlank(url))
			return;

		String lowerUrl = url.toLowerCase();

		// Check for common patterns
		if (lowerUrl.contains("/api/"))
			tags.add("api");
		if (lowerUrl.contains("/v1/") || lowerUrl.contains("/v2/"))
			tags.add("versioned");
		if (lowerUrl.contains("/{") || lowerUrl.contains("{{"))
			tags.add("parameterized");
	}

	/**
	 * Adds category tags based on endpoint name and URL patterns.
	 */
	private void addCategoryTags(Set<String> tags, String itemName, String url) {
		String searchText = (itemName + " " + url).toLowerCase();

		for (Map.Entry<String, String> category : Config.ENDPOINT_CATEGORIES.entrySet()) {
			if (searchText.matches(".*(" + category.getKey() + ").*")) {
				tags.add(category.getValue());
			}
		}
	}

	/**
	 * Normalizes tag by converting to lowercase and replacing spaces with hyphens.
	 */
	private String normalizeTag(String tag) {
		return tag.toLowerCase().replaceAll("\\s+", "-").replaceAll("[^a-z0-9-]", "");
	}
}