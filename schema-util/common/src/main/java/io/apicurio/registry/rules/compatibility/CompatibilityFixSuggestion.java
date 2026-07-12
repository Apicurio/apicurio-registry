package io.apicurio.registry.rules.compatibility;

import java.util.Objects;

/**
 * A concrete suggestion for how to resolve a compatibility violation.
 */
public class CompatibilityFixSuggestion {

    public enum Tier {
        RECOMMENDED, ACCEPTABLE, WORKAROUND, INFORMATIONAL
    }

    private final Tier tier;
    private final String description;
    private final String example;

    public CompatibilityFixSuggestion(Tier tier, String description, String example) {
        this.tier = Objects.requireNonNull(tier, "tier");
        this.description = Objects.requireNonNull(description, "description");
        this.example = example;
    }

    public static CompatibilityFixSuggestion of(Tier tier, String description) {
        return new CompatibilityFixSuggestion(tier, description, null);
    }

    public static CompatibilityFixSuggestion of(Tier tier, String description, String example) {
        return new CompatibilityFixSuggestion(tier, description, example);
    }

    public Tier getTier() {
        return tier;
    }

    public String getDescription() {
        return description;
    }

    public String getExample() {
        return example;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        CompatibilityFixSuggestion that = (CompatibilityFixSuggestion) o;
        return tier == that.tier && Objects.equals(description, that.description)
                && Objects.equals(example, that.example);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tier, description, example);
    }
}
