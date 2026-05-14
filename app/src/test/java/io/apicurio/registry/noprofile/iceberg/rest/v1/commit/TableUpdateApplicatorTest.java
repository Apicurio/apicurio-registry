package io.apicurio.registry.noprofile.iceberg.rest.v1.commit;

import io.apicurio.registry.iceberg.rest.v1.impl.commit.TableUpdateApplicator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("unchecked")
public class TableUpdateApplicatorTest {

    @Test
    public void testNullUpdates() {
        Map<String, Object> metadata = buildMetadata();
        TableUpdateApplicator.apply(null, metadata);
        // No exception, metadata unchanged except last-updated-ms not modified for null
    }

    @Test
    public void testEmptyUpdates() {
        Map<String, Object> metadata = buildMetadata();
        TableUpdateApplicator.apply(List.of(), metadata);
    }

    @Test
    public void testAssignUuid() {
        Map<String, Object> metadata = buildMetadata();
        List<Map<String, Object>> updates = List.of(Map.of("action", "assign-uuid", "uuid", "new-uuid"));
        TableUpdateApplicator.apply(updates, metadata);
        assertEquals("new-uuid", metadata.get("table-uuid"));
    }

    @Test
    public void testUpgradeFormatVersion() {
        Map<String, Object> metadata = buildMetadata();
        metadata.put("format-version", 1);
        List<Map<String, Object>> updates = List
                .of(Map.of("action", "upgrade-format-version", "format-version", 2));
        TableUpdateApplicator.apply(updates, metadata);
        assertEquals(2, metadata.get("format-version"));
    }

    @Test
    public void testUpgradeFormatVersionNoDowngrade() {
        Map<String, Object> metadata = buildMetadata();
        metadata.put("format-version", 2);
        List<Map<String, Object>> updates = List
                .of(Map.of("action", "upgrade-format-version", "format-version", 1));
        TableUpdateApplicator.apply(updates, metadata);
        // Should not downgrade
        assertEquals(2, metadata.get("format-version"));
    }

    @Test
    public void testAddSchema() {
        Map<String, Object> metadata = buildMetadata();
        Map<String, Object> newSchema = new HashMap<>();
        newSchema.put("type", "struct");
        newSchema.put("schema-id", 1);
        newSchema.put("fields", List.of(
                Map.of("id", 1, "name", "id", "type", "long", "required", true),
                Map.of("id", 2, "name", "data", "type", "string", "required", false),
                Map.of("id", 3, "name", "ts", "type", "timestamp", "required", false)));

        List<Map<String, Object>> updates = List.of(Map.of("action", "add-schema", "schema", newSchema));
        TableUpdateApplicator.apply(updates, metadata);

        List<Object> schemas = (List<Object>) metadata.get("schemas");
        assertEquals(2, schemas.size()); // Original + new
        assertEquals(3, metadata.get("last-column-id")); // Updated from field IDs
    }

    @Test
    public void testSetCurrentSchema() {
        Map<String, Object> metadata = buildMetadata();
        List<Map<String, Object>> updates = List.of(Map.of("action", "set-current-schema", "schema-id", 1));
        TableUpdateApplicator.apply(updates, metadata);
        assertEquals(1, metadata.get("current-schema-id"));
    }

    @Test
    public void testAddSpec() {
        Map<String, Object> metadata = buildMetadata();
        Map<String, Object> spec = new HashMap<>();
        spec.put("spec-id", 1);
        spec.put("fields", List.of(Map.of("field-id", 1000, "source-id", 1, "name", "id_bucket",
                "transform", "bucket[16]")));

        List<Map<String, Object>> updates = List.of(Map.of("action", "add-spec", "spec", spec));
        TableUpdateApplicator.apply(updates, metadata);

        List<Object> specs = (List<Object>) metadata.get("partition-specs");
        assertEquals(2, specs.size());
        assertEquals(1000, metadata.get("last-partition-id"));
    }

    @Test
    public void testSetDefaultSpec() {
        Map<String, Object> metadata = buildMetadata();
        List<Map<String, Object>> updates = List.of(Map.of("action", "set-default-spec", "spec-id", 1));
        TableUpdateApplicator.apply(updates, metadata);
        assertEquals(1, metadata.get("default-spec-id"));
    }

    @Test
    public void testAddSortOrder() {
        Map<String, Object> metadata = buildMetadata();
        Map<String, Object> sortOrder = Map.of("order-id", 1, "fields",
                List.of(Map.of("source-id", 1, "transform", "identity", "direction", "asc", "null-order",
                        "nulls-first")));

        List<Map<String, Object>> updates = List
                .of(Map.of("action", "add-sort-order", "sort-order", sortOrder));
        TableUpdateApplicator.apply(updates, metadata);

        List<Object> sortOrders = (List<Object>) metadata.get("sort-orders");
        assertEquals(2, sortOrders.size());
    }

    @Test
    public void testSetDefaultSortOrder() {
        Map<String, Object> metadata = buildMetadata();
        List<Map<String, Object>> updates = List
                .of(Map.of("action", "set-default-sort-order", "order-id", 1));
        TableUpdateApplicator.apply(updates, metadata);
        assertEquals(1, metadata.get("default-sort-order-id"));
    }

    @Test
    public void testAddSnapshot() {
        Map<String, Object> metadata = buildMetadata();
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("snapshot-id", 100L);
        snapshot.put("timestamp-ms", System.currentTimeMillis());
        snapshot.put("summary", Map.of("operation", "append"));
        snapshot.put("manifest-list", "s3://bucket/path/snap-100.avro");

        List<Map<String, Object>> updates = List
                .of(Map.of("action", "add-snapshot", "snapshot", snapshot));
        TableUpdateApplicator.apply(updates, metadata);

        List<Object> snapshots = (List<Object>) metadata.get("snapshots");
        assertEquals(1, snapshots.size());
        assertEquals(1L, metadata.get("last-sequence-number"));

        List<Object> snapshotLog = (List<Object>) metadata.get("snapshot-log");
        assertEquals(1, snapshotLog.size());
    }

    @Test
    public void testSetSnapshotRef() {
        Map<String, Object> metadata = buildMetadata();

        Map<String, Object> update = new HashMap<>();
        update.put("action", "set-snapshot-ref");
        update.put("ref-name", "main");
        update.put("snapshot-id", 100L);
        update.put("type", "branch");

        TableUpdateApplicator.apply(List.of(update), metadata);

        Map<String, Object> refs = (Map<String, Object>) metadata.get("refs");
        assertNotNull(refs.get("main"));
        assertEquals(100L, metadata.get("current-snapshot-id"));
    }

    @Test
    public void testSetSnapshotRefTag() {
        Map<String, Object> metadata = buildMetadata();

        Map<String, Object> update = new HashMap<>();
        update.put("action", "set-snapshot-ref");
        update.put("ref-name", "tag-v1");
        update.put("snapshot-id", 100L);
        update.put("type", "tag");

        TableUpdateApplicator.apply(List.of(update), metadata);

        Map<String, Object> refs = (Map<String, Object>) metadata.get("refs");
        assertNotNull(refs.get("tag-v1"));
        // current-snapshot-id should not change for non-main refs
        assertEquals(-1, metadata.get("current-snapshot-id"));
    }

    @Test
    public void testRemoveSnapshots() {
        Map<String, Object> metadata = buildMetadata();
        List<Object> snapshots = new ArrayList<>();
        snapshots.add(Map.of("snapshot-id", 100L));
        snapshots.add(Map.of("snapshot-id", 200L));
        metadata.put("snapshots", snapshots);

        Map<String, Object> update = new HashMap<>();
        update.put("action", "remove-snapshots");
        update.put("snapshot-ids", List.of(100L));

        TableUpdateApplicator.apply(List.of(update), metadata);

        List<Object> remaining = (List<Object>) metadata.get("snapshots");
        assertEquals(1, remaining.size());
        assertEquals(200L, ((Map<String, Object>) remaining.get(0)).get("snapshot-id"));
    }

    @Test
    public void testRemoveSnapshotRef() {
        Map<String, Object> metadata = buildMetadata();
        Map<String, Object> refs = new HashMap<>();
        refs.put("tag-v1", Map.of("snapshot-id", 100L, "type", "tag"));
        metadata.put("refs", refs);

        Map<String, Object> update = new HashMap<>();
        update.put("action", "remove-snapshot-ref");
        update.put("ref-name", "tag-v1");

        TableUpdateApplicator.apply(List.of(update), metadata);

        Map<String, Object> resultRefs = (Map<String, Object>) metadata.get("refs");
        assertTrue(resultRefs.isEmpty());
    }

    @Test
    public void testSetLocation() {
        Map<String, Object> metadata = buildMetadata();
        List<Map<String, Object>> updates = List
                .of(Map.of("action", "set-location", "location", "/new/location"));
        TableUpdateApplicator.apply(updates, metadata);
        assertEquals("/new/location", metadata.get("location"));
    }

    @Test
    public void testSetProperties() {
        Map<String, Object> metadata = buildMetadata();

        Map<String, Object> update = new HashMap<>();
        update.put("action", "set-properties");
        update.put("updates", Map.of("key1", "value1", "key2", "value2"));

        TableUpdateApplicator.apply(List.of(update), metadata);

        Map<String, Object> props = (Map<String, Object>) metadata.get("properties");
        assertEquals("value1", props.get("key1"));
        assertEquals("value2", props.get("key2"));
    }

    @Test
    public void testRemoveProperties() {
        Map<String, Object> metadata = buildMetadata();
        Map<String, Object> props = new HashMap<>();
        props.put("key1", "value1");
        props.put("key2", "value2");
        metadata.put("properties", props);

        Map<String, Object> update = new HashMap<>();
        update.put("action", "remove-properties");
        update.put("removals", List.of("key1"));

        TableUpdateApplicator.apply(List.of(update), metadata);

        Map<String, Object> resultProps = (Map<String, Object>) metadata.get("properties");
        assertEquals(1, resultProps.size());
        assertEquals("value2", resultProps.get("key2"));
    }

    @Test
    public void testUnknownAction() {
        Map<String, Object> metadata = buildMetadata();
        List<Map<String, Object>> updates = List.of(Map.of("action", "unknown-action"));
        assertThrows(IllegalArgumentException.class,
                () -> TableUpdateApplicator.apply(updates, metadata));
    }

    @Test
    public void testMissingActionField() {
        Map<String, Object> metadata = buildMetadata();
        List<Map<String, Object>> updates = List.of(Map.of("foo", "bar"));
        assertThrows(IllegalArgumentException.class,
                () -> TableUpdateApplicator.apply(updates, metadata));
    }

    @Test
    public void testLastUpdatedMsAlwaysSet() {
        Map<String, Object> metadata = buildMetadata();
        long before = System.currentTimeMillis();
        List<Map<String, Object>> updates = List.of(Map.of("action", "assign-uuid", "uuid", "new-uuid"));
        TableUpdateApplicator.apply(updates, metadata);
        long after = System.currentTimeMillis();

        long lastUpdated = ((Number) metadata.get("last-updated-ms")).longValue();
        assertTrue(lastUpdated >= before && lastUpdated <= after);
    }

    @Test
    public void testMultipleSequentialUpdates() {
        Map<String, Object> metadata = buildMetadata();

        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "struct");
        schema.put("schema-id", 1);
        schema.put("fields", List.of(
                Map.of("id", 1, "name", "id", "type", "long", "required", true),
                Map.of("id", 2, "name", "data", "type", "string", "required", false)));

        List<Map<String, Object>> updates = List.of(
                Map.of("action", "add-schema", "schema", schema),
                Map.of("action", "set-current-schema", "schema-id", 1),
                Map.of("action", "assign-uuid", "uuid", "updated-uuid"));

        TableUpdateApplicator.apply(updates, metadata);

        assertEquals(1, metadata.get("current-schema-id"));
        assertEquals("updated-uuid", metadata.get("table-uuid"));
        assertEquals(2, ((List<?>) metadata.get("schemas")).size());
    }

    private Map<String, Object> buildMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("format-version", 2);
        metadata.put("table-uuid", "test-uuid");
        metadata.put("location", "/warehouse/test-ns/test-table");
        metadata.put("last-sequence-number", 0);
        metadata.put("last-updated-ms", 0L);
        metadata.put("last-column-id", 0);
        metadata.put("current-schema-id", 0);
        metadata.put("schemas", new ArrayList<>(List.of(
                Map.of("type", "struct", "schema-id", 0, "fields", List.of()))));
        metadata.put("default-spec-id", 0);
        metadata.put("partition-specs", new ArrayList<>(
                List.of(Map.of("spec-id", 0, "fields", List.of()))));
        metadata.put("last-partition-id", 999);
        metadata.put("default-sort-order-id", 0);
        metadata.put("sort-orders", new ArrayList<>(
                List.of(Map.of("order-id", 0, "fields", List.of()))));
        metadata.put("properties", new HashMap<>());
        metadata.put("current-snapshot-id", -1);
        metadata.put("snapshots", new ArrayList<>());
        metadata.put("snapshot-log", new ArrayList<>());
        metadata.put("metadata-log", new ArrayList<>());
        metadata.put("refs", new HashMap<>());
        return metadata;
    }
}
