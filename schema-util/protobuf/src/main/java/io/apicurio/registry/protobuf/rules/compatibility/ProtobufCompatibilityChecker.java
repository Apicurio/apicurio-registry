package io.apicurio.registry.protobuf.rules.compatibility;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.protobuf.ProtobufDifference;
import io.apicurio.registry.protobuf.rules.compatibility.protobuf.ProtobufCompatibilityCheckerLibrary;
import io.apicurio.registry.rules.compatibility.CompatibilityChecker;
import io.apicurio.registry.rules.compatibility.CompatibilityDifference;
import io.apicurio.registry.rules.compatibility.CompatibilityExecutionResult;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.rules.compatibility.SimpleCompatibilityDifference;
import io.apicurio.registry.utils.protobuf.schema.ProtobufFile;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

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

        ProtobufFile fileBefore = new ProtobufFile(
                existingArtifacts.get(existingArtifacts.size() - 1).getContent().content());
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
    private CompatibilityExecutionResult testFullTransitive(List<TypedContent> existingSchemas,
            ProtobufFile fileAfter) {
        Set<CompatibilityDifference> allDifferences = new HashSet<>();
        for (TypedContent existing : existingSchemas) {
            ProtobufFile fileBefore = new ProtobufFile(existing.getContent().content());
            // Collect backward differences
            ProtobufCompatibilityCheckerLibrary backwardChecker = new ProtobufCompatibilityCheckerLibrary(
                    fileBefore, fileAfter);
            allDifferences.addAll(collectDifferences(backwardChecker));
            // Collect forward differences
            ProtobufCompatibilityCheckerLibrary forwardChecker = new ProtobufCompatibilityCheckerLibrary(
                    fileAfter, fileBefore);
            allDifferences.addAll(collectDifferences(forwardChecker));
        }
        return CompatibilityExecutionResult.incompatibleOrEmpty(allDifferences);
    }

    @NotNull
    private CompatibilityExecutionResult testFull(ProtobufFile fileBefore, ProtobufFile fileAfter) {
        Set<CompatibilityDifference> allDifferences = new HashSet<>();
        // Collect backward differences
        ProtobufCompatibilityCheckerLibrary backwardChecker = new ProtobufCompatibilityCheckerLibrary(
                fileBefore, fileAfter);
        allDifferences.addAll(collectDifferences(backwardChecker));
        // Collect forward differences
        ProtobufCompatibilityCheckerLibrary forwardChecker = new ProtobufCompatibilityCheckerLibrary(
                fileAfter, fileBefore);
        allDifferences.addAll(collectDifferences(forwardChecker));
        return CompatibilityExecutionResult.incompatibleOrEmpty(allDifferences);
    }

    @NotNull
    private CompatibilityExecutionResult testForwardTransitive(List<TypedContent> existingSchemas,
            ProtobufFile fileAfter) {
        Set<CompatibilityDifference> allDifferences = new HashSet<>();
        for (TypedContent existing : existingSchemas) {
            ProtobufFile fileBefore = new ProtobufFile(existing.getContent().content());
            ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(fileAfter,
                    fileBefore);
            allDifferences.addAll(collectDifferences(checker));
        }
        return CompatibilityExecutionResult.incompatibleOrEmpty(allDifferences);
    }

    @NotNull
    private CompatibilityExecutionResult testForward(ProtobufFile fileBefore, ProtobufFile fileAfter) {
        ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(fileAfter,
                fileBefore);
        Set<CompatibilityDifference> differences = collectDifferences(checker);
        return CompatibilityExecutionResult.incompatibleOrEmpty(differences);
    }

    @NotNull
    private CompatibilityExecutionResult testBackwardTransitive(List<TypedContent> existingSchemas,
            ProtobufFile fileAfter) {
        Set<CompatibilityDifference> allDifferences = new HashSet<>();
        for (TypedContent existing : existingSchemas) {
            ProtobufFile fileBefore = new ProtobufFile(existing.getContent().content());
            ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(fileBefore,
                    fileAfter);
            allDifferences.addAll(collectDifferences(checker));
        }
        return CompatibilityExecutionResult.incompatibleOrEmpty(allDifferences);
    }

    @NotNull
    private CompatibilityExecutionResult testBackward(ProtobufFile fileBefore, ProtobufFile fileAfter) {
        ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(fileBefore,
                fileAfter);
        Set<CompatibilityDifference> differences = collectDifferences(checker);
        return CompatibilityExecutionResult.incompatibleOrEmpty(differences);
    }

    /**
     * Collects all differences from the compatibility checker and converts them to CompatibilityDifference
     * objects.
     */
    private Set<CompatibilityDifference> collectDifferences(ProtobufCompatibilityCheckerLibrary checker) {
        List<ProtobufDifference> differences = checker.findDifferences();
        return differences.stream().map(this::toCompatibilityDifference).collect(Collectors.toSet());
    }

    /**
     * Converts a ProtobufDifference to a CompatibilityDifference.
     */
    private CompatibilityDifference toCompatibilityDifference(ProtobufDifference diff) {
        String message = diff.getMessage();
        String context = extractContext(message);
        return new SimpleCompatibilityDifference(message, context);
    }

    /**
     * Extracts the context (message name) from a difference message. The context is used to provide
     * additional location information in the error response.
     */
    private String extractContext(String message) {
        if (message != null && message.contains("message ")) {
            int startIndex = message.indexOf("message ") + "message ".length();
            int endIndex = message.indexOf(",", startIndex);
            if (endIndex == -1) {
                endIndex = message.length();
            }
            String messageName = message.substring(startIndex, endIndex).trim();
            return "/" + messageName;
        }
        return "/";
    }
}
