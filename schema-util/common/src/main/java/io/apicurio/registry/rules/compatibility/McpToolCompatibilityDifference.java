package io.apicurio.registry.rules.compatibility;

import io.apicurio.registry.rules.violation.RuleViolation;

import java.util.Objects;

/**
 * Represents a compatibility difference for MCP tool definition artifacts.
 */
public class McpToolCompatibilityDifference implements CompatibilityDifference {

    public enum Type {
        REQUIRED_PARAM_ADDED("inputSchema/required"),
        REQUIRED_PARAM_REMOVED("inputSchema/required"),
        INPUT_SCHEMA_TYPE_CHANGED("inputSchema/type"),
        PROPERTY_REMOVED("inputSchema/properties"),
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

    public McpToolCompatibilityDifference(Type type, String description) {
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
        McpToolCompatibilityDifference that = (McpToolCompatibilityDifference) o;
        return type == that.type && Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, description);
    }

    @Override
    public String toString() {
        return "McpToolCompatibilityDifference{" + "type=" + type + ", description='" + description
                + '\'' + '}';
    }
}
