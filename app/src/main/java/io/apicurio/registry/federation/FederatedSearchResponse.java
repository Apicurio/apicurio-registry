package io.apicurio.registry.federation;

import io.apicurio.registry.rest.v3.beans.AgentSearchResult;

import java.util.List;

/**
 * The merged outcome of a federated agent search.
 *
 * <p>{@code sources} carries one entry per source consulted, including the local registry, so a
 * caller can tell the difference between "no agent matched" and "the registry holding it did not
 * answer". A single global count is deliberately absent: the local total understates, and summing
 * peer totals double-counts duplicates, so per-source counts are reported instead.
 */
public record FederatedSearchResponse(List<AgentSearchResult> agents, List<PeerSearchOutcome> sources) {
}
