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
import java.util.function.Predicate;
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

    public enum ViolationClassification {
        BACKWARD_ONLY,
        FORWARD_ONLY,
        BOTH;

        public boolean appliesToBackward() {
            return this == BACKWARD_ONLY || this == BOTH;
        }

        public boolean appliesToForward() {
            return this == FORWARD_ONLY || this == BOTH;
        }
    }

    /**
     * Classification table of Protobuf difference violation types based on Protobuf wire-format compatibility semantics:
     *
     * | DifferenceType                 | Classification  | Rationale                                                                                  |
     * | :----------------------------- | :-------------- | :----------------------------------------------------------------------------------------- |
     * | REQUIRED_FIELD_ADDED           | BACKWARD_ONLY   | Proto2: new reader (v2) fails when parsing old data (v1) missing the required field.       |
     * | FIELD_REMOVED_WITHOUT_RESERVE  | FORWARD_ONLY    | Unreserved field removal endangers old readers (v1) if tag is reused in future schemas.   |
     * | RESERVED_FIELD_REMOVED         | FORWARD_ONLY    | Un-reserving a tag permits tag reuse in future schemas, breaking old readers (v1).         |
     * | RESERVED_FIELD_USED            | BOTH            | Reusing a reserved tag breaks wire contracts for both reader directions.                   |
     * | FIELD_ID_CHANGED               | BOTH            | Changing tag numbers breaks binary wire decoding in both directions.                       |
     * | FIELD_TYPE_CHANGED             | BOTH            | Changing field type breaks wire parsing/deserialization in both directions.                |
     * | FIELD_LABEL_CHANGED            | BOTH            | Changing field label breaks wire parsing/deserialization in both directions.               |
     * | FIELD_NAME_CHANGED             | BOTH            | Changing field names breaks DTO/JSON deserialization in both directions.                   |
     * | SERVICE_RPC_REMOVED            | BOTH            | Removing an RPC endpoint breaks client-server contract in both directions.                |
     * | SERVICE_RPC_SIGNATURE_CHANGED  | BOTH            | Changing RPC request/response types breaks API contract in both directions.                |
     */
    public static ViolationClassification classifyDifference(ProtobufDifference difference) {
        ProtobufDifference.DifferenceType type = difference.getType();
        if (type == null) {
            throw new IllegalArgumentException("DifferenceType must not be null for difference: " + difference.getMessage());
        }

        return switch (type) {
            case REQUIRED_FIELD_ADDED -> ViolationClassification.BACKWARD_ONLY;
            case FIELD_REMOVED_WITHOUT_RESERVE, RESERVED_FIELD_REMOVED -> ViolationClassification.FORWARD_ONLY;
            case RESERVED_FIELD_USED, FIELD_ID_CHANGED, FIELD_TYPE_CHANGED, FIELD_LABEL_CHANGED,
                 FIELD_NAME_CHANGED, SERVICE_RPC_REMOVED, SERVICE_RPC_SIGNATURE_CHANGED -> ViolationClassification.BOTH;
        };
    }

    private Set<CompatibilityDifference> collectDirectionalDifferences(ProtobufFile fileBefore, ProtobufFile fileAfter,
            Predicate<ViolationClassification> filter) {
        ProtobufCompatibilityCheckerLibrary checker = new ProtobufCompatibilityCheckerLibrary(fileBefore, fileAfter);
        List<ProtobufDifference> differences = checker.findDifferences();
        return differences.stream()
                .filter(diff -> filter.test(classifyDifference(diff)))
                .map(this::toCompatibilityDifference)
                .collect(Collectors.toSet());
    }

    private Set<CompatibilityDifference> checkBackwardCompatible(ProtobufFile fileBefore, ProtobufFile fileAfter) {
        return collectDirectionalDifferences(fileBefore, fileAfter, ViolationClassification::appliesToBackward);
    }

    private Set<CompatibilityDifference> checkForwardCompatible(ProtobufFile fileBefore, ProtobufFile fileAfter) {
        return collectDirectionalDifferences(fileBefore, fileAfter, ViolationClassification::appliesToForward);
    }

    @NotNull
    private CompatibilityExecutionResult testFullTransitive(List<TypedContent> existingSchemas,
            ProtobufFile fileAfter) {
        Set<CompatibilityDifference> allDifferences = new HashSet<>();
        for (TypedContent existing : existingSchemas) {
            ProtobufFile fileBefore = new ProtobufFile(existing.getContent().content());
            allDifferences.addAll(collectDirectionalDifferences(fileBefore, fileAfter,
                    classification -> classification.appliesToBackward() || classification.appliesToForward()));
        }
        return CompatibilityExecutionResult.incompatibleOrEmpty(allDifferences);
    }

    @NotNull
    private CompatibilityExecutionResult testFull(ProtobufFile fileBefore, ProtobufFile fileAfter) {
        Set<CompatibilityDifference> allDifferences = collectDirectionalDifferences(fileBefore, fileAfter,
                classification -> classification.appliesToBackward() || classification.appliesToForward());
        return CompatibilityExecutionResult.incompatibleOrEmpty(allDifferences);
    }

    @NotNull
    private CompatibilityExecutionResult testForwardTransitive(List<TypedContent> existingSchemas,
            ProtobufFile fileAfter) {
        Set<CompatibilityDifference> allDifferences = new HashSet<>();
        for (TypedContent existing : existingSchemas) {
            ProtobufFile fileBefore = new ProtobufFile(existing.getContent().content());
            allDifferences.addAll(checkForwardCompatible(fileBefore, fileAfter));
        }
        return CompatibilityExecutionResult.incompatibleOrEmpty(allDifferences);
    }

    @NotNull
    private CompatibilityExecutionResult testForward(ProtobufFile fileBefore, ProtobufFile fileAfter) {
        Set<CompatibilityDifference> differences = checkForwardCompatible(fileBefore, fileAfter);
        return CompatibilityExecutionResult.incompatibleOrEmpty(differences);
    }

    @NotNull
    private CompatibilityExecutionResult testBackwardTransitive(List<TypedContent> existingSchemas,
            ProtobufFile fileAfter) {
        Set<CompatibilityDifference> allDifferences = new HashSet<>();
        for (TypedContent existing : existingSchemas) {
            ProtobufFile fileBefore = new ProtobufFile(existing.getContent().content());
            allDifferences.addAll(checkBackwardCompatible(fileBefore, fileAfter));
        }
        return CompatibilityExecutionResult.incompatibleOrEmpty(allDifferences);
    }

    @NotNull
    private CompatibilityExecutionResult testBackward(ProtobufFile fileBefore, ProtobufFile fileAfter) {
        Set<CompatibilityDifference> differences = checkBackwardCompatible(fileBefore, fileAfter);
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
