package io.apicurio.registry.rules.compatibility;

import io.apicurio.registry.rules.violation.RuleViolation;

import java.util.Objects;

/**
 * A universal {@link CompatibilityDifference} implementation, holding the description and context of the
 * underlying {@link RuleViolation} directly.
 */
public class SimpleCompatibilityDifference implements CompatibilityDifference {

    private final RuleViolation ruleViolation;

    public SimpleCompatibilityDifference(String description, String context) {
        Objects.requireNonNull(description);
        if (context == null || context.isBlank()) {
            context = "/";
        }
        ruleViolation = new RuleViolation(description, context);
    }

    public SimpleCompatibilityDifference(String description) {
        this(description, null);
    }

    @Override
    public RuleViolation asRuleViolation() {
        return ruleViolation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SimpleCompatibilityDifference that = (SimpleCompatibilityDifference) o;
        return Objects.equals(ruleViolation, that.ruleViolation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ruleViolation);
    }

    @Override
    public String toString() {
        return "SimpleCompatibilityDifference{" +
                "description='" + ruleViolation.getDescription() + '\'' +
                ", context='" + ruleViolation.getContext() + '\'' +
                '}';
    }
}
