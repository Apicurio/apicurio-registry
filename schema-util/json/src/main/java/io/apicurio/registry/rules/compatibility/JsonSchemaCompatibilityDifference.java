package io.apicurio.registry.rules.compatibility;

import io.apicurio.registry.rules.RuleViolation;
import io.apicurio.registry.rules.compatibility.jsonschema.diff.Difference;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;

/**
 * Translation object for Difference into CompatibilityDifference.
 */
@Builder
@Getter
@EqualsAndHashCode
public class JsonSchemaCompatibilityDifference implements CompatibilityDifference {
    @NonNull
    private final Difference difference;

    /**
     * @see CompatibilityDifference#asRuleViolation()
     */
    @Override
    public RuleViolation asRuleViolation() {
        return new RuleViolation(difference.getDiffType().getDescription(), difference.getPathUpdated());
    }
}
