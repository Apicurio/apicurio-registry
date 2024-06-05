package io.apicurio.registry.rules.compatibility;

import io.apicurio.registry.content.TypedContent;

import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

public class NoopCompatibilityChecker implements CompatibilityChecker {
    public static CompatibilityChecker INSTANCE = new NoopCompatibilityChecker();

    /**
     * @see CompatibilityChecker#testCompatibility(CompatibilityLevel, List, TypedContent, Map) 
     */
    @Override
    public CompatibilityExecutionResult testCompatibility(CompatibilityLevel compatibilityLevel, List<TypedContent> existingArtifacts, 
                                                          TypedContent proposedArtifact, Map<String, TypedContent> resolvedReferences) {
        requireNonNull(compatibilityLevel, "compatibilityLevel MUST NOT be null");
        requireNonNull(existingArtifacts, "existingSchemas MUST NOT be null");
        requireNonNull(proposedArtifact, "proposedSchema MUST NOT be null");
        return CompatibilityExecutionResult.compatible();
    }
}