package es.alesqui.postmangpt.helper;

import es.alesqui.postmangpt.dto.EndpointInfo;
import es.alesqui.postmangpt.model.postman.Body;
import es.alesqui.postmangpt.model.postman.Description;
import es.alesqui.postmangpt.model.postman.FormParameter;
import es.alesqui.postmangpt.model.postman.Header;
import es.alesqui.postmangpt.model.postman.Item;
import es.alesqui.postmangpt.model.postman.QueryParam;
import es.alesqui.postmangpt.model.postman.Request;
import es.alesqui.postmangpt.model.postman.Response;
import es.alesqui.postmangpt.model.postman.Url;
import es.alesqui.postmangpt.model.postman.UrlEncodedParameter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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
 * Separated from PostmanService to maintain single responsibility and
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
			    .compile(":([a-zA-Z_][a-zA-Z0-9_]*)|\\{([^}]+)\\}");
		
		static final Pattern POSTMAN_VARIABLE_PATTERN = Pattern
			    .compile("\\{\\{([^}]+)\\}\\}");

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
	        log.debug("🔍 Processing URL object: {}", request.getUrl().getClass().getSimpleName());
	        
	        // Handle LinkedHashMap format (Jackson deserialization)
	        if (request.getUrl() instanceof Map) {
	            Map<String, Object> urlMap = (Map<String, Object>) request.getUrl();
	            
	            // Try raw URL first if available
	            Object rawUrl = urlMap.get("raw");
	            if (rawUrl != null && StringUtils.isNotBlank(rawUrl.toString())) {
	                log.debug("✅ Using raw URL: {}", rawUrl);
	                return rawUrl.toString();
	            }
	            
	            log.debug("⚠️ Raw URL is blank, building from components");
	            String builtUrl = buildUrlFromMap(urlMap);
	            log.debug("🔨 Built URL: {}", builtUrl);
	            return builtUrl;
	        }

	        // Handle string URL format
	        if (request.getUrl() instanceof String) {
	            log.debug("✅ Using string URL: {}", request.getUrl());
	            return (String) request.getUrl();
	        }

	        // Fallback to toString
	        String fallbackUrl = request.getUrl().toString();
	        log.debug("🔄 Using toString fallback: {}", fallbackUrl);
	        return fallbackUrl;

	    } catch (Exception e) {
	        log.error("❌ Failed to extract URL from request", e);
	        return "";
	    }
	}

	private String buildUrlFromMap(Map<String, Object> urlMap) {
	    try {
	        StringBuilder urlBuilder = new StringBuilder();
	        
	        // Get protocol (default to https if not specified)
	        Object protocol = urlMap.get("protocol");
	        if (protocol != null && StringUtils.isNotBlank(protocol.toString())) {
	            urlBuilder.append(protocol).append("://");
	        }
	        
	        // Get host
	        Object host = urlMap.get("host");
	        if (host != null) {
	            if (host instanceof List) {
	                List<?> hostList = (List<?>) host;
	                if (!hostList.isEmpty()) {
	                    urlBuilder.append(hostList.get(0).toString());
	                }
	            } else {
	                urlBuilder.append(host.toString());
	            }
	        }
	        
	        // Get port
	        Object port = urlMap.get("port");
	        if (port != null && StringUtils.isNotBlank(port.toString())) {
	            urlBuilder.append(":").append(port);
	        }
	        
	        // Get path
	        Object path = urlMap.get("path");
	        if (path != null) {
	            if (path instanceof List) {
	                List<?> pathList = (List<?>) path;
	                for (Object pathSegment : pathList) {
	                    urlBuilder.append("/").append(pathSegment.toString());
	                }
	            } else {
	                if (!path.toString().startsWith("/")) {
	                    urlBuilder.append("/");
	                }
	                urlBuilder.append(path.toString());
	            }
	        }
	        
	        // Get query parameters
	        Object query = urlMap.get("query");
	        if (query != null && query instanceof List) {
	            List<?> queryList = (List<?>) query;
	            if (!queryList.isEmpty()) {
	                urlBuilder.append("?");
	                boolean first = true;
	                for (Object queryParam : queryList) {
	                    if (queryParam instanceof Map) {
	                        Map<?, ?> paramMap = (Map<?, ?>) queryParam;
	                        Object key = paramMap.get("key");
	                        Object value = paramMap.get("value");
	                        
	                        if (key != null && value != null) {
	                            if (!first) {
	                                urlBuilder.append("&");
	                            }
	                            urlBuilder.append(key).append("=").append(value);
	                            first = false;
	                        }
	                    }
	                }
	            }
	        }
	        
	        return urlBuilder.toString();
	        
	    } catch (Exception e) {
	        log.error("❌ Failed to build URL from map: {}", urlMap, e);
	        return "";
	    }
	}


	/**
	 * Builds URL string from Postman URL object with all components.
	 */
	private String buildUrlFromObject(Url urlObj) {
	    try {
	        // If we have host and path, build manually
	        if (urlObj.getHost() != null && urlObj.getPath() != null) {
	            StringBuilder url = new StringBuilder();
	            
	            // Add host
	            if (urlObj.getHost() instanceof List) {
	                @SuppressWarnings("unchecked")
	                List<String> hostParts = (List<String>) urlObj.getHost();
	                url.append(String.join(".", hostParts));
	            } else {
	                url.append(urlObj.getHost().toString());
	            }
	            
	            // Add path
	            if (urlObj.getPath() instanceof List) {
	                @SuppressWarnings("unchecked")
	                List<String> pathParts = (List<String>) urlObj.getPath();
	                if (!pathParts.isEmpty()) {
	                    url.append("/").append(String.join("/", pathParts));
	                }
	            } else {
	                String pathStr = urlObj.getPath().toString();
	                if (StringUtils.isNotBlank(pathStr)) {
	                    if (!pathStr.startsWith("/")) {
	                        url.append("/");
	                    }
	                    url.append(pathStr);
	                }
	            }
	            
	            return url.toString();
	        }
	        
	        // Fallback to toString if we can't build properly
	        return urlObj.toString();
	        
	    } catch (Exception e) {
	        log.error("Error building URL from object", e);
	        return urlObj.toString();
	    }
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
		return extractPathParameters(url);
	}
	
	private List<String> extractPathParameters(String url) {
	    List<String> pathParams = new ArrayList<>();
	    
	    if (StringUtils.isBlank(url)) {
	        return pathParams;
	    }
	    
	    try {
	        // Find all {param} patterns but exclude {{variable}} patterns
	        Pattern pattern = Pattern.compile("\\{([^}]+)\\}");
	        Matcher matcher = pattern.matcher(url);
	        
	        while (matcher.find()) {
	            String fullMatch = matcher.group(0); // e.g., "{id}" or "{{var}}"
	            String param = matcher.group(1);     // e.g., "id" or "{var"
	            
	            // Skip if it's a Postman variable (contains another opening brace)
	            if (!param.startsWith("{") && !fullMatch.startsWith("{{")) {
	                pathParams.add(param);
	                log.debug("Found path parameter: {}", param);
	            } else {
	                log.debug("Skipping Postman variable: {}", fullMatch);
	            }
	        }
	        
	        log.debug("Extracted {} path parameters from URL: {}", pathParams.size(), url);
	        
	    } catch (Exception e) {
	        log.error("Failed to extract path parameters from URL: {}", url, e);
	    }
	    
	    return pathParams;
	}


	/**
	 * Extracts query parameters with descriptions from URL object.
	 */
	private Map<String, String> extractQueryParameters(Request request) {
		Map<String, String> queryParams = new HashMap<>();
		String url = extractUrl(request);
		if (StringUtils.isBlank(url)) {
			return queryParams;
		} 
		return extractQueryParameters(url);
	}

	private Map<String, String> extractQueryParameters(String url) {
	    Map<String, String> queryParams = new HashMap<>();
	    
	    if (StringUtils.isBlank(url)) {
	        return queryParams;
	    }
	    
	    try {
	        // Find the query string part (after ?)
	        int queryStart = url.indexOf('?');
	        if (queryStart == -1) {
	            log.debug("No query parameters found in URL: {}", url);
	            return queryParams;
	        }
	        
	        String queryString = url.substring(queryStart + 1);
	        if (StringUtils.isBlank(queryString)) {
	            return queryParams;
	        }
	        
	        // Split by & to get individual parameters
	        String[] paramPairs = queryString.split("&");
	        
	        for (String pair : paramPairs) {
	            if (StringUtils.isNotBlank(pair)) {
	                // Split by = to get key and value
	                String[] keyValue = pair.split("=", 2); // Limit to 2 parts in case value contains =
	                
	                if (keyValue.length >= 1) {
	                    String key = keyValue[0].trim();
	                    String value = keyValue.length > 1 ? keyValue[1].trim() : "";
	                    
	                    if (StringUtils.isNotBlank(key)) {
	                        // URL decode if needed
	                        try {
	                            key = URLDecoder.decode(key, StandardCharsets.UTF_8);
	                            value = URLDecoder.decode(value, StandardCharsets.UTF_8);
	                        } catch (Exception e) {
	                            log.warn("Failed to URL decode parameter: {}={}", key, value);
	                        }
	                        
	                        queryParams.put(key, value);
	                        log.debug("Found query parameter: {} = {}", key, value);
	                    }
	                }
	            }
	        }
	        
	        log.debug("Extracted {} query parameters from URL: {}", queryParams.size(), url);
	        
	    } catch (Exception e) {
	        log.error("Failed to extract query parameters from URL: {}", url, e);
	    }
	    
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