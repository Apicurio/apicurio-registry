package io.apicurio.registry.rules.compatibility;

import io.apicurio.registry.rules.RuleViolation;

/**
 * Represents a single compatibility difference.  These are generated when doing compatibility checking
 * between two versions of an artifact.  A non-zero collection of these indicates a compatibility violation.
 *
 */
public interface CompatibilityDifference {

    /**
     * Converts the difference into a rule violation cause.
     */
    public RuleViolation asRuleViolation();

}
