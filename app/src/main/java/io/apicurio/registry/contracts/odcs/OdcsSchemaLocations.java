package io.apicurio.registry.contracts.odcs;

/**
 * Parses ODCS {@code schemas[].location} values.
 * <p>
 * Supported forms: {@code [groupId/]artifactId[:versionOrBranch]}. Group may be omitted
 * (defaults to {@code defaultGroupId}). Version/branch expression is optional.
 */
public final class OdcsSchemaLocations {

    private static final String[] INVALID = new String[0];

    private OdcsSchemaLocations() {
    }

    /**
     * @return {@code [groupId, artifactId]}, or an empty array if {@code location} is invalid
     */
    public static String[] parse(String location, String defaultGroupId) {
        if (location == null || location.isBlank()) {
            return INVALID;
        }
        String withoutVersion = location.contains(":")
                ? location.substring(0, location.indexOf(':'))
                : location;
        if (withoutVersion.isBlank()) {
            return INVALID;
        }

        String schemaGroupId;
        String schemaArtifactId;
        int slashIdx = withoutVersion.indexOf('/');
        if (slashIdx >= 0) {
            schemaGroupId = withoutVersion.substring(0, slashIdx);
            schemaArtifactId = withoutVersion.substring(slashIdx + 1);
            if (schemaArtifactId.contains("/")) {
                return INVALID;
            }
        } else {
            schemaGroupId = defaultGroupId;
            schemaArtifactId = withoutVersion;
        }

        if (schemaGroupId == null || schemaGroupId.isBlank()
                || schemaArtifactId.isBlank()) {
            return INVALID;
        }
        return new String[] { schemaGroupId, schemaArtifactId };
    }

    public static boolean isValid(String[] parsed) {
        return parsed != null && parsed.length == 2;
    }
}
