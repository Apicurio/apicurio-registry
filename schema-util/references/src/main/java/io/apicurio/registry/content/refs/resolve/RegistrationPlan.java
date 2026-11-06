package io.apicurio.registry.content.refs.resolve;

import java.util.List;

/**
 * The result of planning: artifacts in registration order (dependencies first) and any warnings.
 *
 * @param artifacts the artifacts to register, dependencies first
 * @param warnings non-fatal warnings produced while planning
 */
public record RegistrationPlan(List<PlannedArtifact> artifacts, List<String> warnings) {

    /**
     * Creates a plan with immutable copies of the given lists.
     *
     * @param artifacts the artifacts
     * @param warnings the warnings
     */
    public RegistrationPlan {
        artifacts = List.copyOf(artifacts);
        warnings = List.copyOf(warnings);
    }

    /**
     * Returns only the root artifacts, in registration order.
     *
     * @return the root artifacts
     */
    public List<PlannedArtifact> roots() {
        return artifacts.stream().filter(PlannedArtifact::isRoot).toList();
    }
}
