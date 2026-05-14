package io.apicurio.registry.rules.compatibility;

import io.apicurio.registry.rules.violation.RuleViolation;

import java.util.Objects;

/**
 * Represents a compatibility difference for AI/ML Model Schema artifacts.
 */
public class ModelSchemaCompatibilityDifference implements CompatibilityDifference {

    public enum Type {
        REQUIRED_INPUT_FIELD_ADDED("input/required"),
        INPUT_PROPERTY_REMOVED("input/properties"),
        INPUT_PROPERTY_TYPE_CHANGED("input/properties"),
        INPUT_SCHEMA_REMOVED("input"),
        OUTPUT_PROPERTY_REMOVED("output/properties"),
        OUTPUT_PROPERTY_TYPE_CHANGED("output/properties"),
        OUTPUT_SCHEMA_REMOVED("output"),
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

    public ModelSchemaCompatibilityDifference(Type type, String description) {
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
        ModelSchemaCompatibilityDifference that = (ModelSchemaCompatibilityDifference) o;
        return type == that.type && Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, description);
    }

    @Override
    public String toString() {
        return "ModelSchemaCompatibilityDifference{" +
                "type=" + type +
                ", description='" + description + '\'' +
                '}';
    }
}
