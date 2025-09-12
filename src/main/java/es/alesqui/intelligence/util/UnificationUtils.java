package es.alesqui.intelligence.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import es.alesqui.intelligence.annotation.HandleApiUnificationException;
import es.alesqui.intelligence.model.api_spec.postman.Body;
import es.alesqui.intelligence.model.api_spec.postman.Description;
import es.alesqui.intelligence.model.api_spec.postman.FormParameter;
import es.alesqui.intelligence.model.api_spec.postman.Header;
import es.alesqui.intelligence.model.api_spec.postman.QueryParam;
import es.alesqui.intelligence.model.api_spec.postman.Request;
import es.alesqui.intelligence.model.api_spec.postman.Url;
import es.alesqui.intelligence.model.api_spec.postman.Variable;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedMediaType;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedParameter;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedRequestBody;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedResponse;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UnificationUtils {
	
	/**
    * Converts Swagger parameters into a unified format.
    * 
    * @param parameters The list of Swagger parameters.
    * @return A list of unified parameters.
    */
   @HandleApiUnificationException
   public static List<UnifiedParameter> convertSwaggerParameters(List<Parameter> parameters) {
       if (parameters == null || parameters.isEmpty()) {
           log.debug("No parameters to convert, returning empty list");
           return new ArrayList<>();
       }

       return parameters.stream()
               .map(param -> UnifiedParameter.builder()
                       .name(param.getName())
                       .in(param.getIn())
                       .description(param.getDescription())
                       .required(Boolean.TRUE.equals(param.getRequired()))
                       .schema(param.getSchema() != null ? convertSwaggerSchema(param.getSchema()) : null)
                       .build())
               .collect(Collectors.toList());
   }

   /**
    * Converts a Swagger schema into a unified schema.
    * 
    * @param swaggerSchema The Swagger schema object.
    * @return A unified schema object.
    */
   @HandleApiUnificationException
   public static UnifiedSchema convertSwaggerSchema(io.swagger.v3.oas.models.media.Schema<?> swaggerSchema) {
       if (swaggerSchema == null) {
           return null;
       }

       // Build the UnifiedSchema object
       UnifiedSchema.UnifiedSchemaBuilder builder = UnifiedSchema.builder()
               .type(swaggerSchema.getType())
               .format(swaggerSchema.getFormat())
               .title(swaggerSchema.getTitle())
               .description(swaggerSchema.getDescription())
               .example(swaggerSchema.getExample())
               .defaultValue(swaggerSchema.getDefault())
               .nullable(swaggerSchema.getNullable())
               .readOnly(swaggerSchema.getReadOnly())
               .writeOnly(swaggerSchema.getWriteOnly())
               .deprecated(swaggerSchema.getDeprecated());

       // Handle $ref by removing the prefix
       if (swaggerSchema.get$ref() != null) {
           String ref = swaggerSchema.get$ref();
           if (ref.startsWith("#/components/schemas/")) {
               ref = ref.replace("#/components/schemas/", ""); // Remove the prefix
           }
           builder.ref(ref); // Set the cleaned ref
       }

       // Handle numeric validations
       builder.minimum(swaggerSchema.getMinimum())
              .maximum(swaggerSchema.getMaximum())
              .exclusiveMinimum(swaggerSchema.getExclusiveMinimum())
              .exclusiveMaximum(swaggerSchema.getExclusiveMaximum())
              .multipleOf(swaggerSchema.getMultipleOf());

       // Handle string validations
       builder.minLength(swaggerSchema.getMinLength())
              .maxLength(swaggerSchema.getMaxLength())
              .pattern(swaggerSchema.getPattern());

       // Handle array validations
       builder.minItems(swaggerSchema.getMinItems())
              .maxItems(swaggerSchema.getMaxItems())
              .uniqueItems(swaggerSchema.getUniqueItems());

       // Handle object properties
       if (swaggerSchema.getProperties() != null) {
           Map<String, UnifiedSchema> properties = new HashMap<>();
           swaggerSchema.getProperties().forEach((name, property) -> 
               properties.put(name, convertSwaggerSchema(property))
           );
           builder.properties(properties);
       }
       builder.required(swaggerSchema.getRequired())
              .minProperties(swaggerSchema.getMinProperties())
              .maxProperties(swaggerSchema.getMaxProperties());

       // Handle additional properties
       if (swaggerSchema.getAdditionalProperties() instanceof io.swagger.v3.oas.models.media.Schema) {
           builder.additionalPropertiesSchema(convertSwaggerSchema((io.swagger.v3.oas.models.media.Schema<?>) swaggerSchema.getAdditionalProperties()));
       } else {
           builder.additionalProperties(swaggerSchema.getAdditionalProperties());
       }

       // Handle items for arrays
       if (swaggerSchema.getItems() != null) {
           builder.items(convertSwaggerSchema(swaggerSchema.getItems()));
       }

       // Handle composition
       if (swaggerSchema.getAllOf() != null) {
           List<UnifiedSchema> allOf = new ArrayList<>();
           swaggerSchema.getAllOf().forEach(schema -> allOf.add(convertSwaggerSchema(schema)));
           builder.allOf(allOf);
       }

       if (swaggerSchema.getOneOf() != null) {
           List<UnifiedSchema> oneOf = new ArrayList<>();
           swaggerSchema.getOneOf().forEach(schema -> oneOf.add(convertSwaggerSchema(schema)));
           builder.oneOf(oneOf);
       }

       if (swaggerSchema.getAnyOf() != null) {
           List<UnifiedSchema> anyOf = new ArrayList<>();
           swaggerSchema.getAnyOf().forEach(schema -> anyOf.add(convertSwaggerSchema(schema)));
           builder.anyOf(anyOf);
       }

       if (swaggerSchema.getNot() != null) {
           builder.not(convertSwaggerSchema(swaggerSchema.getNot()));
       }

       // Handle extensions (e.g., specVersion, exampleSetFlag)
       if (swaggerSchema.getExtensions() != null) {
           builder.extensions(swaggerSchema.getExtensions());
       }

       return builder.build();
   }
   
   /**
	 * Converts a Swagger request body into a unified request body.
	 * 
	 * @param requestBody The Swagger request body.
	 * @return A unified request body object.
	 */
	@HandleApiUnificationException
	public static UnifiedRequestBody convertSwaggerRequestBody(RequestBody requestBody) {
		if (requestBody == null) {
			log.debug("RequestBody is null, returning null");
			return null;
		}

		UnifiedRequestBody.UnifiedRequestBodyBuilder builder = UnifiedRequestBody.builder()
				.description(requestBody.getDescription()).required(Boolean.TRUE.equals(requestBody.getRequired()));

		if (requestBody.getContent() != null) {
			Map<String, UnifiedMediaType> content = new HashMap<>();
			requestBody.getContent().forEach((mediaType, media) -> content.put(mediaType, convertMediaType(media)));
			builder.content(content);
		}

		return builder.build();
	}
	
	/**
     * Converts Swagger API responses into a unified format.
     * 
     * @param responses The Swagger API responses.
     * @return A map of unified responses.
     */
    @HandleApiUnificationException
    public static Map<String, UnifiedResponse> convertSwaggerResponses(io.swagger.v3.oas.models.responses.ApiResponses responses) {
        if (responses == null || responses.isEmpty()) {
            log.debug("No responses to convert, returning empty map");
            return new HashMap<>();
        }

        Map<String, UnifiedResponse> result = new HashMap<>();
        responses.forEach((code, response) -> result.put(code, convertSwaggerResponse(response)));
        return result;
    }

    /**
     * Converts a single Swagger API response into a unified response.
     * 
     * @param response The Swagger API response.
     * @return A unified response object.
     */
    @HandleApiUnificationException
    public static UnifiedResponse convertSwaggerResponse(ApiResponse response) {
        UnifiedResponse.UnifiedResponseBuilder builder = UnifiedResponse.builder()
                .description(response.getDescription());

        if (response.getContent() != null) {
            Map<String, UnifiedMediaType> content = new HashMap<>();
            response.getContent().forEach((mediaType, media) -> content.put(mediaType, UnificationUtils.convertMediaType(media)));
            builder.content(content);
        }

        if (response.getHeaders() != null) {
            Map<String, String> headers = new HashMap<>();
            response.getHeaders().forEach((name, header) -> headers.put(name, header.getDescription()));
            builder.headers(headers);
        }

        return builder.build();
    }
    
    /**
     * Extracts headers from a Postman request and converts them into a map.
     *
     * @param request The Postman request containing header information.
     * @return A map of headers or null if no valid headers are found.
     */
    @HandleApiUnificationException
    public static Map<String, String> extractPostmanHeaders(Request request) {
    	if (request == null || request.getHeader() == null) {
			log.debug("Request or headers are null, returning null");
			return null;
		}

		log.debug("Processing {} headers from Postman request", request.getHeader().size());

		Map<String, String> headers = new HashMap<>();
		for (Header header : request.getHeader()) {
			if (!Boolean.TRUE.equals(header.getDisabled())) {
				if (header.getKey() != null && !header.getKey().trim().isEmpty()) {
					headers.put(header.getKey().trim(), header.getValue() != null ? header.getValue() : "");
					log.trace("Added header: {} = {}", header.getKey(), header.getValue());
				} else {
					log.warn("Skipping header with null or empty key");
				}
			} else {
				log.trace("Skipping disabled header: {}", header.getKey());
			}
		}

		if (headers.isEmpty()) {
			log.debug("No valid headers found, returning null");
			return null;
		}

		return headers;
    }
    
    /**
     * Extracts the request body from a Postman request object and converts it into a unified format.
     *
     * @param request The Postman request containing the body information.
     * @return A unified request body object or null if no body is present.
     */
    @HandleApiUnificationException
    public static UnifiedRequestBody extractPostmanRequestBody(UnifiedRequestBody unifiedRequestBody, Request request) {
    	if (request == null || request.getBody() == null) {
			log.debug("Request or body is null, no request body to extract");
			return null;
		}

		Body body = request.getBody();
		String bodyMode = body.getMode();
		log.debug("Processing request body with mode: {}", bodyMode);

		UnifiedRequestBody.UnifiedRequestBodyBuilder builder = UnifiedRequestBody.builder().required(true);
		Map<String, UnifiedMediaType> content = unifiedRequestBody.getContent();

		if (bodyMode == null) {
			log.warn("Body mode is null, cannot process request body");
			return null;
		}

		switch (bodyMode.toLowerCase().trim()) {
		case "raw":
			log.debug("Processing raw body content");
			processRawBody(body, request, content);
			break;

		case "formdata":
			log.debug("Processing form data body content");
			processFormDataBody(body, content);
			break;

		case "urlencoded":
			log.debug("Processing URL-encoded body content");
			processUrlEncodedBody(body, content);
			break;

		default:
			log.warn("Unsupported body mode: {}", bodyMode);
			processRawBody(body, request, content); // Fallback to raw
			break;
		}

		if (content.isEmpty()) {
			log.warn("No content was extracted from request body");
			return null;
		}

		builder.content(content);
		UnifiedRequestBody result = builder.build();
		log.debug("Successfully extracted request body with {} content types", content.size());
		return result;
    }
    
    /**
     * Processes a raw body from a Postman request and adds it to the content map.
     * 
     * @param body    The body object containing raw content. Must not be null.
     * @param request The Postman request providing additional context like headers. Must not be null.
     * @param content The map where the processed content is added. Must not be null.
     */
    @HandleApiUnificationException
    public static void processRawBody(Body body, Request request, Map<String, UnifiedMediaType> content) {
        // Validate inputs
        if (body == null || request == null || content == null) {
            log.error("Invalid input: body, request, or content is null");
            throw new IllegalArgumentException("Body, request, and content must not be null");
        }

        // Determine content type
        String contentType = guessContentType(request.getHeader());
        log.debug("Determined content type for raw body: {}", contentType);

        // Extract raw content
        String rawContent = body.getRaw();
        if (rawContent == null) {
            log.debug("Raw body content is null, defaulting to empty string");
            rawContent = "";
        }

        // Retrieve existing UnifiedMediaType
        UnifiedMediaType mediaType = content.get(contentType);
        if (mediaType == null) {
            log.debug("No UnifiedMediaType found for content type: {}, skipping processing", contentType);
            return; // Do nothing if mediaType is not found
        }

        // Set example in media type
        mediaType.setExample(rawContent);
        log.debug("Set example for content type {}: {}", contentType, rawContent);
    }
	
	/**
	 * Processes form data from a Postman request body and adds it to the content map.
	 * 
	 * @param body    The body object containing form data.
	 * @param content The map where the processed content is added.
	 */
	@HandleApiUnificationException
	public static void processFormDataBody(Body body, Map<String, UnifiedMediaType> content) {
	    if (body == null || content == null) {
	        log.warn("Body or content is null, skipping processing");
	        return;
	    }

	    // Create schema from form data
	    UnifiedSchema schema = createFormSchema(body.getFormdata(), "formdata");
	    if (CollectionUtils.isEmpty(schema.getProperties())) {
	        log.debug("No properties found in schema, skipping content update");
	        return;
	    }

	    // Define the content type for form data
	    String formContentType = "multipart/form-data";

	    // Retrieve the existing media type
	    UnifiedMediaType mediaType = content.get(formContentType);
	    if (mediaType == null || mediaType.getSchema() == null) {
	        log.debug("No UnifiedMediaType or schema found for content type: {}", formContentType);
	        return;
	    }

	    // Update example values in the existing schema
	    schema.getProperties().forEach((key, newProperty) -> {
	        UnifiedSchema existingProperty = mediaType.getSchema().getProperties().get(key);
	        if (existingProperty != null) {
	            existingProperty.setExample(newProperty.getExample());
	            log.debug("Updated example for property: {}", key);
	        }
	    });
	}

	/**
	 * Processes URL-encoded data from a Postman request body and adds it to the
	 * content map.
	 * 
	 * @param body    The body object containing URL-encoded data.
	 * @param content The map where the processed content is added.
	 */
	@HandleApiUnificationException
	public static void processUrlEncodedBody(Body body, Map<String, UnifiedMediaType> content) {
		UnifiedSchema schema = createFormSchema(body.getUrlencoded(), "urlencoded");

		String formContentType = "application/x-www-form-urlencoded";

		UnifiedMediaType mediaType = UnifiedMediaType.builder().schema(schema).build();
		content.put(formContentType, mediaType);
		log.trace("Added URL-encoded content with {} properties",
				schema.getProperties() != null ? schema.getProperties().size() : 0);
	}
	
	/**
     * Extracts parameters from a Postman request object, including query parameters and path variables.
     *
     * @param request The Postman request containing URL and parameter information.
     * @return A list of unified parameters extracted from the request.
     */
    @HandleApiUnificationException
    public static List<UnifiedParameter> extractPostmanParameters(Request request) {
    	if (request == null || request.getUrl() == null) {
			log.debug("Request or URL is null, returning empty parameter list");
			return new ArrayList<>();
		}

		List<UnifiedParameter> parameters = new ArrayList<>();

		// Extract query parameters
		if (request.getUrl() instanceof Url) {
			Url url = (Url) request.getUrl();
			if (url.getQuery() != null) {
				for (QueryParam param : url.getQuery()) {
					if (!Boolean.TRUE.equals(param.getDisabled())) {
						parameters.add(UnifiedParameter.builder().name(param.getKey()).in("query")
								.description(UnificationUtils.extractDescription(param.getDescription())).required(false)
								.example(param.getValue()).build());
						log.trace("Added query parameter: {}", param.getKey());
					} else {
						log.trace("Skipping disabled query parameter: {}", param.getKey());
					}
				}
			}

			// Extract path variables
			if (url.getVariable() != null) {
				for (Variable var : url.getVariable()) {
					parameters.add(UnifiedParameter.builder().name(var.getKey() != null ? var.getKey() : var.getId())
							.in("path").description(UnificationUtils.extractDescription(var.getDescription())).required(true)
							.example(var.getValue()).build());
					log.trace("Added path variable: {}", var.getKey());
				}
			}
		}

		log.debug("Extracted {} parameters from Postman request", parameters.size());
		return parameters;
    }
	
    /**
     * Creates a unified schema from form data, supporting both form-data and URL-encoded modes.
     * 
     * @param formData The list of form parameters.
     * @param mode     The mode of the form data (formdata or urlencoded).
     * @return A unified schema representing the form data.
     */
    @HandleApiUnificationException
    public static UnifiedSchema createFormSchema(List<?> formData, String mode) {
        if (CollectionUtils.isEmpty(formData)) {
            log.debug("No {} parameters found", mode);
            return UnifiedSchema.builder().type("object").properties(new HashMap<>()).build();
        }

        log.debug("Processing {} {} parameters", formData.size(), mode);

        // Initialize the schema
        UnifiedSchema schema = UnifiedSchema.builder()
            .type("object")
            .properties(new HashMap<>())
            .build();

        // Process each form parameter
        formData.stream()
            .filter(FormParameter.class::isInstance)
            .map(FormParameter.class::cast)
            .forEach(formParam -> processFormParameter(formParam, schema));

        log.debug("Created schema with {} properties for {} data", schema.getProperties().size(), mode);
        return schema;
    }
	
	/**
	 * Processes an individual form parameter and adds it to the schema.
	 * 
	 * @param formParam The form parameter to process.
	 * @param schema    The schema to which the parameter is added.
	 */
	@HandleApiUnificationException
	public static void processFormParameter(FormParameter formParam, UnifiedSchema schema) {
	    if (formParam == null || StringUtils.isBlank(formParam.getKey())) {
	        log.warn("Skipping form parameter with null or empty key");
	        return;
	    }

	    String paramKey = formParam.getKey().trim();
	    String paramValue = formParam.getValue();

	    // Build the property schema
	    UnifiedSchema property = UnifiedSchema.builder()
	        .type("string")
	        .example(StringUtils.isNotBlank(paramValue) ? paramValue : null)
	        .build();

	    // Add the property to the schema
	    schema.getProperties().put(paramKey, property);
	    log.debug("Added form parameter: {} = {}", paramKey, paramValue);
	}
	
	/**
     * Converts a Swagger media type into a unified media type.
     * 
     * @param media The Swagger media type.
     * @return A unified media type object.
     */
    @HandleApiUnificationException
    public static UnifiedMediaType convertMediaType(io.swagger.v3.oas.models.media.MediaType media) {
        return UnifiedMediaType.builder()
                .schema(media.getSchema() != null ? UnificationUtils.convertSwaggerSchema(media.getSchema()) : null)
                .example(media.getExample())
                .build();
    }
    
    /**
	 * Extracts a description from a given object, supporting both String and
	 * Description types.
	 * 
	 * @param description The object containing the description.
	 * @return The extracted description as a string, or null if not available.
	 */
	@HandleApiUnificationException
	public static String extractDescription(Object description) {
		if (description == null) {
			log.trace("Description object is null");
			return null;
		}

		log.trace("Extracting description from object of type: {}", description.getClass().getSimpleName());

		if (description instanceof String) {
			return (String) description;
		}

		if (description instanceof Description) {
			return ((Description) description).getContent();
		}

		log.warn("Unsupported description type: {}", description.getClass().getName());
		return description.toString();
	}
	
	/**
	 * Determines the content type of a request body based on the headers.
	 * 
	 * @param headers The list of headers from which to determine the content type.
	 * @return The determined content type or a default of application/json.
	 */
	@HandleApiUnificationException
	public static String guessContentType(List<Header> headers) {
	    // Validate input
	    if (headers == null || headers.isEmpty()) {
	        log.debug("No headers provided for content type detection, using default");
	        return "application/json";
	    }

	    log.debug("Searching for Content-Type header among {} headers", headers.size());

	    // Search for the Content-Type header
	    return headers.stream()
	        .filter(header -> header != null && "Content-Type".equalsIgnoreCase(header.getKey()))
	        .map(Header::getValue)
	        .filter(value -> value != null && !value.trim().isEmpty())
	        .findFirst()
	        .map(String::trim)
	        .orElseGet(() -> {
	            log.debug("No valid Content-Type header found, defaulting to application/json");
	            return "application/json";
	        });
	}

}
