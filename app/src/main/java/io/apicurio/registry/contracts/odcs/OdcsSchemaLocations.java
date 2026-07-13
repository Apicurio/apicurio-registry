package io.apicurio.registry.contracts.odcs;

/**
 * Parses ODCS {@code schemas[].location} values.
 * <p>
 * Supported forms: {@code [groupId/]artifactId[:versionOrBranch]}. Group may be omitted
 * (defaults to {@code defaultGroupId}). Version/branch expression is optional.
 */
public final class OdcsSchemaLocations {

    private OdcsSchemaLocations() {
    }

    /**
     * @return {@code [groupId, artifactId]}, or {@code null} if {@code location} is invalid
     */
    public static String[] parse(String location, String defaultGroupId) {
        if (location == null || location.isBlank()) {
            return null;
        }
        String withoutVersion = location.contains(":")
                ? location.substring(0, location.indexOf(':'))
                : location;
        if (withoutVersion.isBlank()) {
            return null;
        }

        String schemaGroupId;
        String schemaArtifactId;
        int slashIdx = withoutVersion.indexOf('/');
        if (slashIdx >= 0) {
            schemaGroupId = withoutVersion.substring(0, slashIdx);
            schemaArtifactId = withoutVersion.substring(slashIdx + 1);
            if (schemaArtifactId.contains("/")) {
                return null;
            }
        } else {
            schemaGroupId = defaultGroupId;
            schemaArtifactId = withoutVersion;
        }

        if (schemaGroupId == null || schemaGroupId.isBlank()
                || schemaArtifactId.isBlank()) {
            return null;
        }
        return new String[] { schemaGroupId, schemaArtifactId };
    }
}
