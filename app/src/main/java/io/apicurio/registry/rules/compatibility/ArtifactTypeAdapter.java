package io.apicurio.registry.rules.compatibility;

import java.util.List;

/**
 * @author Ales Justin
 */
public interface ArtifactTypeAdapter {
    boolean isCompatibleWith(CompatibilityLevel compatibilityLevel, List<String> existingSchemas, String proposedSchema);
}
