package io.apicurio.registry.rules.compatibility;

import io.apicurio.registry.rules.compatibility.jsonschema.diff.Difference;

import java.util.Collections;
import java.util.Set;

/**
 * Created by aohana
 *
 * Holds the result for a compatibility check
 * isCompatible - whether the compatibility check is successful or not
 * incompatibleDifferences - will contain values in case the schema type has difference type information in case the
 * new schema is not compatible (only JSON schema as of now)
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
