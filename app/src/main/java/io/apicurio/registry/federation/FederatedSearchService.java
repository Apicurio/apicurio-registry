package io.apicurio.registry.federation;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.rest.v3.beans.AgentSearchResult;
import io.apicurio.registry.rest.v3.beans.AgentSearchResults;
import io.apicurio.registry.rest.v3.beans.FederatedAgentSearchResult;
import io.apicurio.registry.rest.v3.beans.FederatedAgentSearchResults;
import io.apicurio.registry.rest.v3.beans.FederatedSearchFailureReason;
import io.apicurio.registry.rest.v3.beans.FederatedSearchOutcome;
import io.apicurio.registry.rest.v3.beans.FederatedSearchSource;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.PeerDto;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.smallrye.faulttolerance.api.CircuitBreakerState;
import io.smallrye.faulttolerance.api.TypedGuard;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Searches the agent cards of this registry and of its enabled peer registries in one request.
 * <p>
 * Every source is searched for at most {@code limit} agents, and each gets an outcome of its own,
 * so that one slow or failing peer shows up as a typed outcome and not as a failed or slow
 * response. The peers are called in parallel while this registry's own search runs on the calling
 * thread, all under one deadline: whatever has not answered when it passes is cancelled and
 * reported as {@code deadline_exceeded}. This registry's own search must finish within the
 * deadline: it is stopped at its next checkpoint once the deadline has passed, and checked again
 * when it returns, so that a last unit of work, or a search that found nothing, cannot finish late
 * and still be answered. If it fails, or is stopped, the whole request fails and the calls in
 * flight are cancelled.
 * <p>
 * Peers are asked for public agents only and the caller's credentials are never sent to them.
 * Each peer has its own circuit breaker, so a peer that keeps failing is skipped without any
 * effect on the others. Only transport failures, server errors and timeouts count against a
 * peer; a refusal made here, a 401 or 403, an unsupported peer, a malformed answer, a call that
 * was cancelled at the deadline and a call this registry had no capacity for do not.
 * <p>
 * Agents with the same group and artifact id from different sources are all returned: nothing
 * about a peer proves it publishes under the same identity as another source.
 * <p>
 * At most {@code max-peers} peers are searched: if more are enabled, which the peer management API
 * refuses but which a lowered setting, declarative loading or two concurrent updates can still lead
 * to, the first ones in peer id order are searched and the rest are reported as
 * {@code capacity_exceeded}, so that the response still says which peers were left out.
 * <p>
 * A search calls every peer it searches, so one request leads to as many outbound calls. One caller can
 * have only so many searches in progress; a search beyond that still searches this registry but
 * calls no peer, and reports every peer as {@code capacity_exceeded}, as when the calls in progress
 * across all searches are at their limit.
 * <p>
 * The peers are configuration only administrators may list, so only a caller who may list them is
 * shown which peer is which and why one failed. Any other caller sees each peer as {@code peer-1},
 * {@code peer-2} and so on, numbered in peer id order within the one response, with its outcome
 * but without the reason for a failure.
 */
@ApplicationScoped
public class FederatedSearchService {

    static final String LOCAL_SOURCE = "local";

    /** With a number after it, how a peer is named to a caller who may not list the peers. */
    static final String NUMBERED_PEER_PREFIX = "peer-";

    /**
     * Sent on every call to a peer. A registry that receives a federated search carrying it refuses
     * it, so a search is forwarded at most once.
     */
    public static final String HOP_HEADER = "X-Federation-Hop";

    private static final Logger log = LoggerFactory.getLogger(FederatedSearchService.class);

    /**
     * Searches this registry on behalf of the caller, whose own visibility rules apply.
     */
    @FunctionalInterface
    public interface LocalSearch {

        /**
         * @param checkpoint run between the units of work of the search; it throws once the deadline
         *            of the federated search has passed
         */
        AgentSearchResults search(int limit, Runnable checkpoint);
    }

    @Inject
    FederationConfig config;

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    PeerSearchClient client;

    @Inject
    MeterRegistry meters;

    private final ConcurrentMap<String, PeerGuard> guards = new ConcurrentHashMap<>();

    private final KeyedPermits searchesInProgress = new KeyedPermits();

    /** How many enabled peers were last found beyond max-peers, so that it is logged once a change. */
    private final AtomicInteger peersLeftOut = new AtomicInteger();

    public FederatedAgentSearchResults search(PeerQuery query, FederatedSearchCaller caller,
            LocalSearch localSearch) {
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(config.getSearchDeadlineMs());

        List<PeerDto> peers = enabledPeers();
        pruneGuards(peers);
        int maxPeers = config.getSearchMaxPeers();
        warnIfPeersAreLeftOut(peers.size() - maxPeers);

        boolean admitted = searchesInProgress.tryAcquire(caller.key(), config.getSearchMaxSearchesPerCaller());
        List<Dispatch> dispatches = new ArrayList<>();
        try {
            for (int i = 0; i < peers.size(); i++) {
                PeerDto peer = peers.get(i);
                if (i >= maxPeers) {
                    dispatches.add(refused(peer, "More peers are enabled than a federated search queries."));
                } else if (!admitted) {
                    dispatches.add(refused(peer, "The caller has too many federated searches in progress."));
                } else {
                    dispatches.add(dispatch(peer, query));
                }
            }
            Runnable checkpoint = () -> {
                if (System.nanoTime() - deadlineNanos > 0) {
                    throw new SearchDeadlineExceededException(config.getSearchDeadlineMs());
                }
            };
            checkpoint.run();
            AgentSearchResults local = localSearch.search(query.limit(), checkpoint);
            checkpoint.run();
            awaitUntil(dispatches, deadlineNanos);
            return assemble(local, dispatches, caller);
        } finally {
            dispatches.forEach(Dispatch::cancelIfRunning);
            if (admitted) {
                searchesInProgress.release(caller.key());
            }
        }
    }

    private List<PeerDto> enabledPeers() {
        return storage.getPeers().stream().filter(PeerDto::isEnabled)
                .sorted(Comparator.comparing(PeerDto::getPeerId)).toList();
    }

    private void warnIfPeersAreLeftOut(int leftOut) {
        int count = Math.max(0, leftOut);
        if (peersLeftOut.getAndSet(count) != count && count > 0) {
            log.warn("{} enabled peers are more than the {} a federated search queries "
                    + "(apicurio.federation.search.max-peers): the last {} in peer id order are not searched.",
                    count + config.getSearchMaxPeers(), config.getSearchMaxPeers(), count);
        }
    }

    private Dispatch dispatch(PeerDto peer, PeerQuery query) {
        Dispatch dispatch = new Dispatch(peer);
        CompletableFuture<PeerSearchResponse> future;
        try {
            CompletionStage<PeerSearchResponse> stage = guardFor(peer).call(() -> {
                PeerCall call = client.search(peer, query);
                dispatch.call = call;
                return call.future();
            });
            future = stage.toCompletableFuture();
        } catch (Exception ex) {
            future = CompletableFuture.failedFuture(ex);
        }
        dispatch.future = future;
        future.whenComplete((response, failure) -> dispatch.endNanos = System.nanoTime());
        return dispatch;
    }

    /** A peer that is not called, and is reported as capacity_exceeded. */
    private static Dispatch refused(PeerDto peer, String why) {
        Dispatch dispatch = new Dispatch(peer);
        dispatch.future = CompletableFuture.failedFuture(new PeerSearchException.CapacityExceeded(why));
        dispatch.endNanos = System.nanoTime();
        return dispatch;
    }

    private void awaitUntil(List<Dispatch> dispatches, long deadlineNanos) {
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (!dispatches.isEmpty() && remainingNanos > 0) {
            CompletableFuture<?>[] all = dispatches.stream().map(dispatch -> dispatch.future)
                    .toArray(CompletableFuture[]::new);
            try {
                CompletableFuture.allOf(all).get(remainingNanos, TimeUnit.NANOSECONDS);
            } catch (TimeoutException | ExecutionException ex) {
                // Timed out, or some calls failed: each source is reported on its own below.
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        dispatches.forEach(Dispatch::cancelIfRunning);
    }

    private FederatedAgentSearchResults assemble(AgentSearchResults local, List<Dispatch> dispatches,
            FederatedSearchCaller caller) {
        List<FederatedAgentSearchResult> agents = new ArrayList<>();
        List<FederatedSearchSource> sources = new ArrayList<>();

        List<AgentSearchResult> localAgents = local.getAgents();
        localAgents.forEach(agent -> agents.add(toFederated(LOCAL_SOURCE, agent, true)));
        sources.add(source(LOCAL_SOURCE, FederatedSearchOutcome.ok, null, localAgents.size(),
                local.getCount() > localAgents.size()));

        int position = 0;
        for (Dispatch dispatch : dispatches) {
            position++;
            String peerId = dispatch.peer.getPeerId();
            String shownAs = caller.seesPeerDetails() ? peerId : NUMBERED_PEER_PREFIX + position;
            FederatedSearchSource source;
            try {
                PeerSearchResponse response = dispatch.future.getNow(null);
                if (response == null) {
                    // Cancelled at the deadline and not yet seen to be failed.
                    source = source(peerId, FederatedSearchOutcome.deadline_exceeded, null, 0, false);
                } else {
                    List<AgentSearchResult> included = distinct(response.agents());
                    included.forEach(agent -> agents.add(toFederated(shownAs, agent, false)));
                    source = source(peerId, FederatedSearchOutcome.ok, null, included.size(),
                            response.reportedCount() > response.agents().size());
                }
            } catch (CompletionException | CancellationException ex) {
                source = failedSource(peerId, ex.getCause() != null ? ex.getCause() : ex);
            }
            record(dispatch, source);
            if (!caller.seesPeerDetails()) {
                source.setSource(shownAs);
                source.setReason(null);
            }
            sources.add(source);
        }

        return FederatedAgentSearchResults.builder().agents(agents).sources(sources).build();
    }

    private FederatedSearchSource failedSource(String peerId, Throwable cause) {
        if (cause instanceof PeerSearchException) {
            PeerSearchException failure = (PeerSearchException) cause;
            return source(peerId, failure.getOutcome(), failure.getReason(), 0, false);
        }
        if (cause instanceof CircuitBreakerOpenException) {
            return source(peerId, FederatedSearchOutcome.failed, FederatedSearchFailureReason.circuit_open, 0,
                    false);
        }
        log.warn("Unexpected failure searching peer {}", peerId, cause);
        return source(peerId, FederatedSearchOutcome.failed, FederatedSearchFailureReason.unreachable, 0, false);
    }

    private static FederatedSearchSource source(String id, FederatedSearchOutcome outcome,
            FederatedSearchFailureReason reason, int count, boolean truncated) {
        return FederatedSearchSource.builder().source(id).outcome(outcome).reason(reason).count(count)
                .truncated(truncated).build();
    }

    /** A peer may repeat an agent; within one source, group and artifact id identify it. */
    private static List<AgentSearchResult> distinct(List<AgentSearchResult> agents) {
        Map<String, AgentSearchResult> byCoordinates = new LinkedHashMap<>();
        for (AgentSearchResult agent : agents) {
            byCoordinates.putIfAbsent(String.valueOf(agent.getGroupId()) + '\u0000' + agent.getArtifactId(),
                    agent);
        }
        return new ArrayList<>(byCoordinates.values());
    }

    /**
     * An agent from a peer keeps what the peer said about it, except its owner: a principal of
     * another registry, asserted by a peer nobody authenticated, means nothing here and could pass
     * for one of this registry's own.
     */
    private static FederatedAgentSearchResult toFederated(String sourceId, AgentSearchResult agent,
            boolean local) {
        return FederatedAgentSearchResult.builder().source(sourceId).groupId(agent.getGroupId())
                .artifactId(agent.getArtifactId()).name(agent.getName()).description(agent.getDescription())
                .supportedInterfaces(agent.getSupportedInterfaces()).skills(agent.getSkills())
                .capabilities(agent.getCapabilities()).createdOn(agent.getCreatedOn())
                .owner(local ? agent.getOwner() : null)
                .build();
    }

    private void record(Dispatch dispatch, FederatedSearchSource source) {
        long endNanos = dispatch.endNanos != 0 ? dispatch.endNanos : System.nanoTime();
        Timer.builder("apicurio.federation.peer.search")
                .description("Time spent searching a peer registry, by outcome")
                .tag("peer", dispatch.peer.getPeerId())
                .tag("outcome", source.getOutcome().name())
                .tag("reason", source.getReason() == null ? "none" : source.getReason().name())
                .register(meters).record(endNanos - dispatch.startNanos, TimeUnit.NANOSECONDS);
    }

    private TypedGuard<CompletionStage<PeerSearchResponse>> guardFor(PeerDto peer) {
        return guards.compute(peer.getPeerId(), (peerId, existing) -> {
            if (existing != null && existing.url.equals(peer.getUrl())) {
                return existing;
            }
            if (existing != null) {
                meters.remove(existing.stateGauge);
            }
            return newGuard(peer);
        }).guard;
    }

    void pruneGuards(List<PeerDto> peers) {
        Set<String> current = new HashSet<>();
        peers.forEach(peer -> current.add(peer.getPeerId()));
        guards.entrySet().removeIf(entry -> {
            boolean gone = !current.contains(entry.getKey());
            if (gone) {
                meters.remove(entry.getValue().stateGauge);
            }
            return gone;
        });
    }

    private PeerGuard newGuard(PeerDto peer) {
        AtomicInteger state = new AtomicInteger(0);
        TypedGuard<CompletionStage<PeerSearchResponse>> guard = TypedGuard
                .create(new TypeLiteral<CompletionStage<PeerSearchResponse>>() {
                })
                .withCircuitBreaker()
                .requestVolumeThreshold(config.getBreakerRequestVolumeThreshold())
                .failureRatio(config.getBreakerFailureRatio())
                .delay(config.getBreakerDelayMs(), ChronoUnit.MILLIS)
                .successThreshold(1)
                .failOn(List.<Class<? extends Throwable>>of(PeerSearchException.Unreachable.class,
                        PeerSearchException.Timeout.class, PeerSearchException.PeerError.class))
                .onStateChange(newState -> state.set(stateCode(newState)))
                .done()
                .build();
        Gauge gauge = Gauge.builder("apicurio.federation.peer.circuit.state", state, AtomicInteger::get)
                .description("Circuit breaker state of a peer registry: 0 closed, 1 half open, 2 open")
                .tag("peer", peer.getPeerId()).register(meters);
        return new PeerGuard(peer.getUrl(), guard, gauge);
    }

    private static int stateCode(CircuitBreakerState state) {
        switch (state) {
            case CLOSED:
                return 0;
            case HALF_OPEN:
                return 1;
            default:
                return 2;
        }
    }

    private static final class PeerGuard {
        final String url;
        final TypedGuard<CompletionStage<PeerSearchResponse>> guard;
        final Gauge stateGauge;

        PeerGuard(String url, TypedGuard<CompletionStage<PeerSearchResponse>> guard, Gauge stateGauge) {
            this.url = url;
            this.guard = guard;
            this.stateGauge = stateGauge;
        }
    }

    private static final class Dispatch {
        final PeerDto peer;
        final long startNanos = System.nanoTime();
        volatile PeerCall call;
        volatile CompletableFuture<PeerSearchResponse> future;
        volatile long endNanos;

        Dispatch(PeerDto peer) {
            this.peer = peer;
        }

        void cancelIfRunning() {
            PeerCall running = call;
            if (running != null && !future.isDone()) {
                running.cancel();
            }
        }
    }
}
