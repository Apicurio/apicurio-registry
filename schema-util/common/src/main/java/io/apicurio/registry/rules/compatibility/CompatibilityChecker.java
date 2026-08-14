package io.apicurio.registry.rules.compatibility;

import io.apicurio.registry.content.TypedContent;

import java.util.List;
import java.util.Map;

/**
 * An interface that is used to determine whether a proposed artifact's content is compatible and return a set
 * of incompatible differences with older version(s) of the same content, based on a given compatibility
 * level.
 */
public interface CompatibilityChecker {

    /**
     * @param compatibilityLevel MUST NOT be null
     * @param existingArtifacts MUST NOT be null and MUST NOT contain null elements, but may be empty if the
     *            rule is executed and the artifact does not exist (e.g. a global COMPATIBILITY rule with
     *            <code>io.apicurio.registry.rules.RuleApplicationType#CREATE</code>)
     * @param proposedArtifact MUST NOT be null
     */
    CompatibilityExecutionResult testCompatibility(CompatibilityLevel compatibilityLevel,
            List<TypedContent> existingArtifacts, TypedContent proposedArtifact,
            Map<String, TypedContent> resolvedReferences);

    /**
     * Whether this checker can actually evaluate compatibility. Implementations that do not compare the
     * proposed content against the existing versions (i.e. stubs such as
     * {@link NoopCompatibilityChecker}) MUST return <code>false</code> so that callers can distinguish
     * "verified compatible" from "not checked at all" instead of treating both as a passing verdict.
     *
     * @return true if {@link #testCompatibility} produces a meaningful verdict, false if it is a stub
     */
    default boolean isCompatibilitySupported() {
        return true;
    }

}