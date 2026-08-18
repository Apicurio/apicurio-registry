package io.apicurio.registry.federation;

import io.apicurio.registry.rest.v3.beans.AgentSearchResult;

import java.util.Collections;
import java.util.List;

/**
 * The outcome of querying a single source (the local registry, or one peer).
 *
 * <p>A peer that fails or times out produces an outcome with a non-OK status and no results,
 * rather than an exception. That is what allows the overall search to degrade instead of fail.
 */
public record PeerSearchOutcome(String source, Status status, List<AgentSearchResult> results) {

    public enum Status {
        /** Source answered, and applied the filters itself. */
        OK,
        /**
         * Source could not apply the structured filters, so it returned its unfiltered list and the
         * filters were applied here instead. The result set is the same; only the place the work
         * happened differs.
         */
        DEGRADED,
        TIMEOUT,
        ERROR,
        CIRCUIT_OPEN
    }

    public static PeerSearchOutcome ok(String source, List<AgentSearchResult> results) {
        return new PeerSearchOutcome(source, Status.OK, results);
    }

    public static PeerSearchOutcome degraded(String source, List<AgentSearchResult> results) {
        return new PeerSearchOutcome(source, Status.DEGRADED, results);
    }

    public static PeerSearchOutcome timeout(String source) {
        return new PeerSearchOutcome(source, Status.TIMEOUT, Collections.emptyList());
    }

    public static PeerSearchOutcome error(String source) {
        return new PeerSearchOutcome(source, Status.ERROR, Collections.emptyList());
    }

    public static PeerSearchOutcome circuitOpen(String source) {
        return new PeerSearchOutcome(source, Status.CIRCUIT_OPEN, Collections.emptyList());
    }
}
