package io.apicurio.registry.iceberg.content;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rules.compatibility.CompatibilityChecker;
import io.apicurio.registry.rules.compatibility.CompatibilityExecutionResult;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;

import java.util.List;
import java.util.Map;

/**
 * Compatibility checker for Apache Iceberg table and view metadata.
 *
 * Iceberg has its own compatibility semantics managed by the Iceberg library,
 * so this checker provides basic compatibility checking.
 *
 * For now, we allow all changes and rely on Iceberg's own evolution rules.
 */
public class IcebergCompatibilityChecker implements CompatibilityChecker {

    @Override
    public CompatibilityExecutionResult testCompatibility(CompatibilityLevel compatibilityLevel,
            List<TypedContent> existingArtifacts, TypedContent proposedArtifact,
            Map<String, TypedContent> resolvedReferences) {

        // Iceberg manages its own schema evolution rules
        // For the registry, we allow all changes and defer to Iceberg's semantics
        return CompatibilityExecutionResult.compatible();
    }
}
