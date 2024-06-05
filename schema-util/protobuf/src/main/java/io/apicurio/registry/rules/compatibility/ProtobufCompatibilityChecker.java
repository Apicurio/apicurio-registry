package io.apicurio.registry.rules.compatibility;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rules.compatibility.protobuf.ProtobufCompatibilityCheckerLibrary;
import io.apicurio.registry.utils.protobuf.schema.ProtobufFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

public class ProtobufCompatibilityChecker implements CompatibilityChecker {

    @Override
    public CompatibilityExecutionResult testCompatibility(CompatibilityLevel compatibilityLevel, List<TypedContent> existingArtifacts, TypedContent proposedArtifact, Map<String, TypedContent> resolvedReferences) {
        requireNonNull(compatibilityLevel, "compatibilityLevel MUST NOT be null");
        requireNonNull(existingArtifacts, "existingArtifacts MUST NOT be null");
        requireNonNull(proposedArtifact, "proposedArtifact MUST NOT be null");

        if (existingArtifacts.isEmpty()) {
            return CompatibilityExecutionResult.compatible();
        }

        ProtobufFile fileBefore = new ProtobufFile(existingArtifacts.get(existingArtifacts.size() - 1).getContent().content());
        ProtobufFile fileAfter = new ProtobufFile(proposedArtifact.getContent().content());

        switch (compatibilityLevel) {
            case BACKWARD: {
                return testBackward(fileBefore, fileAfter);
            }
            case BACKWARD_TRANSITIVE: {
                return testBackwardTransitive(existingArtifacts, fileAfter);
            }
            case FORWARD: {
                return testForward(fileBefore, fileAfter);
            }
            case FORWARD_TRANSITIVE: {
                return testForwardTransitive(existingArtifacts, fileAfter);
            }
            case FULL: {
                return testFull(fileBefore, fileAfter);
            }
            case FULL_TRANSITIVE: {
                return testFullTransitive(existingArtifacts, fileAfter);
            }
            default:
                return CompatibilityExecutionResult.compatible();
        }
    }

    @NotNull
    private CompatibilityExecutionResult testFullTransitive(List<TypedContent> existingSchemas, ProtobufFile fileAfter) {
        ProtobufFile fileBefore;
        for (TypedContent existing : existingSchemas) {
            fileBefore = new ProtobufFile(existing.getContent().content());
            if (!testFull(fileBefore, fileAfter).isCompatible()) {
                return CompatibilityExecutionResult.incompatible("The new version of the protobuf artifact is not fully compatible.");
            }
        }
        return CompatibilityExecutionResult.compatible();
    }

    @NotNull
    private CompatibilityExecutionResult testFull(ProtobufFile fileBefore, ProtobufFile fileAfter) {
        ProtobufCompatibilityCheckerLibrary backwardChecker = new ProtobufCompatibilityCheckerLibrary(fileBefore, fileAfter);
        ProtobufCompatibilityCheckerLibrary forwardChecker = new ProtobufCompatibilityCheckerLibrary(fileAfter, fileBefore);
        if (!backwardChecker.validate() && !forwardChecker.validate()) {
            return CompatibilityExecutionResult.incompatible("The new version of the protobuf artifact is not fully compatible.");
        } else {
            return CompatibilityExecutionResult.compatible();
        }
    }

    @NotNull
    private CompatibilityExecutionResult testForwardTransitive(List<TypedContent> existingSchemas, ProtobufFile fileAfter) {
        ProtobufFile fileBefore;
        for (TypedContent existing : existingSchemas) {
            fileBefore = new ProtobufFile(existing.getContent().content());
            ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(fileAfter, fileBefore);
            if (!checker.validate()) {
                return CompatibilityExecutionResult.incompatible("The new version of the protobuf artifact is not forward compatible.");
            }
        }
        return CompatibilityExecutionResult.compatible();
    }

    @NotNull
    private CompatibilityExecutionResult testForward(ProtobufFile fileBefore, ProtobufFile fileAfter) {
        ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(fileAfter, fileBefore);
        if (checker.validate()) {
            return CompatibilityExecutionResult.compatible();
        } else {
            return CompatibilityExecutionResult.incompatible("The new version of the protobuf artifact is not forward compatible.");
        }
    }

    @NotNull
    private CompatibilityExecutionResult testBackwardTransitive(List<TypedContent> existingSchemas, ProtobufFile fileAfter) {
        ProtobufFile fileBefore;
        for (TypedContent existing : existingSchemas) {
            fileBefore = new ProtobufFile(existing.getContent().content());
            ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(fileBefore, fileAfter);
            if (!checker.validate()) {
                return CompatibilityExecutionResult.incompatible("The new version of the protobuf artifact is not backward compatible.");
            }
        }
        return CompatibilityExecutionResult.compatible();
    }

    @NotNull
    private CompatibilityExecutionResult testBackward(ProtobufFile fileBefore, ProtobufFile fileAfter) {
        ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(fileBefore, fileAfter);
        if (checker.validate()) {
            return CompatibilityExecutionResult.compatible();
        } else {
            return CompatibilityExecutionResult.incompatible("The new version of the protobuf artifact is not backward compatible.");
        }
    }
}