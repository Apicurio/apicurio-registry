package io.apicurio.registry.rules.compatibility;

import java.util.List;

/**
 * @author Ales Justin
 */
public class NoopArtifactTypeAdapter implements ArtifactTypeAdapter {
    public static ArtifactTypeAdapter INSTANCE = new NoopArtifactTypeAdapter();

    /**
     * @see io.apicurio.registry.rules.compatibility.ArtifactTypeAdapter#isCompatibleWith(io.apicurio.registry.rules.compatibility.CompatibilityLevel, java.util.List, java.lang.String)
     */
    @Override
    public boolean isCompatibleWith(CompatibilityLevel compatibilityLevel, List<String> existingSchemas, String proposedSchema) {
        return true;
    }
}
