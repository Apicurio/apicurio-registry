package io.apicurio.registry.rules.compatibility;

import io.apicurio.registry.rules.RuleViolation;

class BigquerySchemaCompatibilityDifference implements CompatibilityDifference {

    private final String message;

    private final String path;

    public BigquerySchemaCompatibilityDifference(String message, String path) {
        this.message = message;
        this.path = path;
    }

    @Override
    public RuleViolation asRuleViolation() {
        return new RuleViolation(message, path);
    }
}
