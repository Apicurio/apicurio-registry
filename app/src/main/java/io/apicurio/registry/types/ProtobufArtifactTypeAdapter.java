package io.apicurio.registry.types;

import com.squareup.wire.schema.Location;
import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import com.squareup.wire.schema.internal.parser.ProtoParser;

import java.util.List;

/**
 * @author Ales Justin
 */
public class ProtobufArtifactTypeAdapter implements ArtifactTypeAdapter {
    @Override
    public ArtifactWrapper wrapper(String schemaString) {
        ProtoFileElement element = ProtoParser.parse(Location.get(""), schemaString);
        return new ArtifactWrapper(element, element.toSchema());
    }

    @Override
    public boolean isCompatibleWith(String compatibilityLevel, List<String> existingSchemas, String proposedSchema) {
        if (existingSchemas.isEmpty()) {
            return true;
        }
        switch (compatibilityLevel) {
            case "BACKWARD": {
                ProtobufFile fileBefore = new ProtobufFile(existingSchemas.get(existingSchemas.size() - 1));
                ProtobufFile fileAfter = new ProtobufFile(proposedSchema);
                ProtobufCompatibilityChecker checker = new ProtobufCompatibilityChecker(fileBefore, fileAfter);
                return checker.validate();
            }
            case "BACKWARD_TRANSITIVE":
                ProtobufFile fileAfter = new ProtobufFile(proposedSchema);
                for (String existing : existingSchemas) {
                    ProtobufFile fileBefore = new ProtobufFile(existing);
                    ProtobufCompatibilityChecker checker = new ProtobufCompatibilityChecker(fileBefore, fileAfter);
                    if (!checker.validate()) {
                        return false;
                    }
                }
                return true;
            case "FORWARD":
            case "FORWARD_TRANSITIVE":
            case "FULL":
            case "FULL_TRANSITIVE":
                throw new IllegalStateException("Compatibility level " + compatibilityLevel + " not supported for Protobuf schemas");
            default:
                return true;
        }
    }
}
