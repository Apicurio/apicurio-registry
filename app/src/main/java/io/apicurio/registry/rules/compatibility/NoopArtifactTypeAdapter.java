package io.apicurio.registry.rules.compatibility;

import java.util.List;

/**
 * @author Ales Justin
 */
public class NoopArtifactTypeAdapter implements ArtifactTypeAdapter {
    public static ArtifactTypeAdapter INSTANCE = new NoopArtifactTypeAdapter();

    @Override
    public boolean isCompatibleWith(CompatibilityLevel compatibilityLevel, List<String> existingSchemas, String proposedSchema) {
        return true;
    }
}
