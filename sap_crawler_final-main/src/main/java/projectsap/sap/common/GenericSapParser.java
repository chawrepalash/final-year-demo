package projectsap.sap.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.openapitools.jackson.nullable.JsonNullableModule;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Universal Generic SAP Parser.
 * Automatically converts raw SAP JSON payloads into strongly-typed POJOs
 * without any hardcoded field-by-field mapping logic.
 */
public class GenericSapParser {

    private static final ObjectMapper MAPPER = createObjectMapper();

    public static ObjectMapper getMapper() {
        return MAPPER;
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        om.registerModule(new JsonNullableModule());
        // Tolerate unknown/additional fields so that SAP API changes never break deserialization
        om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // Tolerate empty strings as null objects
        om.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
        // Accept single value where array is expected
        om.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        return om;
    }

    /**
     * Automatically deserializes a JSON object string into the target POJO class.
     *
     * @param json        Raw JSON string from SAP API.
     * @param targetClass The POJO class to map into.
     * @param <T>         The POJO type.
     * @return Deserialized POJO instance.
     * @throws JsonProcessingException If JSON is invalid or incompatible.
     */
    public static <T> T parseObject(String json, Class<T> targetClass) throws JsonProcessingException {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        return MAPPER.readValue(json, targetClass);
    }

    /**
     * Automatically deserializes a JSON array or wrapped SAP payload into a List of target POJOs.
     * Handles common SAP response wrappers automatically:
     * - Top-level JSON arrays: [ {...}, {...} ]
     * - REST wrapped objects: { "bills": [ {...} ] }, { "value": [ {...} ] }, { "items": [ {...} ] }
     * - OData wrapped responses: { "d": { "results": [ {...} ] } }
     *
     * @param json        Raw JSON string from SAP API.
     * @param targetClass The POJO class of individual items.
     * @param <T>         The POJO type.
     * @return List of deserialized POJOs.
     * @throws IOException If parsing fails.
     */
    public static <T> List<T> parseList(String json, Class<T> targetClass) throws IOException {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }

        JsonNode root = MAPPER.readTree(json);
        JsonNode arrayNode = extractArrayNode(root);

        if (arrayNode == null || !arrayNode.isArray()) {
            // If the payload is a single object rather than an array, deserialize that single object
            T singleItem = MAPPER.treeToValue(root, targetClass);
            return singleItem != null ? List.of(singleItem) : Collections.emptyList();
        }

        List<T> resultList = new ArrayList<>();
        for (JsonNode itemNode : arrayNode) {
            T item = MAPPER.treeToValue(itemNode, targetClass);
            if (item != null) {
                resultList.add(item);
            }
        }
        return resultList;
    }

    /**
     * Serializes any POJO back to a clean JSON string.
     *
     * @param object The POJO object.
     * @return JSON string.
     * @throws JsonProcessingException If serialization fails.
     */
    public static String toJson(Object object) throws JsonProcessingException {
        return MAPPER.writeValueAsString(object);
    }

    /**
     * Helper to detect and extract the inner array node from SAP-wrapped responses.
     */
    public static JsonNode extractArrayNode(JsonNode root) {
        if (root == null) {
            return null;
        }
        if (root.isArray()) {
            return root;
        }

        // Standard SAP REST wrappers
        String[] wrapperKeys = {"bills", "subscriptions", "value", "items", "data", "results", "content"};
        for (String key : wrapperKeys) {
            if (root.has(key) && root.get(key).isArray()) {
                return root.get(key);
            }
        }

        // SAP OData wrappers { "d": { "results": [ ... ] } }
        if (root.has("d")) {
            JsonNode dNode = root.get("d");
            if (dNode.has("results") && dNode.get("results").isArray()) {
                return dNode.get("results");
            }
            if (dNode.isArray()) {
                return dNode;
            }
        }

        return null;
    }
}
