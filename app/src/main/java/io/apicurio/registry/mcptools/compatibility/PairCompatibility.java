package io.apicurio.registry.mcptools.compatibility;

import java.util.List;

/**
 * The result of comparing a producer's {@code outputSchema} with a consumer's
 * {@code inputSchema}.
 *
 * @param verdict whether the producer's output can be passed to the consumer
 * @param reasons the mismatches that decide an {@link CompatibilityVerdict#INCOMPATIBLE} verdict
 * @param limitations everything that prevented a complete comparison
 */
public record PairCompatibility(CompatibilityVerdict verdict, List<CompatibilityReason> reasons,
        List<CompatibilityLimitation> limitations) {

    public PairCompatibility {
        reasons = List.copyOf(reasons);
        limitations = List.copyOf(limitations);
    }
}
