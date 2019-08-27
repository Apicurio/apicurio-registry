package io.apicurio.registry.rules;

import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.CompatibilityLevel;

/**
 * @author Ales Justin
 */
public interface RulesService {

    void validate(RuleContext context);

    boolean isCompatible(ArtifactType type, CompatibilityLevel level, String artifactId, String content);
}
