package es.alesqui.intelligence.service.unification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import es.alesqui.intelligence.annotation.HandleApiUnificationException;
import es.alesqui.intelligence.model.postman.Auth;
import es.alesqui.intelligence.model.postman.Collection;
import es.alesqui.intelligence.model.postman.Info;
import es.alesqui.intelligence.model.postman.Url;
import es.alesqui.intelligence.model.postman.Variable;
import es.alesqui.intelligence.model.unified.UnifiedApiDocument;
import es.alesqui.intelligence.model.unified.UnifiedAuthentication;
import es.alesqui.intelligence.util.UnificationUtils;

import java.util.*;

/**
 * Service responsible for processing and extracting information from Postman collections.
 * This service includes methods to merge metadata, global variables, authentication schemes, and other details
 * from Postman collections into a unified API document format.
 */
@Service
@Slf4j
public class PostmanProcessingService {

    /**
     * Merges information from a Postman collection into a unified API document builder.
     *
     * @param builder    The builder for the unified API document.
     * @param collection The Postman collection containing metadata, variables, and authentication.
     */
    @HandleApiUnificationException
    public void mergePostmanInfo(UnifiedApiDocument.UnifiedApiDocumentBuilder builder, Collection collection) {
    	if (builder == null) {
			throw new IllegalArgumentException("Builder cannot be null");
		}

		if (collection == null) {
			log.debug("Postman collection is null, skipping info merge");
			return;
		}

		log.debug("Merging Postman collection information");
		mergeCollectionMetadata(builder, collection);
		mergeGlobalVariables(builder, collection);
		mergeAuthenticationInfo(builder, collection);

		log.debug("Successfully completed Postman info merge");
    }

    /**
     * Merges metadata from the Postman collection into the unified API document builder.
     *
     * @param builder    The builder for the unified API document.
     * @param collection The Postman collection containing metadata.
     */
    @HandleApiUnificationException
    public void mergeCollectionMetadata(UnifiedApiDocument.UnifiedApiDocumentBuilder builder, Collection collection) {
    	if (collection.getInfo() == null) {
			log.debug("Postman collection info is null, skipping metadata merge");
			return;
		}

		Info info = collection.getInfo();
		log.debug("Processing Postman collection metadata");

		UnifiedApiDocument currentState = builder.build();

		if (currentState.getDescription() == null && info.getDescription() != null) {
			String description = UnificationUtils.extractDescription(info.getDescription());
			if (description != null && !description.trim().isEmpty()) {
				builder.description(description.trim());
				log.debug("Merged collection description from Postman");
			}
		} else {
			log.trace("Description already exists or Postman description is null");
		}

		if (info.getName() != null && !info.getName().trim().isEmpty()) {
			log.trace("Found collection name: {}", info.getName());
		}
    }

    /**
     * Merges global variables from the Postman collection into the unified API document builder.
     *
     * @param builder    The builder for the unified API document.
     * @param collection The Postman collection containing global variables.
     */
    @HandleApiUnificationException
    public void mergeGlobalVariables(UnifiedApiDocument.UnifiedApiDocumentBuilder builder, Collection collection) {
    	if (collection.getVariable() == null || collection.getVariable().isEmpty()) {
			log.debug("No variables found in Postman collection");
			return;
		}

		List<Variable> variables = collection.getVariable();
		log.debug("Processing {} global variables from Postman collection", variables.size());

		Map<String, Object> globalVariables = new HashMap<>();

		for (Variable var : variables) {
			if (var.getKey() != null && !var.getKey().trim().isEmpty()) {
				String key = var.getKey().trim();
				Object value = var.getValue() != null ? var.getValue() : "";

				globalVariables.put(key, value);
				log.trace("Added global variable: {} = {}", key, value);
			} else {
				log.warn("Skipping variable with null or empty key");
			}
		}

		if (!globalVariables.isEmpty()) {
			builder.globalVariables(globalVariables);
			log.debug("Successfully merged {} global variables", globalVariables.size());
		}
    }

    /**
     * Merges authentication information from the Postman collection into the unified API document builder.
     *
     * @param builder    The builder for the unified API document.
     * @param collection The Postman collection containing authentication configurations.
     */
    @HandleApiUnificationException
    public void mergeAuthenticationInfo(UnifiedApiDocument.UnifiedApiDocumentBuilder builder, Collection collection) {
    	UnifiedApiDocument currentState = builder.build();

		if (currentState.getAuthentication() == null && collection.getAuth() != null) {
			log.debug("Merging authentication from Postman collection");

			UnifiedAuthentication authentication = convertPostmanAuth(collection.getAuth());
			if (authentication != null) {
				builder.authentication(authentication);
				log.debug("Successfully merged Postman authentication");
			} else {
				log.debug("Postman auth conversion returned null");
			}
		} else {
			if (currentState.getAuthentication() != null) {
				log.trace("Authentication already exists, skipping Postman auth merge");
			} else {
				log.trace("No authentication found in Postman collection");
			}
		}
    }

    /**
     * Extracts the path from a Postman URL object, converting variables into a unified format.
     *
     * @param url The URL object (either a String or a Postman URL object).
     * @return The extracted path as a string.
     */
    @HandleApiUnificationException
    public String extractPathFromUrl(Object url) {
    	if (url instanceof String) {
			return extractPathFromString((String) url);
		} else if (url instanceof Url) {
			Url urlObj = (Url) url;
			if (urlObj.getRaw() != null) {
				return extractPathFromString(urlObj.getRaw());
			} else if (urlObj.getPath() != null) {
				return "/" + String.join("/", (List<String>) urlObj.getPath());
			}
		}
		log.debug("URL object is null or unsupported, returning default path '/'");
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

		log.debug("Extracted path from URL: {}", path);
		return path;
	}

    

    
    

    /**
     * Converts Postman authentication data into a unified authentication object.
     *
     * @param auth The Postman authentication object.
     * @return A unified authentication object.
     */
    @HandleApiUnificationException
    public UnifiedAuthentication convertPostmanAuth(Auth auth) {
    	if (auth == null || auth.getType() == null) {
			log.debug("Auth object or type is null, returning null");
			return null;
		}

		UnifiedAuthentication.UnifiedAuthenticationBuilder builder = UnifiedAuthentication.builder()
				.type(auth.getType());

		Map<String, String> attributes = new HashMap<>();

		switch (auth.getType().toLowerCase()) {
		case "basic":
			if (auth.getBasic() != null) {
				auth.getBasic().forEach(attr -> attributes.put(attr.getKey(), String.valueOf(attr.getValue())));
			}
			break;
		case "bearer":
			if (auth.getBearer() != null) {
				auth.getBearer().forEach(attr -> attributes.put(attr.getKey(), String.valueOf(attr.getValue())));
			}
			break;
		case "apikey":
			if (auth.getApikey() != null) {
				auth.getApikey().forEach(attr -> {
					if ("key".equals(attr.getKey())) {
						builder.name(String.valueOf(attr.getValue()));
					} else if ("in".equals(attr.getKey())) {
						builder.in(String.valueOf(attr.getValue()));
					}
					attributes.put(attr.getKey(), String.valueOf(attr.getValue()));
				});
			}
			break;
		default:
			log.warn("Unsupported auth type: {}", auth.getType());
			break;
		}

		builder.attributes(attributes);
		return builder.build();
    }

}

