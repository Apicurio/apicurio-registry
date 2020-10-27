package io.apicurio.registry.rules.compatibility;

import io.apicurio.registry.rules.compatibility.jsonschema.diff.Difference;

import java.util.Collections;
import java.util.Set;

/**
 * Created by aohana
 *
 * Holds the result for compatibility check
 * incompatibleDifferences set will have values only schema types which have information regarding difference type
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

    public static CompatibilityExecutionResult empty(boolean isCompatible) {
        return new CompatibilityExecutionResult(isCompatible, Collections.emptySet());
    }
}
