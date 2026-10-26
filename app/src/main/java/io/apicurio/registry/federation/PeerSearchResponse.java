package io.apicurio.registry.federation;

import io.apicurio.registry.rest.v3.beans.AgentSearchResult;

import java.util.List;

/**
 * What a peer registry answered to a public-only agent search, once checked: the entries it
 * returned and the number of matches it reported, which can be more than it returned.
 */
record PeerSearchResponse(List<AgentSearchResult> agents, long reportedCount) {
}
