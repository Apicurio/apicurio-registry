package io.apicurio.registry.protobuf.rules.compatibility;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rules.compatibility.CompatibilityChecker;
import io.apicurio.registry.rules.compatibility.CompatibilityExecutionResult;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.protobuf.rules.compatibility.protobuf.ProtobufCompatibilityCheckerLibrary;
import io.apicurio.registry.utils.protobuf.schema.ProtobufFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Protobuf schema compatibility checker.
 *
 * <p>This class checks compatibility between protobuf schemas at various levels
 * (BACKWARD, FORWARD, FULL, and their transitive variants).</p>
 *
 * <p>The implementation has been optimized to accept pre-parsed {@link ProtobufFile}
 * instances when available, avoiding redundant parsing in workflows where schemas
 * have already been compiled (e.g., during validation + compatibility checks).</p>
 */
public class ProtobufCompatibilityChecker implements CompatibilityChecker {

    @Override
    public CompatibilityExecutionResult testCompatibility(CompatibilityLevel compatibilityLevel,
                                                          List<TypedContent> existingArtifacts, TypedContent proposedArtifact,
                                                          Map<String, TypedContent> resolvedReferences) {
        requireNonNull(compatibilityLevel, "compatibilityLevel MUST NOT be null");
        requireNonNull(existingArtifacts, "existingArtifacts MUST NOT be null");
        requireNonNull(proposedArtifact, "proposedArtifact MUST NOT be null");

        if (existingArtifacts.isEmpty()) {
            return CompatibilityExecutionResult.compatible();
        }

        try {
            // Parse the proposed artifact once
            ProtobufFile fileAfter = new ProtobufFile(proposedArtifact.getContent().content());

            // For non-transitive checks, only parse the latest existing artifact
            if (!isTransitiveCheck(compatibilityLevel)) {
                ProtobufFile fileBefore = new ProtobufFile(
                        existingArtifacts.get(existingArtifacts.size() - 1).getContent().content());
                return performCompatibilityCheck(compatibilityLevel, null, fileBefore, fileAfter);
            }

            // For transitive checks, parse all existing artifacts upfront
            List<ProtobufFile> existingFiles = parseExistingArtifacts(existingArtifacts);
            ProtobufFile fileBefore = existingFiles.get(existingFiles.size() - 1);

            return performCompatibilityCheckWithParsedSchemas(compatibilityLevel, existingFiles, fileBefore, fileAfter);
        } catch (Exception e) {
            return CompatibilityExecutionResult.incompatible("Error parsing protobuf schema: " + e.getMessage());
        }
    }

    /**
     * Test compatibility using pre-parsed ProtobufFile instances.
     *
     * <p>This method allows callers that have already parsed the schemas (e.g., during
     * validation) to skip redundant parsing.</p>
     *
     * @param compatibilityLevel The compatibility level to check
     * @param existingSchemas Pre-parsed existing schemas (for transitive checks)
     * @param proposedSchema Pre-parsed proposed schema
     * @return The compatibility result
     */
    public CompatibilityExecutionResult testCompatibility(CompatibilityLevel compatibilityLevel,
                                                          List<ProtobufFile> existingSchemas,
                                                          ProtobufFile proposedSchema) {
        requireNonNull(compatibilityLevel, "compatibilityLevel MUST NOT be null");
        requireNonNull(proposedSchema, "proposedSchema MUST NOT be null");

        if (existingSchemas == null || existingSchemas.isEmpty()) {
            return CompatibilityExecutionResult.compatible();
        }

        ProtobufFile fileBefore = existingSchemas.get(existingSchemas.size() - 1);
        return performCompatibilityCheckWithParsedSchemas(compatibilityLevel, existingSchemas, fileBefore, proposedSchema);
    }

    /**
     * Parse all existing artifacts into ProtobufFile instances.
     */
    private List<ProtobufFile> parseExistingArtifacts(List<TypedContent> existingArtifacts) throws Exception {
        List<ProtobufFile> files = new ArrayList<>(existingArtifacts.size());
        for (TypedContent artifact : existingArtifacts) {
            files.add(new ProtobufFile(artifact.getContent().content()));
        }
        return files;
    }

    /**
     * Check if the compatibility level requires transitive checking.
     */
    private boolean isTransitiveCheck(CompatibilityLevel level) {
        return level == CompatibilityLevel.BACKWARD_TRANSITIVE ||
               level == CompatibilityLevel.FORWARD_TRANSITIVE ||
               level == CompatibilityLevel.FULL_TRANSITIVE;
    }

    private CompatibilityExecutionResult performCompatibilityCheck(CompatibilityLevel compatibilityLevel,
            List<TypedContent> existingArtifacts, ProtobufFile fileBefore, ProtobufFile fileAfter) {
        switch (compatibilityLevel) {
            case BACKWARD:
                return testBackward(fileBefore, fileAfter);
            case FORWARD:
                return testForward(fileBefore, fileAfter);
            case FULL:
                return testFull(fileBefore, fileAfter);
            default:
                return CompatibilityExecutionResult.compatible();
        }
    }

    private CompatibilityExecutionResult performCompatibilityCheckWithParsedSchemas(CompatibilityLevel compatibilityLevel,
            List<ProtobufFile> existingSchemas, ProtobufFile fileBefore, ProtobufFile fileAfter) {
        switch (compatibilityLevel) {
            case BACKWARD:
                return testBackward(fileBefore, fileAfter);
            case BACKWARD_TRANSITIVE:
                return testBackwardTransitive(existingSchemas, fileAfter);
            case FORWARD:
                return testForward(fileBefore, fileAfter);
            case FORWARD_TRANSITIVE:
                return testForwardTransitive(existingSchemas, fileAfter);
            case FULL:
                return testFull(fileBefore, fileAfter);
            case FULL_TRANSITIVE:
                return testFullTransitive(existingSchemas, fileAfter);
            default:
                return CompatibilityExecutionResult.compatible();
        }
    }

    private CompatibilityExecutionResult testFullTransitive(List<ProtobufFile> existingSchemas,
            ProtobufFile fileAfter) {
        for (ProtobufFile fileBefore : existingSchemas) {
            if (!testFull(fileBefore, fileAfter).isCompatible()) {
                return CompatibilityExecutionResult
                        .incompatible("The new version of the protobuf artifact is not fully compatible.");
            }
        }
        return CompatibilityExecutionResult.compatible();
    }

    
    private CompatibilityExecutionResult testFull(ProtobufFile fileBefore, ProtobufFile fileAfter) {
        ProtobufCompatibilityCheckerLibrary backwardChecker = new ProtobufCompatibilityCheckerLibrary(
                fileBefore, fileAfter);
        ProtobufCompatibilityCheckerLibrary forwardChecker = new ProtobufCompatibilityCheckerLibrary(
                fileAfter, fileBefore);
        if (!backwardChecker.validate() && !forwardChecker.validate()) {
            return CompatibilityExecutionResult
                    .incompatible("The new version of the protobuf artifact is not fully compatible.");
        } else {
            return CompatibilityExecutionResult.compatible();
        }
    }


    private CompatibilityExecutionResult testForwardTransitive(List<ProtobufFile> existingSchemas,
            ProtobufFile fileAfter) {
        for (ProtobufFile fileBefore : existingSchemas) {
            ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(fileAfter,
                    fileBefore);
            if (!checker.validate()) {
                return CompatibilityExecutionResult
                        .incompatible("The new version of the protobuf artifact is not forward compatible.");
            }
        }
        return CompatibilityExecutionResult.compatible();
    }

    
    private CompatibilityExecutionResult testForward(ProtobufFile fileBefore, ProtobufFile fileAfter) {
        ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(fileAfter,
                fileBefore);
        if (checker.validate()) {
            return CompatibilityExecutionResult.compatible();
        } else {
            return CompatibilityExecutionResult
                    .incompatible("The new version of the protobuf artifact is not forward compatible.");
        }
    }


    private CompatibilityExecutionResult testBackwardTransitive(List<ProtobufFile> existingSchemas,
            ProtobufFile fileAfter) {
        for (ProtobufFile fileBefore : existingSchemas) {
            ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(fileBefore,
                    fileAfter);
            if (!checker.validate()) {
                return CompatibilityExecutionResult
                        .incompatible("The new version of the protobuf artifact is not backward compatible.");
            }
        }
        return CompatibilityExecutionResult.compatible();
    }

    
    private CompatibilityExecutionResult testBackward(ProtobufFile fileBefore, ProtobufFile fileAfter) {
        ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(fileBefore,
                fileAfter);
        if (checker.validate()) {
            return CompatibilityExecutionResult.compatible();
        } else {
            return CompatibilityExecutionResult
                    .incompatible("The new version of the protobuf artifact is not backward compatible.");
        }
    }
}