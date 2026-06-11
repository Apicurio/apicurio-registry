package io.apicurio.registry.json.rules.compatibility;

import io.apitomy.datamodels.jsonschema.compat.Difference;
import io.apicurio.registry.rules.compatibility.CompatibilityDifference;
import io.apicurio.registry.rules.violation.RuleViolation;

import java.util.Objects;

public class ApitomyCompatibilityDifference implements CompatibilityDifference {

    private final Difference difference;

    public ApitomyCompatibilityDifference(Difference difference) {
        this.difference = Objects.requireNonNull(difference);
    }

    @Override
    public RuleViolation asRuleViolation() {
        return new RuleViolation(difference.getDiffType().name(), difference.getPathUpdated());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ApitomyCompatibilityDifference that)) return false;
        return difference.equals(that.difference);
    }

    @Override
    public int hashCode() {
        return difference.hashCode();
    }
}
