package io.apicurio.registry.rules.compatibility;

import java.util.Collections;
import java.util.Set;

/**
 * Created by aohana
 *
 * Holds the result for a compatibility check
 * incompatibleDifferences - will contain values in case the schema type has difference type information in case the
 * new schema is not compatible (only JSON schema as of now)
 */
public class CompatibilityExecutionResult {

    private final Set<CompatibilityDifference> incompatibleDifferences;

    private CompatibilityExecutionResult(Set<CompatibilityDifference> incompatibleDifferences) {
        this.incompatibleDifferences = incompatibleDifferences;
    }

    public boolean isCompatible() {
        return incompatibleDifferences == null || incompatibleDifferences.isEmpty();
    }

    public Set<CompatibilityDifference> getIncompatibleDifferences() {
        return incompatibleDifferences;
    }

    public static CompatibilityExecutionResult compatible() {
        return new CompatibilityExecutionResult(Collections.emptySet());
    }
    
    /**
     * Creates an instance of {@link CompatibilityExecutionResult} that represents "incompatible" results.  This
     * variant takes the set of {@link CompatibilityDifference}s as the basis of the result.  A non-zero number
     * of differences indicates incompatibility.
     */
    public static CompatibilityExecutionResult incompatible(Set<CompatibilityDifference> incompatibleDifferences) {
        return new CompatibilityExecutionResult(incompatibleDifferences);
    }

    /**
     * Creates an instance of {@link CompatibilityExecutionResult} that represents "incompatible" results.  This
     * variant takes an Exception and converts that into a set of differences.  Ideally this would never be used,
     * but some artifact types do not have the level of granularity to report individual differences.
     */
    public static CompatibilityExecutionResult incompatible(Exception e) {
        CompatibilityDifference diff = new GenericCompatibilityDifference(e);
        return new CompatibilityExecutionResult(Collections.singleton(diff));
    }

    /**
     * Creates an instance of {@link CompatibilityExecutionResult} that represents "incompatible" results.  This
     * variant takes a message.
     */
    public static CompatibilityExecutionResult incompatible(String message) {
        CompatibilityDifference diff = new GenericCompatibilityDifference(message);
        return new CompatibilityExecutionResult(Collections.singleton(diff));
    }
}
