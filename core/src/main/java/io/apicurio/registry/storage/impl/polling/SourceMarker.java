package io.apicurio.registry.storage.impl.polling;

import java.time.Instant;
import java.util.Map;

/**
 * Marker identifying the current state of a polling data source.
 * <p>
 * Each polling storage implementation provides its own marker type (e.g., Git commit SHA,
 * Kubernetes resourceVersion). The marker carries enough information to:
 * <ul>
 *     <li>Produce a human-readable summary for logging</li>
 *     <li>Report per-source state for multi-source setups</li>
 *     <li>Provide a timestamp for metadata fallback (createdOn/modifiedOn)</li>
 * </ul>
 */
public interface SourceMarker {

    /**
     * Returns per-source identifiers as a map of source ID to a short identifier
     * string (e.g., abbreviated commit SHA, resource version). These values are
     * for display only — they appear in the status API's {@code sources} field
     * and are never parsed back into domain objects.
     * <p>
     * Must always contain at least one entry, even for single-source implementations.
     */
    Map<String, String> toSources();

    /**
     * Returns a compact, human-readable summary of this marker for log messages.
     * Default implementation formats the {@link #toSources()} map.
     */
    default String toDisplayString() {
        return toSources().entrySet().stream()
                .map(e -> e.getKey() + ":" + (e.getValue() != null ? e.getValue() : "none"))
                .collect(java.util.stream.Collectors.joining(", "));
    }

    /**
     * Returns the timestamp associated with this marker, used as a fallback
     * for entity createdOn/modifiedOn fields when not explicitly specified.
     */
    Instant getCommitTime();
}
