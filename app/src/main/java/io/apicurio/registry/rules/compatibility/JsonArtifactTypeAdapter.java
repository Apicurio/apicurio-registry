package io.apicurio.registry.rules.compatibility;

import java.util.List;

/**
 * @author Ales Justin
 * @author Jonathan Halliday
 */
public class JsonArtifactTypeAdapter implements ArtifactTypeAdapter {
    
    /**
     * @see io.apicurio.registry.rules.compatibility.ArtifactTypeAdapter#isCompatibleWith(io.apicurio.registry.rules.compatibility.CompatibilityLevel, java.util.List, java.lang.String)
     */
    @Override
    public boolean isCompatibleWith(CompatibilityLevel compatibilityLevel, List<String> existingSchemas, String proposedSchema) {
        return existingSchemas.isEmpty() || existingSchemas.get(0).equals(proposedSchema);
    }
}
