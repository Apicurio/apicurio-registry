package io.apicurio.registry.noprofile.iceberg.rest.v1.commit;

import io.apicurio.registry.iceberg.rest.v1.impl.commit.TableRequirementValidator;
import io.apicurio.registry.storage.error.CommitFailedException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TableRequirementValidatorTest {

    private static final String GROUP_ID = "test-ns";
    private static final String ARTIFACT_ID = "test-table";

    @Test
    public void testNullRequirements() {
        assertDoesNotThrow(
                () -> TableRequirementValidator.validate(null, buildMetadata(), GROUP_ID, ARTIFACT_ID));
    }

    @Test
    public void testEmptyRequirements() {
        assertDoesNotThrow(() -> TableRequirementValidator.validate(List.of(), buildMetadata(), GROUP_ID,
                ARTIFACT_ID));
    }

    @Test
    public void testAssertCreateWhenTableDoesNotExist() {
        List<Map<String, Object>> reqs = List.of(Map.of("type", "assert-create"));
        assertDoesNotThrow(
                () -> TableRequirementValidator.validate(reqs, null, GROUP_ID, ARTIFACT_ID));
    }

    @Test
    public void testAssertCreateWhenTableExists() {
        List<Map<String, Object>> reqs = List.of(Map.of("type", "assert-create"));
        assertThrows(CommitFailedException.class,
                () -> TableRequirementValidator.validate(reqs, buildMetadata(), GROUP_ID, ARTIFACT_ID));
    }

    @Test
    public void testAssertTableUuidMatch() {
        List<Map<String, Object>> reqs = List.of(Map.of("type", "assert-table-uuid", "uuid", "test-uuid"));
        assertDoesNotThrow(
                () -> TableRequirementValidator.validate(reqs, buildMetadata(), GROUP_ID, ARTIFACT_ID));
    }

    @Test
    public void testAssertTableUuidMismatch() {
        List<Map<String, Object>> reqs = List
                .of(Map.of("type", "assert-table-uuid", "uuid", "wrong-uuid"));
        assertThrows(CommitFailedException.class,
                () -> TableRequirementValidator.validate(reqs, buildMetadata(), GROUP_ID, ARTIFACT_ID));
    }

    @Test
    public void testAssertRefSnapshotIdMainMatch() {
        Map<String, Object> metadata = buildMetadata();
        metadata.put("current-snapshot-id", -1L);
        List<Map<String, Object>> reqs = List
                .of(Map.of("type", "assert-ref-snapshot-id", "ref", "main", "snapshot-id", -1L));
        assertDoesNotThrow(
                () -> TableRequirementValidator.validate(reqs, metadata, GROUP_ID, ARTIFACT_ID));
    }

    @Test
    public void testAssertRefSnapshotIdMainMismatch() {
        Map<String, Object> metadata = buildMetadata();
        metadata.put("current-snapshot-id", -1L);
        List<Map<String, Object>> reqs = List
                .of(Map.of("type", "assert-ref-snapshot-id", "ref", "main", "snapshot-id", 100L));
        assertThrows(CommitFailedException.class,
                () -> TableRequirementValidator.validate(reqs, metadata, GROUP_ID, ARTIFACT_ID));
    }

    @Test
    public void testAssertRefSnapshotIdNamedRefMatch() {
        Map<String, Object> metadata = buildMetadata();
        Map<String, Object> refs = new HashMap<>();
        refs.put("tag-v1", Map.of("snapshot-id", 42L, "type", "tag"));
        metadata.put("refs", refs);

        List<Map<String, Object>> reqs = List
                .of(Map.of("type", "assert-ref-snapshot-id", "ref", "tag-v1", "snapshot-id", 42L));
        assertDoesNotThrow(
                () -> TableRequirementValidator.validate(reqs, metadata, GROUP_ID, ARTIFACT_ID));
    }

    @Test
    public void testAssertRefSnapshotIdNamedRefMismatch() {
        Map<String, Object> metadata = buildMetadata();
        Map<String, Object> refs = new HashMap<>();
        refs.put("tag-v1", Map.of("snapshot-id", 42L, "type", "tag"));
        metadata.put("refs", refs);

        List<Map<String, Object>> reqs = List
                .of(Map.of("type", "assert-ref-snapshot-id", "ref", "tag-v1", "snapshot-id", 99L));
        assertThrows(CommitFailedException.class,
                () -> TableRequirementValidator.validate(reqs, metadata, GROUP_ID, ARTIFACT_ID));
    }

    @Test
    public void testAssertRefSnapshotIdMissingRef() {
        Map<String, Object> metadata = buildMetadata();
        List<Map<String, Object>> reqs = List
                .of(Map.of("type", "assert-ref-snapshot-id", "ref", "nonexistent", "snapshot-id", 42L));
        assertThrows(CommitFailedException.class,
                () -> TableRequirementValidator.validate(reqs, metadata, GROUP_ID, ARTIFACT_ID));
    }

    @Test
    public void testAssertLastAssignedFieldIdMatch() {
        List<Map<String, Object>> reqs = List
                .of(Map.of("type", "assert-last-assigned-field-id", "last-assigned-field-id", 2));
        assertDoesNotThrow(
                () -> TableRequirementValidator.validate(reqs, buildMetadata(), GROUP_ID, ARTIFACT_ID));
    }

    @Test
    public void testAssertLastAssignedFieldIdMismatch() {
        List<Map<String, Object>> reqs = List
                .of(Map.of("type", "assert-last-assigned-field-id", "last-assigned-field-id", 99));
        assertThrows(CommitFailedException.class,
                () -> TableRequirementValidator.validate(reqs, buildMetadata(), GROUP_ID, ARTIFACT_ID));
    }

    @Test
    public void testAssertCurrentSchemaIdMatch() {
        List<Map<String, Object>> reqs = List
                .of(Map.of("type", "assert-current-schema-id", "current-schema-id", 0));
        assertDoesNotThrow(
                () -> TableRequirementValidator.validate(reqs, buildMetadata(), GROUP_ID, ARTIFACT_ID));
    }

    @Test
    public void testAssertCurrentSchemaIdMismatch() {
        List<Map<String, Object>> reqs = List
                .of(Map.of("type", "assert-current-schema-id", "current-schema-id", 5));
        assertThrows(CommitFailedException.class,
                () -> TableRequirementValidator.validate(reqs, buildMetadata(), GROUP_ID, ARTIFACT_ID));
    }

    @Test
    public void testAssertLastAssignedPartitionIdMatch() {
        List<Map<String, Object>> reqs = List
                .of(Map.of("type", "assert-last-assigned-partition-id", "last-assigned-partition-id", 999));
        assertDoesNotThrow(
                () -> TableRequirementValidator.validate(reqs, buildMetadata(), GROUP_ID, ARTIFACT_ID));
    }

    @Test
    public void testAssertDefaultSpecIdMatch() {
        List<Map<String, Object>> reqs = List
                .of(Map.of("type", "assert-default-spec-id", "default-spec-id", 0));
        assertDoesNotThrow(
                () -> TableRequirementValidator.validate(reqs, buildMetadata(), GROUP_ID, ARTIFACT_ID));
    }

    @Test
    public void testAssertDefaultSortOrderIdMatch() {
        List<Map<String, Object>> reqs = List
                .of(Map.of("type", "assert-default-sort-order-id", "default-sort-order-id", 0));
        assertDoesNotThrow(
                () -> TableRequirementValidator.validate(reqs, buildMetadata(), GROUP_ID, ARTIFACT_ID));
    }

    @Test
    public void testUnknownRequirementType() {
        List<Map<String, Object>> reqs = List.of(Map.of("type", "assert-something-unknown"));
        assertThrows(IllegalArgumentException.class,
                () -> TableRequirementValidator.validate(reqs, buildMetadata(), GROUP_ID, ARTIFACT_ID));
    }

    @Test
    public void testMissingTypeField() {
        List<Map<String, Object>> reqs = List.of(Map.of("foo", "bar"));
        assertThrows(IllegalArgumentException.class,
                () -> TableRequirementValidator.validate(reqs, buildMetadata(), GROUP_ID, ARTIFACT_ID));
    }

    @Test
    public void testIntegerLongComparison() {
        // JSON may deserialize small numbers as Integer, ensure comparison works
        Map<String, Object> metadata = buildMetadata();
        metadata.put("last-column-id", 2); // Integer
        List<Map<String, Object>> reqs = List
                .of(Map.of("type", "assert-last-assigned-field-id", "last-assigned-field-id", 2L)); // Long
        assertDoesNotThrow(
                () -> TableRequirementValidator.validate(reqs, metadata, GROUP_ID, ARTIFACT_ID));
    }

    private Map<String, Object> buildMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("format-version", 2);
        metadata.put("table-uuid", "test-uuid");
        metadata.put("location", "/warehouse/test-ns/test-table");
        metadata.put("last-column-id", 2);
        metadata.put("current-schema-id", 0);
        metadata.put("last-partition-id", 999);
        metadata.put("default-spec-id", 0);
        metadata.put("default-sort-order-id", 0);
        metadata.put("current-snapshot-id", -1);
        metadata.put("schemas", List.of());
        metadata.put("partition-specs", List.of());
        metadata.put("sort-orders", List.of());
        metadata.put("snapshots", List.of());
        metadata.put("refs", Map.of());
        return metadata;
    }
}
