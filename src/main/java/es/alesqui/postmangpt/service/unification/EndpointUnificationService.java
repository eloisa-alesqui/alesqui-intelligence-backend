package es.alesqui.postmangpt.service.unification;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import es.alesqui.postmangpt.annotation.HandleApiUnificationException;
import es.alesqui.postmangpt.model.postman.Collection;
import es.alesqui.postmangpt.model.postman.Header;
import es.alesqui.postmangpt.model.postman.Item;
import es.alesqui.postmangpt.model.postman.ItemGroup;
import es.alesqui.postmangpt.model.postman.Request;
import es.alesqui.postmangpt.model.postman.Response;
import es.alesqui.postmangpt.model.postman.Url;
import es.alesqui.postmangpt.model.unified.UnifiedEndpoint;
import es.alesqui.postmangpt.model.unified.UnifiedExample;
import es.alesqui.postmangpt.model.unified.UnifiedRequestBody;
import es.alesqui.postmangpt.util.UnificationUtils;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class EndpointUnificationService {

    @HandleApiUnificationException
    public List<UnifiedEndpoint> mergeEndpoints(OpenAPI openApi, Collection postmanCollection) {
    	Map<String, UnifiedEndpoint> endpointMap = new HashMap<>();

		if (openApi != null && openApi.getPaths() != null) {
			openApi.getPaths().forEach((path, pathItem) -> {
				extractSwaggerEndpoints(path, pathItem, endpointMap);
			});
		}

		if (postmanCollection != null && postmanCollection.getItem() != null) {
			mergePostmanEndpoints(postmanCollection.getItem(), endpointMap);
		}

		return new ArrayList<>(endpointMap.values());
    }

    /**
	 * Extracts endpoints from the Swagger document and adds them to the unified
	 * endpoint map.
	 * 
	 * @param path        The endpoint path in the Swagger document.
	 * @param pathItem    The PathItem object containing operations for the
	 *                    endpoint.
	 * @param endpointMap The map of unified endpoints being built.
	 */
	@HandleApiUnificationException
	private void extractSwaggerEndpoints(String path, PathItem pathItem, Map<String, UnifiedEndpoint> endpointMap) {
		Map<PathItem.HttpMethod, Operation> operations = pathItem.readOperationsMap();

		operations.forEach((method, operation) -> {
			String key = method.toString() + ":" + path;

			UnifiedEndpoint endpoint = UnifiedEndpoint.builder()
					.id(operation.getOperationId() != null ? operation.getOperationId() : key)
					.summary(operation.getSummary() != null ? operation.getSummary() : key)
					.description(operation.getDescription())
					.path(path)
					.method(method.toString())
					.operationId(operation.getOperationId())
					.tags(operation.getTags())
					.deprecated(Boolean.TRUE.equals(operation.getDeprecated()))
					.parameters(UnificationUtils.convertSwaggerParameters(operation.getParameters()))
					.requestBody(UnificationUtils.convertSwaggerRequestBody(operation.getRequestBody()))
					.responses(UnificationUtils.convertSwaggerResponses(operation.getResponses())).build();

			endpointMap.put(key, endpoint);
		});
	}

	/**
	 * Merges endpoints from the Postman collection into the unified endpoint map.
	 * 
	 * @param items       The list of Postman collection items (folders or
	 *                    endpoints).
	 * @param endpointMap The map of unified endpoints being built.
	 */
	@HandleApiUnificationException
	private void mergePostmanEndpoints(List<?> items, Map<String, UnifiedEndpoint> endpointMap) {
		for (Object item : items) {
			processPostmanObject(item, endpointMap);
		}
	}
	
	/**
	 * Processes a Postman object (either an item or a folder) and adds its data to
	 * the unified endpoint map.
	 * 
	 * @param obj         The Postman object (item or folder).
	 * @param endpointMap The map of unified endpoints being built.
	 */
	@HandleApiUnificationException
	private void processPostmanObject(Object obj, Map<String, UnifiedEndpoint> endpointMap) {
		if (obj == null) {
			return;
		}

		if (obj instanceof Item) {
			Item postmanItem = (Item) obj;
			mergePostmanItem(postmanItem, endpointMap);
		} else if (obj instanceof ItemGroup) {
			ItemGroup folder = (ItemGroup) obj;
			processPostmanFolder(folder, endpointMap);
		}
	}

	/**
	 * Processes a Postman folder and recursively merges its contents into the
	 * unified endpoint map.
	 * 
	 * @param folder      The Postman folder containing items or subfolders.
	 * @param endpointMap The map of unified endpoints being built.
	 */
	@HandleApiUnificationException
	private void processPostmanFolder(ItemGroup folder, Map<String, UnifiedEndpoint> endpointMap) {
		if (folder == null) {
			return;
		}

		if (folder.getItem() != null && !folder.getItem().isEmpty()) {
			mergePostmanEndpoints(folder.getItem(), endpointMap);
		}
	}

	/**
	 * Merges a Postman item into the unified endpoint map. If the endpoint already
	 * exists, it merges the data.
	 * 
	 * @param item        The Postman item representing an endpoint.
	 * @param endpointMap The map of unified endpoints being built.
	 */
	@HandleApiUnificationException
	private void mergePostmanItem(Item item, Map<String, UnifiedEndpoint> endpointMap) {
		Request request = item.getRequest();
		if (request == null) {
			return;
		}

		String method = request.getMethod() != null ? request.getMethod().getValue().toUpperCase() : "GET";
		String path = extractPathFromUrl(request.getUrl());
		String key = method + ":" + path;

		UnifiedEndpoint existing = endpointMap.get(key);
		if (existing != null) {
			mergePostmanDataIntoEndpoint(existing, item);
		} else {
			UnifiedEndpoint endpoint = createEndpointFromPostman(item);
			if (endpoint != null) {
				endpointMap.put(key, endpoint);
			}
		}
	}
	
	/**
	 * Merges Postman data into an existing unified endpoint.
	 * 
	 * @param endpoint The existing unified endpoint.
	 * @param item     The Postman item containing additional data.
	 */
	@HandleApiUnificationException
	private void mergePostmanDataIntoEndpoint(UnifiedEndpoint endpoint, Item item) {
		
		String name = item.getName();
		endpoint.setName(name);
		
		Request request = item.getRequest();
		if (request != null) {
			if (request.getHeader() != null) {
				Map<String, String> headers = endpoint.getHeaders() != null ? new HashMap<>(endpoint.getHeaders())
						: new HashMap<>();
	
				for (Header header : request.getHeader()) {
					if (!Boolean.TRUE.equals(header.getDisabled())) {
						headers.put(header.getKey(), header.getValue());
					}
				}
				endpoint.setHeaders(headers);				
			}
			if(request.getBody() != null) {
				UnifiedRequestBody unifiedRequestBody = endpoint.getRequestBody();
				UnificationUtils.extractPostmanRequestBody(unifiedRequestBody, request);
			}
		}
		
		if (item.getResponse() != null && !item.getResponse().isEmpty()) {
			List<UnifiedExample> examples = new ArrayList<>();
			for (Response response : item.getResponse()) {
				UnifiedExample example = UnifiedExample.builder()
						.name(response.getName() != null ? response.getName() : "Example").value(response.getBody())
						.build();
				examples.add(example);
			}
			endpoint.setExamples(examples);
		}		
	}

	/**
	 * Creates a unified endpoint from a Postman item.
	 * 
	 * @param item The Postman item representing an endpoint.
	 * @return A unified endpoint object.
	 */
	@HandleApiUnificationException
	private UnifiedEndpoint createEndpointFromPostman(Item item) {
		Request request = item.getRequest();
		if (request == null) {
			return null;
		}

		String method = request.getMethod() != null ? request.getMethod().getValue().toUpperCase() : "GET";
		String path = extractPathFromUrl(request.getUrl());

		return UnifiedEndpoint.builder().id(item.getId() != null ? item.getId() : method + ":" + path)
				.summary(item.getName()).description(UnificationUtils
				.extractDescription(item.getDescription())).path(path).method(method)
				.parameters(UnificationUtils.extractPostmanParameters(request))
				//.requestBody(UnificationUtils.extractPostmanRequestBody(request))
				.headers(UnificationUtils.extractPostmanHeaders(request)).build();
	}
	
	/**
	 * Extracts the path from a URL object, converting Postman-style variables to OpenAPI format.
	 * 
	 * @param url The URL object (either a String or a Postman Url object).
	 * @return The extracted path.
	 */
	@HandleApiUnificationException
	private String extractPathFromUrl(Object url) {
	    if (url == null) {
	        log.debug("URL object is null, returning default path '/'");
	        return "/";
	    }

	    if (url instanceof String) {
	        return extractPathFromString((String) url);
	    }

	    if (url instanceof Url) {
	        Url urlObj = (Url) url;

	        // Prefer 'raw' if available
	        if (urlObj.getRaw() != null) {
	            return extractPathFromString(urlObj.getRaw());
	        }

	        // Process 'path' if 'raw' is not available
	        List<String> pathSegments = urlObj.getPath();
	        if (pathSegments != null && !pathSegments.isEmpty()) {
	            return pathSegments.size() > 1
	                ? "/" + String.join("/", pathSegments.subList(1, pathSegments.size())) // Exclude the first segment
	                : "/";
	        }
	    }

	    log.debug("URL object is unsupported, returning default path '/'");
	    return "/";
	}
	
	/**
	 * Extracts the path from a URL string, removing query parameters and protocol.
	 * 
	 * @param url The URL string.
	 * @return The extracted path.
	 */
	@HandleApiUnificationException
	private String extractPathFromString(String url) {
		if (url == null || url.trim().isEmpty()) {
			log.debug("URL string is null or empty, returning default path '/'");
			return "/";
		}

		// Remove protocol and host
		String path = url.replaceFirst("^https?://[^/]+", "");

		// Remove query parameters
		int queryIndex = path.indexOf('?');
		if (queryIndex > 0) {
			path = path.substring(0, queryIndex);
		}

		// Ensure path starts with /
		if (!path.startsWith("/")) {
			path = "/" + path;
		}

		// Convert Postman variables to OpenAPI format
		path = path.replaceAll(":\\w+", "{$0}").replaceAll(":\\{", "{");
		
		path = removeFirstTwoSegments(path);

		log.debug("Extracted path from URL: {}", path);
		return path;
	}
	
	/**
	 * Removes the first two segments of a path and returns the remaining part.
	 * 
	 * @param path The input path string.
	 * @return The path with the first two segments removed.
	 */
	private String removeFirstTwoSegments(String path) {
	    if (path == null || path.trim().isEmpty()) {
	        log.debug("Path is null or empty, returning default '/'");
	        return "/";
	    }

	    // Ensure path starts with /
	    if (!path.startsWith("/")) {
	        path = "/" + path;
	    }

	    // Split the path into segments
	    String[] segments = path.split("/");

	    // Check if there are enough segments to remove the first two
	    if (segments.length <= 3) {
	        log.debug("Path has less than or equal to two segments, returning '/'");
	        return "/";
	    }

	    // Reconstruct the path from the third segment onward
	    StringBuilder remainingPath = new StringBuilder();
	    for (int i = 3; i < segments.length; i++) {
	        if (!segments[i].isEmpty()) { // Avoid empty segments
	            remainingPath.append("/").append(segments[i]);
	        }
	    }

	    String result = remainingPath.toString();
	    log.debug("Path after removing first two segments: {}", result);
	    return result;
	}

}