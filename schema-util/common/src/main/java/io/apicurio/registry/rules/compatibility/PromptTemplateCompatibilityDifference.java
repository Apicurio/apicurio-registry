package io.apicurio.registry.rules.compatibility;

import io.apicurio.registry.rules.violation.RuleViolation;

import java.util.Objects;

/**
 * Represents a compatibility difference for Prompt Template artifacts.
 */
public class PromptTemplateCompatibilityDifference implements CompatibilityDifference {

    public enum Type {
        VARIABLE_REMOVED_BUT_USED("variables"),
        VARIABLE_TYPE_CHANGED("variables"),
        VARIABLE_BECAME_REQUIRED("variables"),
        ENUM_VALUE_REMOVED("variables"),
        OUTPUT_SCHEMA_PROPERTY_REMOVED("outputSchema/properties"),
        OUTPUT_SCHEMA_REMOVED("outputSchema"),
        PARSE_ERROR("document");

        private final String context;

        Type(String context) {
            this.context = context;
        }

        public String getContext() {
            return "/" + context;
        }
    }

    private final Type type;
    private final String description;

    public PromptTemplateCompatibilityDifference(Type type, String description) {
        this.type = Objects.requireNonNull(type);
        this.description = Objects.requireNonNull(description);
    }

    public Type getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public RuleViolation asRuleViolation() {
        return new RuleViolation(description, type.getContext());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PromptTemplateCompatibilityDifference that = (PromptTemplateCompatibilityDifference) o;
        return type == that.type && Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, description);
    }

    @Override
    public String toString() {
        return "PromptTemplateCompatibilityDifference{" +
                "type=" + type +
                ", description='" + description + '\'' +
                '}';
    }
}
