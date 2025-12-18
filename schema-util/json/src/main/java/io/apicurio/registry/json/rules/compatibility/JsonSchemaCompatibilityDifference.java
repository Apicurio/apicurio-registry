package io.apicurio.registry.json.rules.compatibility;

import io.apicurio.registry.json.rules.compatibility.jsonschema.diff.Difference;
import io.apicurio.registry.rules.compatibility.CompatibilityDifference;
import io.apicurio.registry.rules.violation.RuleViolation;
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
