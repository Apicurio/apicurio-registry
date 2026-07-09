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
    public void testNestedStructOnlyCountsTopLevel() {
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
        assertEquals(2, IcebergSchemaUtil.computeMaxFieldId(schema));
    }

    @Test
    public void testListTypeOnlyCountsTopLevel() {
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
        assertEquals(2, IcebergSchemaUtil.computeMaxFieldId(schema));
    }

    @Test
    public void testMapTypeOnlyCountsTopLevel() {
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
        assertEquals(2, IcebergSchemaUtil.computeMaxFieldId(schema));
    }

    @Test
    public void testDebeziumCdcLikeSchemaOnlyCountsTopLevel() {
        Map<String, Object> sourceStruct = Map.of(
                "type", "struct",
                "fields", List.of(
                        Map.of("id", 5, "name", "version", "type", "string", "required", false),
                        Map.of("id", 6, "name", "connector", "type", "string", "required", false),
                        Map.of("id", 7, "name", "ts_ms", "type", "long", "required", false),
                        Map.of("id", 8, "name", "db", "type", "string", "required", false),
                        Map.of("id", 9, "name", "table", "type", "string", "required", false)));

        Map<String, Object> schema = Map.of(
                "type", "struct",
                "fields", List.of(
                        Map.of("id", 1, "name", "id", "type", "long", "required", true),
                        Map.of("id", 2, "name", "first_name", "type", "string", "required", false),
                        Map.of("id", 3, "name", "last_name", "type", "string", "required", false),
                        Map.of("id", 4, "name", "source", "required", false, "type", sourceStruct)));
        assertEquals(4, IcebergSchemaUtil.computeMaxFieldId(schema));
    }

    @Test
    public void testNonSequentialIds() {
        Map<String, Object> schema = Map.of(
                "type", "struct",
                "fields", List.of(
                        Map.of("id", 1, "name", "id", "type", "long", "required", true),
                        Map.of("id", 5, "name", "name", "type", "string", "required", false),
                        Map.of("id", 3, "name", "email", "type", "string", "required", false)));
        assertEquals(5, IcebergSchemaUtil.computeMaxFieldId(schema));
    }
}
