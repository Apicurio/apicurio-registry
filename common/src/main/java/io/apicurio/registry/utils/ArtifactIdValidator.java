package io.apicurio.registry.utils;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class ArtifactIdValidator {

    public static final String GROUP_ID_ERROR_MESSAGE = "Character % and non ASCII characters are not allowed in group IDs.";
    public static final String ARTIFACT_ID_ERROR_MESSAGE = "Character % and non ASCII characters are not allowed in artifact IDs.";

    private ArtifactIdValidator() {
        // utility class
    }

    public static boolean isGroupIdAllowed(String groupId) {
        return isArtifactIdAllowed(groupId);
    }

    public static boolean isArtifactIdAllowed(String artifactId) {
        return Charset.forName(StandardCharsets.US_ASCII.name()).newEncoder().canEncode(artifactId)
                && !artifactId.contains("%");
    }

}
