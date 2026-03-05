package io.apicurio.registry.iceberg.rest.v1.impl.commit;

import io.apicurio.registry.storage.error.CommitFailedException;

import java.util.List;
import java.util.Map;

/**
 * Validates Iceberg table requirements against current metadata. Each requirement must be satisfied for a
 * commit to proceed.
 */
public final class TableRequirementValidator {

    private TableRequirementValidator() {
    }

    /**
     * Validates all requirements against the current table metadata.
     * @param requirements list of requirement objects, each with a "type" field
     * @param currentMetadata current table metadata (null if table does not exist)
     * @param groupId group ID for error messages
     * @param artifactId artifact ID for error messages
     * @throws CommitFailedException if any requirement is not met
     * @throws IllegalArgumentException if a requirement type is unknown
     */
    @SuppressWarnings("unchecked")
    public static void validate(List<Map<String, Object>> requirements,
            Map<String, Object> currentMetadata, String groupId, String artifactId) {
        if (requirements == null || requirements.isEmpty()) {
            return;
        }

        for (Map<String, Object> req : requirements) {
            String type = (String) req.get("type");
            if (type == null) {
                throw new IllegalArgumentException("Requirement is missing 'type' field");
            }

            switch (type) {
                case "assert-create":
                    assertCreate(currentMetadata, groupId, artifactId);
                    break;
                case "assert-table-uuid":
                    assertTableUuid(req, currentMetadata, groupId, artifactId);
                    break;
                case "assert-ref-snapshot-id":
                    assertRefSnapshotId(req, currentMetadata, groupId, artifactId);
                    break;
                case "assert-last-assigned-field-id":
                    assertNumericField(req, currentMetadata, "last-assigned-field-id", "last-column-id",
                            groupId, artifactId);
                    break;
                case "assert-current-schema-id":
                    assertNumericField(req, currentMetadata, "current-schema-id", "current-schema-id",
                            groupId, artifactId);
                    break;
                case "assert-last-assigned-partition-id":
                    assertNumericField(req, currentMetadata, "last-assigned-partition-id",
                            "last-partition-id", groupId, artifactId);
                    break;
                case "assert-default-spec-id":
                    assertNumericField(req, currentMetadata, "default-spec-id", "default-spec-id", groupId,
                            artifactId);
                    break;
                case "assert-default-sort-order-id":
                    assertNumericField(req, currentMetadata, "default-sort-order-id",
                            "default-sort-order-id", groupId, artifactId);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown requirement type: " + type);
            }
        }
    }

    private static void assertCreate(Map<String, Object> currentMetadata, String groupId,
            String artifactId) {
        if (currentMetadata != null) {
            throw new CommitFailedException(groupId, artifactId,
                    "Requirement failed: assert-create - table already exists");
        }
    }

    private static void assertTableUuid(Map<String, Object> req, Map<String, Object> currentMetadata,
            String groupId, String artifactId) {
        requireMetadataExists(currentMetadata, groupId, artifactId, "assert-table-uuid");
        String expectedUuid = (String) req.get("uuid");
        if (expectedUuid == null) {
            throw new IllegalArgumentException(
                    "assert-table-uuid requirement is missing required 'uuid' field");
        }
        String actualUuid = (String) currentMetadata.get("table-uuid");
        if (!expectedUuid.equals(actualUuid)) {
            throw new CommitFailedException(groupId, artifactId,
                    "Requirement failed: assert-table-uuid - expected " + expectedUuid + " but was "
                            + actualUuid);
        }
    }

    @SuppressWarnings("unchecked")
    private static void assertRefSnapshotId(Map<String, Object> req, Map<String, Object> currentMetadata,
            String groupId, String artifactId) {
        requireMetadataExists(currentMetadata, groupId, artifactId, "assert-ref-snapshot-id");
        String refName = (String) req.get("ref");
        Long expectedSnapshotId = toLong(req.get("snapshot-id"));

        if ("main".equals(refName)) {
            Long actualSnapshotId = toLong(currentMetadata.get("current-snapshot-id"));
            if (!equalsLong(expectedSnapshotId, actualSnapshotId)) {
                throw new CommitFailedException(groupId, artifactId,
                        "Requirement failed: assert-ref-snapshot-id for ref 'main' - expected "
                                + expectedSnapshotId + " but was " + actualSnapshotId);
            }
        } else {
            Map<String, Object> refs = (Map<String, Object>) currentMetadata.get("refs");
            if (refs == null) {
                if (expectedSnapshotId != null && expectedSnapshotId != -1) {
                    throw new CommitFailedException(groupId, artifactId,
                            "Requirement failed: assert-ref-snapshot-id - ref '" + refName
                                    + "' does not exist");
                }
                return;
            }

            Map<String, Object> refData = (Map<String, Object>) refs.get(refName);
            if (refData == null) {
                if (expectedSnapshotId != null && expectedSnapshotId != -1) {
                    throw new CommitFailedException(groupId, artifactId,
                            "Requirement failed: assert-ref-snapshot-id - ref '" + refName
                                    + "' does not exist");
                }
            } else {
                Long actualSnapshotId = toLong(refData.get("snapshot-id"));
                if (!equalsLong(expectedSnapshotId, actualSnapshotId)) {
                    throw new CommitFailedException(groupId, artifactId,
                            "Requirement failed: assert-ref-snapshot-id for ref '" + refName
                                    + "' - expected " + expectedSnapshotId + " but was "
                                    + actualSnapshotId);
                }
            }
        }
    }

    private static void assertNumericField(Map<String, Object> req, Map<String, Object> currentMetadata,
            String reqFieldName, String metadataFieldName, String groupId, String artifactId) {
        requireMetadataExists(currentMetadata, groupId, artifactId, "assert-" + reqFieldName);
        Long expectedValue = toLong(req.get(reqFieldName));
        Long actualValue = toLong(currentMetadata.get(metadataFieldName));
        if (!equalsLong(expectedValue, actualValue)) {
            throw new CommitFailedException(groupId, artifactId,
                    "Requirement failed: assert-" + reqFieldName + " - expected " + expectedValue
                            + " but was " + actualValue);
        }
    }

    private static void requireMetadataExists(Map<String, Object> currentMetadata, String groupId,
            String artifactId, String requirementType) {
        if (currentMetadata == null) {
            throw new CommitFailedException(groupId, artifactId,
                    "Requirement failed: " + requirementType + " - table does not exist");
        }
    }

    static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expected a numeric value but got: " + value, e);
        }
    }

    private static boolean equalsLong(Long a, Long b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.longValue() == b.longValue();
    }
}
