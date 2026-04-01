package es.alesqui.intelligence.service.chat.tools.support;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import es.alesqui.intelligence.model.api_spec.unified.UnifiedApiDocument;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedEndpoint;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedExample;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedMediaType;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedRequestBody;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedResponse;
import es.alesqui.intelligence.model.api_spec.unified.UnifiedSchema;

/**
 * Endpoint-related helpers shared across tools.
 * 
 * This utility class centralizes common logic used by API discovery and invocation
 * tools:
 * - Parsing operation identifiers written as METHOD:PATH.
 * - Normalizing and composing endpoint paths.
 * - Falling back to a synthetic operation identifier when the original is missing.
 * - Resolving schema references against an API document.
 * - Rendering request and response information in markdown-like text suitable for LLMs.
 * - Pretty-printing JSON and formatting example payloads.
 * 
 * All methods are static and side-effect free. The class is not intended to be
 * instantiated.
 */
public final class EndpointSupport {

    private EndpointSupport() {}

    /**
     * Simple pair of HTTP method and path as parsed from a METHOD:PATH string.
     * The method is uppercased using a root locale. The path is left as provided
     * by the caller and may be normalized separately when needed.
     */
    public record MethodPath(String method, String path) {}

    /**
     * Parses a candidate string that may represent an operation in METHOD:PATH or
     * METHOD PATH form. Examples: "GET:/artworks/search", "get /items/{id}".
     * The parse is tolerant to extra spaces around the separator.
     *
     * @param candidate text to parse
     * @return a MethodPath with uppercased method and raw path, or null if the
     *         input cannot be parsed into the expected two-part shape
     */
    public static MethodPath parseMethodPath(String candidate) {
        if (candidate == null) return null;
        String c = candidate.trim();
        String[] parts = c.split("\\s*[: ]\\s*", 2);
        if (parts.length == 2) {
            String method = parts[0].trim();
            String path = parts[1].trim();
            if (!path.isEmpty()) {
                return new MethodPath(method.toUpperCase(Locale.ROOT), path);
            }
        }
        return null;
    }

    /**
     * Produces a normalized HTTP path:
     * - Ensures the path starts with a single leading slash.
     * - Removes a trailing slash unless the path is the root "/".
     * - Returns "/" for null or blank input.
     *
     * @param path original path text
     * @return normalized path suitable for comparison and display
     */
    public static String normalizePath(String path) {
        if (path == null || path.isBlank()) return "/";
        String p = path.trim();
        if (!p.startsWith("/")) p = "/" + p;
        if (p.length() > 1 && p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p;
    }

    /**
     * Returns the endpoint operationId when present, otherwise returns a
     * synthetic identifier in the form METHOD:/normalized/path. This makes it
     * possible to reference operations consistently even when explicit
     * operation identifiers are missing from the source specification.
     *
     * @param endpoint the endpoint to inspect
     * @return original operationId or a synthetic METHOD:/path fallback
     */
    public static String getOperationIdOrDefault(UnifiedEndpoint endpoint) {
        String op = endpoint.getOperationId();
        if (StringUtils.isNotBlank(op)) return op;
        return endpoint.getMethod() + ":" + normalizePath(endpoint.getPath());
    }

    /**
     * Resolves an endpoint within an API document by operationId, using a three-step
     * fallback strategy:
     * 1. Exact match by operationId.
     * 2. Parse as METHOD:PATH and match by method + normalized path.
     * 3. Unique match by normalized path alone.
     *
     * @param api         the API document to search
     * @param operationId the operation identifier or METHOD:PATH string
     * @return the matching endpoint, or null if none was found
     */
    public static UnifiedEndpoint resolveEndpoint(UnifiedApiDocument api, String operationId) {
        if (api == null || api.getEndpoints() == null || operationId == null) return null;

        // Step 1: exact operationId match
        UnifiedEndpoint endpoint = api.getEndpoints().stream()
                .filter(e -> operationId.trim().equals(e.getOperationId()))
                .findFirst()
                .orElse(null);

        if (endpoint == null) {
            // Step 2: parse METHOD:PATH and match by method + normalized path
            MethodPath mp = parseMethodPath(operationId);
            if (mp != null) {
                String normPath = normalizePath(mp.path());
                endpoint = api.getEndpoints().stream()
                        .filter(e -> mp.method().equalsIgnoreCase(e.getMethod())
                                && normalizePath(e.getPath()).equalsIgnoreCase(normPath))
                        .findFirst()
                        .orElse(null);

                // Step 3: unique match by path alone
                if (endpoint == null) {
                    var byPath = api.getEndpoints().stream()
                            .filter(e -> normalizePath(e.getPath()).equalsIgnoreCase(normPath))
                            .toList();
                    if (byPath.size() == 1) {
                        endpoint = byPath.get(0);
                    }
                }
            }
        }

        return endpoint;
    }

    /**
     * Resolves a UnifiedSchema that uses a reference into the API document's
     * declared schemas. If a matching referenced schema is found, it is returned.
     * When the resolved schema has no title, the name extracted from the
     * reference is set as the title for better readability. If the schema is not
     * a reference or cannot be resolved, the input schema is returned unchanged.
     *
     * @param api the API document containing component schemas
     * @param schema the schema that may contain a reference
     * @return the resolved schema or the original schema when no resolution is possible
     */
    public static UnifiedSchema resolveSchemaRef(UnifiedApiDocument api, UnifiedSchema schema) {
        if (schema == null) return null;
        if (schema.getRef() != null && !schema.getRef().isBlank()) {
            String schemaName = schema.getRef().substring(schema.getRef().lastIndexOf('/') + 1);
            Map<String, UnifiedSchema> allApiSchemas = api.getSchemas();
            if (allApiSchemas != null && allApiSchemas.containsKey(schemaName)) {
                UnifiedSchema resolved = allApiSchemas.get(schemaName);
                if (resolved.getTitle() == null) {
                    resolved.setTitle(schemaName);
                }
                return resolved;
            }
        }
        return schema;
    }

    /**
     * Produces a detailed, human-friendly description of request parameters for
     * LLM consumption. Includes location, requirement, type, default, example,
     * allowed values, and an expanded schema rendered as markdown-like text.
     *
     * @param api the API document used to resolve schema references
     * @param parameters list of parameters to describe
     * @return a multi-line string suitable for inclusion in tool responses
     */
    public static String formatParametersDetailedForLLM(UnifiedApiDocument api, List<es.alesqui.intelligence.model.api_spec.unified.UnifiedParameter> parameters) {
        if (parameters == null || parameters.isEmpty()) return "No parameters.";
        StringBuilder sb = new StringBuilder();
        for (es.alesqui.intelligence.model.api_spec.unified.UnifiedParameter p : parameters) {
            sb.append("- Name: ").append(p.getName())
              .append(", In: ").append(p.getIn() != null ? p.getIn() : "")
              .append(", Required: ").append(p.isRequired())
              .append("\n");
            if (StringUtils.isNotBlank(p.getDescription())) {
                sb.append("  Description: ").append(p.getDescription()).append("\n");
            }
            if (StringUtils.isNotBlank(p.getType())) {
                sb.append("  Type: ").append(p.getType());
                if (StringUtils.isNotBlank(p.getFormat())) sb.append(" (").append(p.getFormat()).append(")");
                sb.append("\n");
            }
            if (p.getDefaultValue() != null) {
                sb.append("  Default: ").append(p.getDefaultValue()).append("\n");
            }
            if (p.getExample() != null) {
                sb.append("  Example: ").append(p.getExample()).append("\n");
            }
            if (p.getEnumValues() != null && !p.getEnumValues().isEmpty()) {
                sb.append("  Allowed values: ").append(p.getEnumValues().stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(", "))).append("\n");
            }
            if (p.getSchema() != null) {
                UnifiedSchema schema = resolveSchemaRef(api, p.getSchema());
                sb.append("  Schema:\n");
                sb.append("```markdown\n").append(schema.toMarkdown()).append("\n```\n");
            }
        }
        return sb.toString();
    }

    /**
     * Summarizes responses declared for an operation. For each status code it
     * lists a short description, content types, an expanded schema when
     * available, and example payloads. JSON is pretty-printed when possible.
     *
     * @param api the API document used to resolve schema references
     * @param responses map of HTTP status to response description
     * @param objectMapper Jackson mapper used for JSON pretty printing
     * @return a multi-line string summarizing responses
     */
    public static String formatResponsesForLLM(UnifiedApiDocument api, Map<String, UnifiedResponse> responses, ObjectMapper objectMapper) {
        if (responses == null || responses.isEmpty()) return "No responses documented.";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, UnifiedResponse> entry : responses.entrySet()) {
            String status = entry.getKey();
            UnifiedResponse resp = entry.getValue();
            sb.append("- ").append(status).append(": ").append(resp.getDescription() != null ? resp.getDescription() : "").append("\n");
            if (resp.getContent() != null && !resp.getContent().isEmpty()) {
                sb.append("  Content types: ").append(String.join(", ", resp.getContent().keySet())).append("\n");
                UnifiedMediaType mt = resp.getContent().get("application/json");
                if (mt == null) mt = resp.getContent().values().stream().findFirst().orElse(null);
                if (mt != null && mt.getSchema() != null) {
                    UnifiedSchema s = resolveSchemaRef(api, mt.getSchema());
                    sb.append("  Schema:\n");
                    sb.append("```markdown\n").append(s.toMarkdown()).append("\n```\n");
                    String examplesBlock = formatMediaTypeExamples(mt, objectMapper);
                    if (!examplesBlock.isEmpty()) {
                        sb.append(examplesBlock);
                    }
                }
            }
        }
        return sb.toString();
    }

    /**
     * Renders the request body schema for an operation and appends available
     * examples. If no JSON content type is present, the first available media
     * type is used. When no schema is provided, a short message is returned.
     *
     * @param api the API document used to resolve schema references
     * @param requestBody the request body definition to render
     * @param objectMapper Jackson mapper used for JSON pretty printing
     * @return a markdown-like block with schema and examples, or a short message when absent
     */
    public static String renderRequestBodySchemaMarkdown(UnifiedApiDocument api, UnifiedRequestBody requestBody, ObjectMapper objectMapper) {
        if (requestBody == null || requestBody.getContent() == null || requestBody.getContent().isEmpty()) {
            return "No request body.";
        }
        UnifiedMediaType mediaType = requestBody.getContent().get("application/json");
        if (mediaType == null) {
            mediaType = requestBody.getContent().values().stream().findFirst().orElse(null);
        }
        if (mediaType == null || mediaType.getSchema() == null) {
            return "No usable schema found for request body.";
        }
        UnifiedSchema schemaToRender = resolveSchemaRef(api, mediaType.getSchema());
        StringBuilder sb = new StringBuilder();
        sb.append("```markdown\n").append(schemaToRender.toMarkdown()).append("\n```\n");
        sb.append(formatMediaTypeExamples(mediaType, objectMapper));
        return sb.toString();
    }

    /**
     * Formats example payloads for a media type. Supports a single unnamed
     * example and multiple named examples. Values that look like JSON are
     * pretty-printed, otherwise the raw string representation is used.
     *
     * @param mediaType the media type that may contain examples
     * @param objectMapper Jackson mapper used for JSON pretty printing
     * @return a multi-line string with examples section, or an empty string when none
     */
    public static String formatMediaTypeExamples(UnifiedMediaType mediaType, ObjectMapper objectMapper) {
        StringBuilder sb = new StringBuilder();
        Object ex = mediaType.getExample();
        if (ex != null) {
            sb.append("  Example:\n");
            sb.append("```json\n").append(prettyJson(objectMapper, ex)).append("\n```\n");
        }
        Map<String, UnifiedExample> examples = mediaType.getExamples();
        if (examples != null && !examples.isEmpty()) {
            sb.append("  Examples:\n");
            for (Map.Entry<String, UnifiedExample> e : examples.entrySet()) {
                UnifiedExample ue = e.getValue();
                String name = ue.getName() != null ? ue.getName() : e.getKey();
                sb.append("  - ").append(name);
                if (ue.getSummary() != null) sb.append(" — ").append(ue.getSummary());
                sb.append("\n");
                if (ue.getDescription() != null) sb.append("    ").append(ue.getDescription()).append("\n");
                if (ue.getExternalValue() != null) sb.append("    external: ").append(ue.getExternalValue()).append("\n");
                if (ue.getValue() != null) {
                    sb.append("```json\n").append(prettyJson(objectMapper, ue.getValue())).append("\n```\n");
                }
            }
        }
        return sb.toString();
    }

    /**
     * Attempts to pretty-print a value as JSON. If the value is a string, it is
     * first parsed as JSON; otherwise it is serialized directly. When parsing or
     * serialization fails, the string representation of the input is returned.
     *
     * @param objectMapper Jackson mapper used for reading and writing JSON
     * @param value the value to format
     * @return pretty-printed JSON or a fallback string
     */
    public static String prettyJson(ObjectMapper objectMapper, Object value) {
        try {
            if (value instanceof String) {
                String s = (String) value;
                Object parsed = objectMapper.readValue(s, Object.class);
                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsed);
            }
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }
}
