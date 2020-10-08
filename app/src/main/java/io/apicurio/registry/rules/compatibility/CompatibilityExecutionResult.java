package io.apicurio.registry.rules.compatibility;

import io.apicurio.registry.rules.compatibility.jsonschema.diff.Difference;

import java.util.Set;

/**
 * Created by aohana
 */
public class CompatibilityExecutionResult {

    private boolean isCompatible;
    private Set<Difference> incompatibleDifferences;

    public CompatibilityExecutionResult(boolean isCompatible, Set<Difference> incompatibleDifferences) {
        this.isCompatible = isCompatible;
        this.incompatibleDifferences = incompatibleDifferences;
    }

    public boolean isCompatible() {
        return isCompatible;
    }

    public void setCompatible(boolean compatible) {
        isCompatible = compatible;
    }

    public Set<Difference> getIncompatibleDifferences() {
        return incompatibleDifferences;
    }

    public void setIncompatibleDifferences(Set<Difference> incompatibleDifferences) {
        this.incompatibleDifferences = incompatibleDifferences;
    }
}
