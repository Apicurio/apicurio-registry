package io.apicurio.registry.rules.compatibility;

import io.apicurio.registry.rules.violation.RuleViolation;

import java.util.Objects;

/**
 * Represents a compatibility difference for A2A Agent Card artifacts.
 */
public class AgentCardCompatibilityDifference implements CompatibilityDifference {

    public enum Type {
        URL_CHANGED("url"),
        SKILL_REMOVED("skills"),
        CAPABILITY_REMOVED("capabilities"),
        AUTH_SCHEME_REMOVED("authentication"),
        MODE_REMOVED("modes"),
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

    public AgentCardCompatibilityDifference(Type type, String description) {
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
        AgentCardCompatibilityDifference that = (AgentCardCompatibilityDifference) o;
        return type == that.type && Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, description);
    }

    @Override
    public String toString() {
        return "AgentCardCompatibilityDifference{" +
                "type=" + type +
                ", description='" + description + '\'' +
                '}';
    }
}
