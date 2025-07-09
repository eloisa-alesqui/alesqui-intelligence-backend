package es.alesqui.postmangpt.util.serialization;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.alesqui.postmangpt.model.postman.Url;
import es.alesqui.postmangpt.model.postman.QueryParam;
import es.alesqui.postmangpt.model.postman.Variable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Custom deserializer for the Url class.
 * This deserializer handles JSON fields that can either be a simple text string 
 * representing the raw URL or a complex object containing broken-down URL components.
 */
public class UrlDeserializer extends JsonDeserializer<Object> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Deserializes a JSON field into either a String or a Url object.
     *
     * @param jsonParser the JSON parser used to parse the input JSON
     * @param deserializationContext the context for deserialization
     * @return an Object which is either a String (if the field is textual) or a Url object (if the field is an object)
     * @throws IOException if the JSON field type is unrecognized or an error occurs during deserialization
     */
    @Override
    public Object deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
        // Parse the JSON field into a JsonNode for inspection
        JsonNode node = jsonParser.getCodec().readTree(jsonParser);

        // If the node is a textual value, treat it as the raw URL string
        if (node.isTextual()) {
            return Url.create(node.asText());
        }

        // If the node is an object, map it to a Url instance
        if (node.isObject()) {
            Url url = new Url();

            // Extract and set the raw URL if present
            if (node.has("raw")) {
                url.setRaw(node.get("raw").asText());
            }

            // Extract and set the protocol if present
            if (node.has("protocol")) {
                url.setProtocol(node.get("protocol").asText());
            }

            // Extract and set the host if present
            if (node.has("host")) {
                url.setHost(convertJsonArrayToList(node.get("host")));
            }

            // Extract and set the port if present
            if (node.has("port")) {
                url.setPort(node.get("port").asText());
            }

            // Extract and set the path if present
            if (node.has("path")) {
                url.setPath(convertJsonArrayToList(node.get("path")));
            }

            // Extract and set the query parameters if present
            if (node.has("query")) {
                List<QueryParam> queryParams = new ArrayList<>();
                for (JsonNode queryNode : node.get("query")) {
                    QueryParam queryParam = objectMapper.treeToValue(queryNode, QueryParam.class);
                    queryParams.add(queryParam);
                }
                url.setQuery(queryParams);
            }

            // Extract and set the hash if present
            if (node.has("hash")) {
                url.setHash(node.get("hash").asText());
            }

            // Extract and set the variables if present
            if (node.has("variable")) {
                List<Variable> variables = new ArrayList<>();
                for (JsonNode variableNode : node.get("variable")) {
                    Variable variable = objectMapper.treeToValue(variableNode, Variable.class);
                    variables.add(variable);
                }
                url.setVariable(variables);
            }

            return url;
        }

        // If the node type is neither textual nor an object, throw an exception
        throw new IOException("Unrecognized type for URL field");
    }

    /**
     * Converts a JSON array node into a list of strings.
     *
     * @param arrayNode the JSON array node
     * @return a list of strings
     */
    private List<String> convertJsonArrayToList(JsonNode arrayNode) {
        List<String> list = new ArrayList<>();
        if (arrayNode != null && arrayNode.isArray()) {
            for (JsonNode element : arrayNode) {
                list.add(element.asText());
            }
        }
        return list;
    }
}
