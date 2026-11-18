package io.apicurio.registry.serde.jsonschema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.ParsedSchemaImpl;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonSchemaDeserializerRuleRecordTest {

    private static final String V2 = "{\"$schema\":\"http://json-schema.org/draft-07/schema#\","
            + "\"type\":\"object\",\"required\":[\"orderId\",\"currency\"],\"properties\":{"
            + "\"orderId\":{\"type\":\"string\"},"
            + "\"totalAmount\":{\"type\":\"number\"},"
            + "\"items\":{\"type\":\"array\",\"items\":{\"type\":\"object\"}},"
            + "\"note\":{\"type\":[\"string\",\"null\"]},"
            + "\"currency\":{\"type\":\"string\"}}}";

    private static final String RECORD = "{\"orderId\":\"A1\",\"totalAmount\":99.99,"
            + "\"items\":[{\"sku\":\"X\",\"quantity\":2}],\"note\":null}";

    private final ObjectMapper mapper = new ObjectMapper();

    public static class Order {
        public String orderId;
        public double totalAmount;
        public String currency;
    }

    private static <T> JsonSchemaDeserializer<T> deserializer(boolean validationEnabled) {
        JsonSchemaDeserializer<T> deserializer = new JsonSchemaDeserializer<>(null, validationEnabled);
        deserializer.setObjectMapper(new ObjectMapper());
        return deserializer;
    }

    private static ParsedSchema<JsonSchema> v2() {
        byte[] raw = V2.getBytes(StandardCharsets.UTF_8);
        return new ParsedSchemaImpl<JsonSchema>().setRawSchema(raw)
                .setParsedSchema(new JsonSchemaParser<>().parseSchema(raw, Map.of()));
    }

    @Test
    void jsonNodeRoundTripsThroughTheRuleRecord() throws Exception {
        JsonSchemaDeserializer<JsonNode> deserializer = deserializer(true);
        JsonNode original = mapper.readTree(RECORD);

        Map<String, Object> map = deserializer.toRuleRecord(original);
        assertEquals("A1", map.get("orderId"));
        assertEquals(99.99, map.get("totalAmount"));
        assertEquals(List.of(Map.of("sku", "X", "quantity", 2)), map.get("items"));
        assertTrue(map.containsKey("note"));

        Map<String, Object> migrated = new LinkedHashMap<>(map);
        migrated.put("currency", "USD");
        JsonNode result = deserializer.fromRuleRecord(migrated, v2(), original);

        assertEquals(mapper.readTree(RECORD.replace("}]", "}],\"currency\":\"USD\"")), result);
        assertEquals("USD", result.get("currency").asText());
        assertFalse(original.has("currency"));
    }

    @Test
    void configuredClassIsConvertedBackToThatClass() {
        JsonSchemaDeserializer<Order> deserializer = deserializer(true);
        Order original = new Order();
        original.orderId = "A1";
        original.totalAmount = 10.5;

        Map<String, Object> map = new LinkedHashMap<>(deserializer.toRuleRecord(original));
        assertEquals("A1", map.get("orderId"));
        map.put("currency", "USD");
        Order result = deserializer.fromRuleRecord(map, v2(), original);

        assertEquals("A1", result.orderId);
        assertEquals(10.5, result.totalAmount);
        assertEquals("USD", result.currency);
    }

    @Test
    void migratedRecordIsValidatedAgainstTheTargetSchema() throws Exception {
        JsonNode original = mapper.readTree(RECORD);
        Map<String, Object> withoutCurrency = deserializer(true).toRuleRecord(original);

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> JsonSchemaDeserializerRuleRecordTest.<JsonNode>deserializer(true)
                        .fromRuleRecord(withoutCurrency, v2(), original));
        assertTrue(e.getMessage().contains("currency"), e.getMessage());

        JsonNode unvalidated = JsonSchemaDeserializerRuleRecordTest.<JsonNode>deserializer(false)
                .fromRuleRecord(withoutCurrency, v2(), original);
        assertEquals(original, unvalidated);
    }

    @Test
    void explicitNullsSurviveWithTheConfiguredMapper() throws Exception {
        // configure() sets up a mapper that leaves nulls out when serializing (NON_NULL)
        JsonSchemaDeserializer<JsonNode> deserializer = new JsonSchemaDeserializer<>(null, false);
        deserializer.setObjectMapper(new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL));
        JsonNode original = mapper.readTree("{\"orderId\":\"A1\",\"note\":null,\"meta\":{\"tag\":null}}");

        Map<String, Object> map = deserializer.toRuleRecord(original);
        assertTrue(map.containsKey("note"));
        assertTrue(((Map<?, ?>) map.get("meta")).containsKey("tag"));

        JsonNode result = deserializer.fromRuleRecord(map, v2(), original);
        assertEquals(original, result);
    }
}
