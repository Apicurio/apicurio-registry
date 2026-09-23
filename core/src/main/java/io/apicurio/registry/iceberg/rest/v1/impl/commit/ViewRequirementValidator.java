package io.apicurio.registry.iceberg.rest.v1.impl.commit;

import io.apicurio.registry.storage.error.CommitFailedException;

import java.util.List;
import java.util.Map;

/**
 * Validates Iceberg view requirements against current metadata. Each requirement must be satisfied for a
 * commit to proceed.
 */
public final class ViewRequirementValidator {

    private ViewRequirementValidator() {
    }

    /**
     * Validates all requirements against the current view metadata.
     * @param requirements list of requirement objects, each with a "type" field
     * @param currentMetadata current view metadata (null if view does not exist)
     * @param groupId group ID for error messages
     * @param artifactId artifact ID for error messages
     * @throws CommitFailedException if any requirement is not met
     * @throws IllegalArgumentException if a requirement type is unknown
     */
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
                case "assert-view-uuid":
                    assertViewUuid(req, currentMetadata, groupId, artifactId);
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
                    "Requirement failed: assert-create - view already exists");
        }
    }

    private static void assertViewUuid(Map<String, Object> req, Map<String, Object> currentMetadata,
            String groupId, String artifactId) {
        requireMetadataExists(currentMetadata, groupId, artifactId, "assert-view-uuid");
        String expectedUuid = (String) req.get("uuid");
        if (expectedUuid == null) {
            throw new IllegalArgumentException(
                    "assert-view-uuid requirement is missing required 'uuid' field");
        }
        String actualUuid = (String) currentMetadata.get("view-uuid");
        if (!expectedUuid.equals(actualUuid)) {
            throw new CommitFailedException(groupId, artifactId,
                    "Requirement failed: assert-view-uuid - expected " + expectedUuid + " but was "
                            + actualUuid);
        }
    }

    private static void requireMetadataExists(Map<String, Object> currentMetadata, String groupId,
            String artifactId, String requirementType) {
        if (currentMetadata == null) {
            throw new CommitFailedException(groupId, artifactId,
                    "Requirement failed: " + requirementType + " - view does not exist");
        }
    }
}
