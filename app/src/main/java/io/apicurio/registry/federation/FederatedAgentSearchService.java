package io.apicurio.registry.federation;

import io.apicurio.registry.rest.v3.beans.AgentInterface;
import io.apicurio.registry.rest.v3.beans.AgentSearchResult;
import io.apicurio.registry.rest.v3.beans.AgentSearchResults;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Fans an agent search out across the local registry and every configured peer, then merges the
 * results.
 *
 * <p>Peers are queried in parallel on a bounded pool and awaited against a single deadline. A peer
 * that fails or does not answer in time becomes a non-OK {@link PeerSearchOutcome} rather than an
 * exception, so one unreachable registry degrades the result instead of failing the request.
 *
 * <p>SPIKE: peers come from static configuration. There is no persistence, no management API and
 * no UI. The purpose is to exercise the merge, timeout and deduplication semantics.
 */
@ApplicationScoped
public class FederatedAgentSearchService {

    private static final Logger log = LoggerFactory.getLogger(FederatedAgentSearchService.class);

    private static final String LOCAL_SOURCE = "local";
    private static final int POOL_SIZE = 8;

    @Inject
    FederationConfig config;

    @Inject
    PeerClient peerClient;

    @Inject
    PeerCircuitBreaker breaker;

    @Inject
    AgentResultFilter resultFilter;

    /**
     * Bounded on purpose. An unbounded pool would allow (peers x concurrent searches) threads,
     * which is a denial of service against ourselves under load.
     */
    private final ExecutorService pool = Executors.newFixedThreadPool(POOL_SIZE);

    @PreDestroy
    void shutdown() {
        pool.shutdownNow();
    }

    /**
     * Merges already-computed local results with results from every configured peer.
     *
     * @param localResults results the local registry produced for this query
     * @param offset       offset into the merged result set
     * @param limit        page size of the merged result set
     */
    public FederatedSearchResponse search(List<AgentSearchResult> localResults, String name,
            List<String> skills, List<String> capabilities, int offset, int limit) {

        List<PeerSearchOutcome> outcomes = new ArrayList<>();

        // Local is added first so that, on a deduplication tie, the local registry wins. Your own
        // registry is authoritative for agents it also holds.
        outcomes.add(PeerSearchOutcome.ok(LOCAL_SOURCE, localResults));

        List<String> peers = config.getPeers();
        if (!peers.isEmpty()) {
            outcomes.addAll(queryPeers(peers, name, skills, capabilities, offset, limit));
        }

        List<AgentSearchResult> merged = mergeAndPage(outcomes, offset, limit);
        return new FederatedSearchResponse(merged, outcomes);
    }

    private List<PeerSearchOutcome> queryPeers(List<String> peers, String name, List<String> skills,
            List<String> capabilities, int offset, int limit) {

        List<CompletableFuture<PeerSearchOutcome>> futures = new ArrayList<>();
        for (String peer : peers) {
            futures.add(CompletableFuture.supplyAsync(
                    () -> queryOnePeer(peer, name, skills, capabilities, offset, limit), pool));
        }

        // allOf alone completes only when every future completes, which would let one dead peer
        // hold the whole response. completeOnTimeout puts a ceiling on the wait; anything still
        // unfinished is reported as a timeout below.
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .completeOnTimeout(null, config.getTimeoutMs(), TimeUnit.MILLISECONDS)
                .join();

        List<PeerSearchOutcome> outcomes = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            CompletableFuture<PeerSearchOutcome> future = futures.get(i);
            if (future.isDone() && !future.isCompletedExceptionally()) {
                outcomes.add(future.join());
            } else {
                future.cancel(true);
                outcomes.add(PeerSearchOutcome.timeout(peers.get(i)));
            }
        }
        return outcomes;
    }

    private PeerSearchOutcome queryOnePeer(String peer, String name, List<String> skills,
            List<String> capabilities, int offset, int limit) {

        // Checked per peer rather than per method. A single dead registry must not stop calls to
        // healthy ones, which is what a method-scoped breaker would do.
        if (breaker.isOpen(peer)) {
            log.debug("Skipping peer {}: circuit open", peer);
            return PeerSearchOutcome.circuitOpen(peer);
        }

        boolean structured = hasStructuredFilters(skills, capabilities);

        try {
            AgentSearchResults results =
                    peerClient.search(peer, name, skills, capabilities, offset, limit);
            breaker.recordSuccess(peer);
            return PeerSearchOutcome.ok(peer, agentsOf(results));
        } catch (Exception e) {
            if (structured) {
                // The peer may simply be unable to apply structured filters: they map to
                // SearchFilterType.structure, which only ElasticsearchSearchService handles, and
                // SqlSearchRepository throws on it (#8058). Retry without them and filter here.
                return retryDegraded(peer, name, skills, capabilities, offset, limit, e);
            }
            breaker.recordFailure(peer);
            log.warn("Federated search failed for peer {}: {}", peer, e.getMessage());
            return PeerSearchOutcome.error(peer);
        }
    }

    private PeerSearchOutcome retryDegraded(String peer, String name, List<String> skills,
            List<String> capabilities, int offset, int limit, Exception original) {
        try {
            AgentSearchResults results =
                    peerClient.search(peer, name, null, null, offset, limit);
            List<AgentSearchResult> filtered =
                    resultFilter.apply(agentsOf(results), skills, capabilities);
            breaker.recordSuccess(peer);
            log.info("Peer {} could not apply structured filters, filtered locally instead", peer);
            return PeerSearchOutcome.degraded(peer, filtered);
        } catch (Exception retryFailure) {
            breaker.recordFailure(peer);
            log.warn("Federated search failed for peer {}: {} (unfiltered retry also failed: {})",
                    peer, original.getMessage(), retryFailure.getMessage());
            return PeerSearchOutcome.error(peer);
        }
    }

    private boolean hasStructuredFilters(List<String> skills, List<String> capabilities) {
        return (skills != null && !skills.isEmpty())
                || (capabilities != null && !capabilities.isEmpty());
    }

    private List<AgentSearchResult> agentsOf(AgentSearchResults results) {
        return results.getAgents() == null ? List.of() : results.getAgents();
    }

    /**
     * Concatenates every source, removes duplicates, sorts, then applies the requested page.
     *
     * <p>Paging happens here rather than at each source, because a source numbers only its own
     * results. Peer position N is not position N of the merged set, which is why each peer was
     * asked for its first {@code offset + limit} rows instead of its own slice.
     */
    private List<AgentSearchResult> mergeAndPage(List<PeerSearchOutcome> outcomes, int offset, int limit) {
        Map<String, AgentSearchResult> deduped = new LinkedHashMap<>();

        for (PeerSearchOutcome outcome : outcomes) {
            for (AgentSearchResult agent : outcome.results()) {
                deduped.putIfAbsent(identityOf(agent, outcome.source()), agent);
            }
        }

        List<AgentSearchResult> merged = new ArrayList<>(deduped.values());

        // artifactId is a tiebreaker, not decoration. Two agents sharing a createdOn have undefined
        // relative order without it, and an unstable sort makes an item appear on two pages or none.
        merged.sort(Comparator
                .comparing(AgentSearchResult::getCreatedOn,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(AgentSearchResult::getArtifactId,
                        Comparator.nullsLast(Comparator.naturalOrder())));

        int from = Math.min(offset, merged.size());
        int to = Math.min(from + limit, merged.size());
        return merged.subList(from, to);
    }

    /**
     * Identity of an agent across registries.
     *
     * <p>Keyed on the first declared interface URL, because an agent is defined by where it answers
     * rather than by which catalogue lists it. Two registries may both hold
     * {@code default/translator} for entirely different agents, so group and artifact ID are not
     * globally unique. Falls back to source-qualified coordinates when no interface is declared,
     * which can never collapse two distinct agents.
     */
    private String identityOf(AgentSearchResult agent, String source) {
        List<AgentInterface> interfaces = agent.getSupportedInterfaces();
        if (interfaces != null && !interfaces.isEmpty()) {
            String url = interfaces.get(0).getUrl();
            if (url != null && !url.isBlank()) {
                return "url:" + url;
            }
        }
        return "ga:" + source + "/" + agent.getGroupId() + "/" + agent.getArtifactId();
    }
}
