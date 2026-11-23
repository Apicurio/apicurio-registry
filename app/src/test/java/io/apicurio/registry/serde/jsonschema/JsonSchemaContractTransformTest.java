package io.apicurio.registry.serde.jsonschema;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Converting a JSON Schema record to the map contract rules are evaluated on, and the output of a TRANSFORM
 * rule back to the record's class.
 */
class JsonSchemaContractTransformTest {

    public static class Order {
        public String orderId;
        public double total;
    }

    @Test
    void transformedRecordBecomesAnObjectOfTheSameClass() {
        JsonSchemaSerializer<Order> serializer = new JsonSchemaSerializer<>();
        serializer.setObjectMapper(new ObjectMapper());
        Order original = new Order();
        original.orderId = "ord-1";
        original.total = 10.0;

        assertEquals(Map.of("orderId", "ord-1", "total", 10.0), serializer.toRuleRecord(original));

        Order transformed = serializer.fromRuleRecord(Map.of("orderId", "ORD-1", "total", 12.5), null, original);
        assertEquals("ORD-1", transformed.orderId);
        assertEquals(12.5, transformed.total);
    }
}
