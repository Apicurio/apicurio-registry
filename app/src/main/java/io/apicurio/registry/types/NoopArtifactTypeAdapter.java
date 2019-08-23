package io.apicurio.registry.types;

import java.util.List;

/**
 * @author Ales Justin
 */
public class NoopArtifactTypeAdapter implements ArtifactTypeAdapter {
    public static ArtifactTypeAdapter INSTANCE = new NoopArtifactTypeAdapter();

    @Override
    public ArtifactWrapper wrapper(String schema) {
        return new ArtifactWrapper(schema, schema);
    }

    @Override
    public boolean isCompatibleWith(String compatibilityLevel, List<String> existingSchemas, String proposedSchema) {
        return true;
    }
}
