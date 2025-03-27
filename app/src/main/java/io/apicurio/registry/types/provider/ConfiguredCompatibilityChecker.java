package io.apicurio.registry.types.provider;

import io.apicurio.registry.config.artifactTypes.Provider;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rules.compatibility.CompatibilityChecker;
import io.apicurio.registry.rules.compatibility.CompatibilityExecutionResult;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;

import java.util.List;
import java.util.Map;

public class ConfiguredCompatibilityChecker implements CompatibilityChecker {
    public ConfiguredCompatibilityChecker(Provider provider) {
    }

    @Override
    public CompatibilityExecutionResult testCompatibility(CompatibilityLevel compatibilityLevel, List<TypedContent> existingArtifacts, TypedContent proposedArtifact, Map<String, TypedContent> resolvedReferences) {
        return CompatibilityExecutionResult.compatible();
    }
}
