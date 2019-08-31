package io.apicurio.registry.rules.compatibility;

import java.util.List;

import io.apicurio.registry.types.ProtobufFile;

/**
 * @author Ales Justin
 */
public class ProtobufArtifactTypeAdapter implements ArtifactTypeAdapter {

    /**
     * @see io.apicurio.registry.rules.compatibility.ArtifactTypeAdapter#isCompatibleWith(io.apicurio.registry.rules.compatibility.CompatibilityLevel, java.util.List, java.lang.String)
     */
    @Override
    public boolean isCompatibleWith(CompatibilityLevel compatibilityLevel, List<String> existingSchemas, String proposedSchema) {
        if (existingSchemas.isEmpty()) {
            return true;
        }
        switch (compatibilityLevel) {
            case BACKWARD: {
                ProtobufFile fileBefore = new ProtobufFile(existingSchemas.get(existingSchemas.size() - 1));
                ProtobufFile fileAfter = new ProtobufFile(proposedSchema);
                ProtobufCompatibilityChecker checker = new ProtobufCompatibilityChecker(fileBefore, fileAfter);
                return checker.validate();
            }
            case BACKWARD_TRANSITIVE:
                ProtobufFile fileAfter = new ProtobufFile(proposedSchema);
                for (String existing : existingSchemas) {
                    ProtobufFile fileBefore = new ProtobufFile(existing);
                    ProtobufCompatibilityChecker checker = new ProtobufCompatibilityChecker(fileBefore, fileAfter);
                    if (!checker.validate()) {
                        return false;
                    }
                }
                return true;
            case FORWARD:
            case FORWARD_TRANSITIVE:
            case FULL:
            case FULL_TRANSITIVE:
                throw new IllegalStateException("Compatibility level " + compatibilityLevel + " not supported for Protobuf schemas");
            default:
                return true;
        }
    }
}
