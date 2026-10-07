package io.apicurio.registry.federation;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.rest.ConflictException;
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
 * reported as {@code deadline_exceeded}. If this registry's own search fails, the whole request
 * fails and the calls in flight are cancelled.
 * <p>
 * Peers are asked for public agents only and the caller's credentials are never sent to them.
 * Each peer has its own circuit breaker, so a peer that keeps failing is skipped without any
 * effect on the others. Only transport failures, server errors and timeouts count against a
 * peer; a refusal made here, a 401 or 403, an unsupported peer, a malformed answer, a call that
 * was cancelled at the deadline and a call this registry had no capacity for do not.
 * <p>
 * Agents with the same group and artifact id from different sources are all returned: nothing
 * about a peer proves it publishes under the same identity as another source.
 */
@ApplicationScoped
public class FederatedSearchService {

    static final String LOCAL_SOURCE = "local";

    private static final Logger log = LoggerFactory.getLogger(FederatedSearchService.class);

    /**
     * Searches this registry on behalf of the caller, whose own visibility rules apply.
     */
    @FunctionalInterface
    public interface LocalSearch {
        AgentSearchResults search(int limit);
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

    public FederatedAgentSearchResults search(PeerQuery query, LocalSearch localSearch) {
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(config.getSearchDeadlineMs());

        List<PeerDto> peers = enabledPeers();
        pruneGuards(peers);

        List<Dispatch> dispatches = new ArrayList<>();
        try {
            for (PeerDto peer : peers) {
                dispatches.add(dispatch(peer, query));
            }
            AgentSearchResults local = localSearch.search(query.limit());
            awaitUntil(dispatches, deadlineNanos);
            return assemble(local, dispatches);
        } finally {
            dispatches.forEach(Dispatch::cancelIfRunning);
        }
    }

    private List<PeerDto> enabledPeers() {
        List<PeerDto> enabled = storage.getPeers().stream().filter(PeerDto::isEnabled)
                .sorted(Comparator.comparing(PeerDto::getPeerId)).toList();
        if (enabled.size() > config.getSearchMaxPeers()) {
            // Failing is deliberate: silently searching only some of the configured peers would
            // return results nobody can tell are incomplete.
            throw new ConflictException(enabled.size() + " peers are enabled, more than the "
                    + config.getSearchMaxPeers() + " a federated search can query. Disable some peers.");
        }
        return enabled;
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

    private FederatedAgentSearchResults assemble(AgentSearchResults local, List<Dispatch> dispatches) {
        List<FederatedAgentSearchResult> agents = new ArrayList<>();
        List<FederatedSearchSource> sources = new ArrayList<>();

        List<AgentSearchResult> localAgents = local.getAgents();
        localAgents.forEach(agent -> agents.add(toFederated(LOCAL_SOURCE, agent)));
        sources.add(source(LOCAL_SOURCE, FederatedSearchOutcome.ok, null, localAgents.size(),
                local.getCount() > localAgents.size()));

        for (Dispatch dispatch : dispatches) {
            String peerId = dispatch.peer.getPeerId();
            FederatedSearchSource source;
            try {
                PeerSearchResponse response = dispatch.future.getNow(null);
                if (response == null) {
                    // Cancelled at the deadline and not yet seen to be failed.
                    source = source(peerId, FederatedSearchOutcome.deadline_exceeded, null, 0, false);
                } else {
                    List<AgentSearchResult> included = distinct(response.agents());
                    included.forEach(agent -> agents.add(toFederated(peerId, agent)));
                    source = source(peerId, FederatedSearchOutcome.ok, null, included.size(),
                            response.reportedCount() > response.agents().size());
                }
            } catch (CompletionException | CancellationException ex) {
                source = failedSource(peerId, ex.getCause() != null ? ex.getCause() : ex);
            }
            sources.add(source);
            record(dispatch, source);
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

    private static FederatedAgentSearchResult toFederated(String sourceId, AgentSearchResult agent) {
        return FederatedAgentSearchResult.builder().source(sourceId).groupId(agent.getGroupId())
                .artifactId(agent.getArtifactId()).name(agent.getName()).description(agent.getDescription())
                .supportedInterfaces(agent.getSupportedInterfaces()).skills(agent.getSkills())
                .capabilities(agent.getCapabilities()).createdOn(agent.getCreatedOn()).owner(agent.getOwner())
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
