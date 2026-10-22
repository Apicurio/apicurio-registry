package io.apicurio.registry.noprofile.iceberg.rest.v1;

import io.apicurio.registry.iceberg.rest.v1.impl.IcebergSchemaUtil;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IcebergSchemaUtilTest {

    @Test
    public void testFlatSchema() {
        Map<String, Object> schema = Map.of(
                "type", "struct",
                "fields", List.of(
                        Map.of("id", 1, "name", "id", "type", "long", "required", true),
                        Map.of("id", 2, "name", "name", "type", "string", "required", false),
                        Map.of("id", 3, "name", "ts", "type", "timestamp", "required", false)));
        assertEquals(3, IcebergSchemaUtil.computeMaxFieldId(schema));
    }

    @Test
    public void testEmptyFields() {
        Map<String, Object> schema = Map.of("type", "struct", "fields", List.of());
        assertEquals(0, IcebergSchemaUtil.computeMaxFieldId(schema));
    }

    @Test
    public void testNullFields() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "struct");
        schema.put("fields", null);
        assertEquals(0, IcebergSchemaUtil.computeMaxFieldId(schema));
    }

    @Test
    public void testNestedStruct() {
        Map<String, Object> schema = Map.of(
                "type", "struct",
                "fields", List.of(
                        Map.of("id", 1, "name", "id", "type", "long", "required", true),
                        Map.of("id", 2, "name", "address", "required", false,
                                "type", Map.of(
                                        "type", "struct",
                                        "fields", List.of(
                                                Map.of("id", 3, "name", "street", "type", "string",
                                                        "required", false),
                                                Map.of("id", 4, "name", "city", "type", "string",
                                                        "required", false),
                                                Map.of("id", 5, "name", "zip", "type", "string",
                                                        "required", false))))));
        assertEquals(5, IcebergSchemaUtil.computeMaxFieldId(schema));
    }

    @Test
    public void testListType() {
        Map<String, Object> schema = Map.of(
                "type", "struct",
                "fields", List.of(
                        Map.of("id", 1, "name", "id", "type", "long", "required", true),
                        Map.of("id", 2, "name", "tags", "required", false,
                                "type", Map.of(
                                        "type", "list",
                                        "element-id", 3,
                                        "element", "string",
                                        "element-required", false))));
        assertEquals(3, IcebergSchemaUtil.computeMaxFieldId(schema));
    }

    @Test
    public void testMapType() {
        Map<String, Object> schema = Map.of(
                "type", "struct",
                "fields", List.of(
                        Map.of("id", 1, "name", "id", "type", "long", "required", true),
                        Map.of("id", 2, "name", "metadata", "required", false,
                                "type", Map.of(
                                        "type", "map",
                                        "key-id", 3,
                                        "key", "string",
                                        "value-id", 4,
                                        "value", "string",
                                        "value-required", false))));
        assertEquals(4, IcebergSchemaUtil.computeMaxFieldId(schema));
    }

    @Test
    public void testDeeplyNestedSchema() {
        // struct -> list -> struct (simulates Debezium CDC nested records)
        Map<String, Object> innerStruct = Map.of(
                "type", "struct",
                "fields", List.of(
                        Map.of("id", 5, "name", "key", "type", "string", "required", true),
                        Map.of("id", 6, "name", "value", "type", "string", "required", false)));

        Map<String, Object> listType = Map.of(
                "type", "list",
                "element-id", 4,
                "element", innerStruct,
                "element-required", false);

        Map<String, Object> schema = Map.of(
                "type", "struct",
                "fields", List.of(
                        Map.of("id", 1, "name", "id", "type", "long", "required", true),
                        Map.of("id", 2, "name", "name", "type", "string", "required", false),
                        Map.of("id", 3, "name", "items", "required", false, "type", listType)));
        assertEquals(6, IcebergSchemaUtil.computeMaxFieldId(schema));
    }

    @Test
    public void testMapWithStructValue() {
        Map<String, Object> valueStruct = Map.of(
                "type", "struct",
                "fields", List.of(
                        Map.of("id", 5, "name", "amount", "type", "double", "required", true),
                        Map.of("id", 6, "name", "currency", "type", "string", "required", true)));

        Map<String, Object> mapType = Map.of(
                "type", "map",
                "key-id", 3,
                "key", "string",
                "value-id", 4,
                "value", valueStruct,
                "value-required", true);

        Map<String, Object> schema = Map.of(
                "type", "struct",
                "fields", List.of(
                        Map.of("id", 1, "name", "id", "type", "long", "required", true),
                        Map.of("id", 2, "name", "prices", "required", false, "type", mapType)));
        assertEquals(6, IcebergSchemaUtil.computeMaxFieldId(schema));
    }

    @Test
    public void testDebeziumCdcLikeSchema() {
        // Simulates a typical Debezium CDC schema with nested source info
        Map<String, Object> sourceStruct = Map.of(
                "type", "struct",
                "fields", List.of(
                        Map.of("id", 5, "name", "version", "type", "string", "required", false),
                        Map.of("id", 6, "name", "connector", "type", "string", "required", false),
                        Map.of("id", 7, "name", "name", "type", "string", "required", false),
                        Map.of("id", 8, "name", "ts_ms", "type", "long", "required", false),
                        Map.of("id", 9, "name", "snapshot", "type", "string", "required", false),
                        Map.of("id", 10, "name", "db", "type", "string", "required", false),
                        Map.of("id", 11, "name", "schema", "type", "string", "required", false),
                        Map.of("id", 12, "name", "table", "type", "string", "required", false)));

        Map<String, Object> schema = Map.of(
                "type", "struct",
                "fields", List.of(
                        Map.of("id", 1, "name", "customer_id", "type", "int", "required", true),
                        Map.of("id", 2, "name", "first_name", "type", "string", "required", false),
                        Map.of("id", 3, "name", "last_name", "type", "string", "required", false),
                        Map.of("id", 4, "name", "source", "required", false, "type", sourceStruct)));

        // Top-level max is 4, but nested goes to 12
        assertEquals(12, IcebergSchemaUtil.computeMaxFieldId(schema));
    }
}
