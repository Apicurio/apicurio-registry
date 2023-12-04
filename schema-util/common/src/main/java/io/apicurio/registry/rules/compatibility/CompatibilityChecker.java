package io.apicurio.registry.rules.compatibility;

import io.apicurio.registry.content.ContentHandle;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * An interface that is used to determine whether a proposed artifact's content is compatible and return a set of
 * incompatible differences
 * with older version(s) of the same content, based on a given compatibility level.
 *
 */
public interface CompatibilityChecker {
    /**
     * @param compatibilityLevel MUST NOT be null
     * @param existingArtifacts  MUST NOT be null and MUST NOT contain null elements,
     *                           but may be empty if the rule is executed and the artifact does not exist
     *                           (e.g. a global COMPATIBILITY rule with <code>io.apicurio.registry.rules.RuleApplicationType#CREATE</code>)
     * @param proposedArtifact   MUST NOT be null
     */
    CompatibilityExecutionResult testCompatibility(CompatibilityLevel compatibilityLevel, List<ContentHandle> existingArtifacts, ContentHandle proposedArtifact, Map<String, ContentHandle> resolvedReferences);

    /**
     * @param compatibilityLevel MUST NOT be null
     * @param existingArtifacts  MUST NOT be null and MUST NOT contain null elements,
     *                           but may be empty if the rule is executed and the artifact does not exist
     *                           (e.g. a global COMPATIBILITY rule with <code>io.apicurio.registry.rules.RuleApplicationType#CREATE</code>)
     * @param proposedArtifact   MUST NOT be null
     */
    default CompatibilityExecutionResult testCompatibility(CompatibilityLevel compatibilityLevel, List<String> existingArtifacts, String proposedArtifact, Map<String, ContentHandle> resolvedReferences) {
        final List<ContentHandle> contentHandles = existingArtifacts.stream().map(ContentHandle::create).collect(Collectors.toList());
        return testCompatibility(compatibilityLevel, contentHandles, ContentHandle.create(proposedArtifact), resolvedReferences);
    }
}