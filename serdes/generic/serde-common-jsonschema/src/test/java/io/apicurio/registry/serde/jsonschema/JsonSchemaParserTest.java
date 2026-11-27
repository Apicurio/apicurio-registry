package io.apicurio.registry.serde.jsonschema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.data.Metadata;
import io.apicurio.registry.resolver.data.Record;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonSchemaParserTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static <T> Record<T> recordOf(T payload) {
        return new Record<>() {
            @Override
            public Metadata metadata() {
                return null;
            }

            @Override
            public T payload() {
                return payload;
            }
        };
    }

    @Test
    void testSupportsExtractSchemaFromData() {
        JsonSchemaParser<Object> parser = new JsonSchemaParser<>();
        assertTrue(parser.supportsExtractSchemaFromData());
    }

    @Test
    void testGetSchemaFromDataNullPayload() {
        JsonSchemaParser<Object> parser = new JsonSchemaParser<>();
        assertNull(parser.getSchemaFromData(null));
        assertNull(parser.getSchemaFromData(recordOf(null)));
    }

    @Test
    void testGetSchemaFromDataJsonNode() throws Exception {
        JsonSchemaParser<JsonNode> parser = new JsonSchemaParser<>();
        ObjectNode node = MAPPER.createObjectNode();
        node.put("firstName", "John");
        node.put("age", 30);
        node.put("active", true);
        node.put("score", 9.5);
        node.putArray("tags").add("a").add("b");
        ObjectNode nested = node.putObject("address");
        nested.put("city", "Boston");

        ParsedSchema<JsonSchema> parsed = parser.getSchemaFromData(recordOf(node));
        assertNotNull(parsed);
        assertNotNull(parsed.getParsedSchema());
        assertNotNull(parsed.getRawSchema());

        JsonNode schemaTree = MAPPER.readTree(new String(parsed.getRawSchema(), StandardCharsets.UTF_8));
        assertEquals("http://json-schema.org/draft-07/schema#", schemaTree.get("$schema").asText());
        assertEquals("object", schemaTree.get("type").asText());
        JsonNode props = schemaTree.get("properties");
        assertNotNull(props);
        assertEquals("string", props.get("firstName").get("type").asText());
        assertEquals("integer", props.get("age").get("type").asText());
        assertEquals("boolean", props.get("active").get("type").asText());
        assertEquals("number", props.get("score").get("type").asText());
        assertEquals("array", props.get("tags").get("type").asText());
        assertEquals("string", props.get("tags").get("items").get("type").asText());
        assertEquals("object", props.get("address").get("type").asText());
        assertEquals("string", props.get("address").get("properties").get("city").get("type").asText());

        assertTrue(parsed.getParsedSchema().validate(node).isEmpty());
    }

    @Test
    void testGetSchemaFromDataMap() throws Exception {
        JsonSchemaParser<Map<String, Object>> parser = new JsonSchemaParser<>();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", "123");
        payload.put("count", 42);
        payload.put("items", List.of(1, 2, 3));

        ParsedSchema<JsonSchema> parsed = parser.getSchemaFromData(recordOf(payload), false);
        assertNotNull(parsed);
        assertNotNull(parsed.getParsedSchema());

        JsonNode schemaTree = MAPPER.readTree(new String(parsed.getRawSchema(), StandardCharsets.UTF_8));
        assertEquals("object", schemaTree.get("type").asText());
        assertEquals("string", schemaTree.get("properties").get("id").get("type").asText());
        assertEquals("integer", schemaTree.get("properties").get("count").get("type").asText());
        assertEquals("array", schemaTree.get("properties").get("items").get("type").asText());

        JsonNode valueTree = MAPPER.valueToTree(payload);
        assertTrue(parsed.getParsedSchema().validate(valueTree).isEmpty());
    }
}
