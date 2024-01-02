package io.apicurio.registry.rules.compatibility;

import io.apicurio.registry.rules.RuleViolation;

import java.util.Objects;

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
}
