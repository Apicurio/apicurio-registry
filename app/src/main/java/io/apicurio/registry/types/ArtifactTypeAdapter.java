package io.apicurio.registry.types;

import java.util.List;

/**
 * @author Ales Justin
 */
public interface ArtifactTypeAdapter {
    ArtifactWrapper wrapper(String schema);

    boolean isCompatibleWith(String compatibilityLevel, List<String> existingSchemas, String proposedSchema);
}
