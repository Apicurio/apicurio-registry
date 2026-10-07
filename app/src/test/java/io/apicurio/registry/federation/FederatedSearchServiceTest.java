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
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How a federated search combines this registry's own results with those of its peers, with the
 * calls to the peers replaced by programmable fakes. Runs in the application because the per-peer
 * circuit breakers are built with the container's fault tolerance, against the real storage.
 */
@QuarkusTest
@TestProfile(FederationEnabledProfile.class)
class FederatedSearchServiceTest {

    private static final PeerQuery QUERY = new PeerQuery(null, null, null, null, null, 10);

    @Inject
    FederatedSearchService service;

    @Inject
    FederationConfig config;

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    MeterRegistry meters;

    private FakePeers peers;
    private long savedDeadlineMs;
    private int savedMaxPeers;
    private int savedVolumeThreshold;
    private double savedFailureRatio;
    private long savedDelayMs;

    @BeforeEach
    void setUp() {
        savedDeadlineMs = config.searchDeadlineMs;
        savedMaxPeers = config.searchMaxPeers;
        savedVolumeThreshold = config.breakerRequestVolumeThreshold;
        savedFailureRatio = config.breakerFailureRatio;
        savedDelayMs = config.breakerDelayMs;

        config.searchDeadlineMs = 2000;
        config.searchMaxPeers = 16;
        config.breakerRequestVolumeThreshold = 4;
        config.breakerFailureRatio = 0.5;
        config.breakerDelayMs = 60_000;

        deleteAllPeers();
        service.pruneGuards(List.of());
        peers = new FakePeers();
        QuarkusMock.installMockForType(peers, PeerSearchClient.class);
    }

    @AfterEach
    void tearDown() {
        deleteAllPeers();
        service.pruneGuards(List.of());
        config.searchDeadlineMs = savedDeadlineMs;
        config.searchMaxPeers = savedMaxPeers;
        config.breakerRequestVolumeThreshold = savedVolumeThreshold;
        config.breakerFailureRatio = savedFailureRatio;
        config.breakerDelayMs = savedDelayMs;
    }

    private void deleteAllPeers() {
        storage.getPeers().forEach(peer -> storage.deletePeer(peer.getPeerId()));
    }

    /** Stands in for the client: each peer id answers however the test has told it to. */
    private static final class FakePeers extends PeerSearchClient {
        final Map<String, Function<PeerQuery, CompletableFuture<PeerSearchResponse>>> behaviours =
                new ConcurrentHashMap<>();
        final Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();
        final Map<String, AtomicInteger> cancelled = new ConcurrentHashMap<>();

        void answers(String peerId, PeerSearchResponse response) {
            behaviours.put(peerId, query -> CompletableFuture.completedFuture(response));
        }

        void fails(String peerId, PeerSearchException failure) {
            behaviours.put(peerId, query -> CompletableFuture.failedFuture(failure));
        }

        void neverAnswers(String peerId) {
            behaviours.put(peerId, query -> new CompletableFuture<>());
        }

        int callsTo(String peerId) {
            return calls.computeIfAbsent(peerId, id -> new AtomicInteger()).get();
        }

        int cancellationsOf(String peerId) {
            return cancelled.computeIfAbsent(peerId, id -> new AtomicInteger()).get();
        }

        @Override
        PeerCall search(PeerDto peer, PeerQuery query) {
            calls.computeIfAbsent(peer.getPeerId(), id -> new AtomicInteger()).incrementAndGet();
            CompletableFuture<PeerSearchResponse> future = behaviours.get(peer.getPeerId()).apply(query);
            future.whenComplete((response, failure) -> {
                if (failure instanceof PeerSearchException.Cancelled) {
                    cancelled.computeIfAbsent(peer.getPeerId(), id -> new AtomicInteger()).incrementAndGet();
                }
            });
            return new PeerCall(future);
        }
    }

    private static PeerDto peer(String peerId, boolean enabled) {
        return PeerDto.builder().peerId(peerId).url("https://" + peerId + ".example.com").enabled(enabled)
                .build();
    }

    private void registered(PeerDto... registered) {
        deleteAllPeers();
        for (PeerDto peer : registered) {
            storage.createPeer(peer);
        }
    }

    private static AgentSearchResult agent(String groupId, String artifactId) {
        return AgentSearchResult.builder().groupId(groupId).artifactId(artifactId).name(artifactId)
                .skills(List.of("skill")).build();
    }

    private static AgentSearchResults local(int count, AgentSearchResult... agents) {
        return AgentSearchResults.builder().count(count).agents(List.of(agents)).build();
    }

    private static PeerSearchResponse response(long reportedCount, AgentSearchResult... agents) {
        return new PeerSearchResponse(List.of(agents), reportedCount);
    }

    private FederatedAgentSearchResults search(AgentSearchResults local) {
        return service.search(QUERY, (limit, checkpoint) -> local);
    }

    private static FederatedSearchSource source(FederatedAgentSearchResults results, String id) {
        return results.getSources().stream().filter(source -> id.equals(source.getSource())).findFirst()
                .orElseThrow(() -> new AssertionError("No source " + id + " in " + results.getSources()));
    }

    private static List<String> agentIds(FederatedAgentSearchResults results) {
        return results.getAgents().stream().map(agent -> agent.getSource() + ":" + agent.getArtifactId())
                .collect(Collectors.toList());
    }

    private static void assertFailed(FederatedAgentSearchResults results, String id,
            FederatedSearchFailureReason reason) {
        FederatedSearchSource source = source(results, id);
        assertEquals(FederatedSearchOutcome.failed, source.getOutcome(), id);
        assertEquals(reason, source.getReason(), id);
        assertEquals(0, source.getCount(), id);
    }

    @Test
    void returnsThisRegistryFirstThenEachPeerInIdOrderWithItsSource() {
        registered(peer("zeta", true), peer("alpha", true));
        peers.answers("alpha", response(1, agent("g", "from-alpha")));
        peers.answers("zeta", response(1, agent("g", "from-zeta")));

        FederatedAgentSearchResults results = search(local(1, agent("g", "from-local")));

        assertEquals(List.of("local:from-local", "alpha:from-alpha", "zeta:from-zeta"), agentIds(results));
        assertEquals(List.of("local", "alpha", "zeta"),
                results.getSources().stream().map(FederatedSearchSource::getSource).collect(Collectors.toList()));
        results.getSources().forEach(source -> {
            assertEquals(FederatedSearchOutcome.ok, source.getOutcome());
            assertEquals(1, source.getCount());
            assertFalse(source.getTruncated());
        });
    }

    @Test
    void passesTheQueryOnToEveryPeer() {
        registered(peer("alpha", true));
        List<PeerQuery> seen = new ArrayList<>();
        peers.behaviours.put("alpha", query -> {
            seen.add(query);
            return CompletableFuture.completedFuture(response(0));
        });
        PeerQuery query = new PeerQuery("refund", List.of("s"), List.of("streaming"), List.of("text"),
                List.of("image"), 7);

        service.search(query, (limit, checkpoint) -> {
            assertEquals(7, limit);
            return local(0);
        });

        assertEquals(List.of(query), seen);
    }

    @Test
    void keepsIdenticalCoordinatesFromDifferentSourcesAndCollapsesRepeatsWithinOne() {
        registered(peer("alpha", true), peer("beta", true));
        peers.answers("alpha", response(3, agent("g", "same"), agent("g", "same"), agent("g", "other")));
        peers.answers("beta", response(1, agent("g", "same")));

        FederatedAgentSearchResults results = search(local(1, agent("g", "same")));

        assertEquals(List.of("local:same", "alpha:same", "alpha:other", "beta:same"), agentIds(results));
        assertEquals(2, source(results, "alpha").getCount());
    }

    @Test
    void sameNamedGroupWithNoGroupIdIsNotMixedUpWithADefaultGroup() {
        registered(peer("alpha", true));
        peers.answers("alpha", response(2, agent(null, "x"), agent("g", "x")));

        FederatedAgentSearchResults results = search(local(0));

        assertEquals(List.of("alpha:x", "alpha:x"), agentIds(results));
    }

    @Test
    void reportsPerSourceTruncationFromTheCountEachSourceReported() {
        registered(peer("alpha", true), peer("beta", true));
        peers.answers("alpha", response(5, agent("g", "a1"), agent("g", "a2")));
        peers.answers("beta", response(1, agent("g", "b1")));

        FederatedAgentSearchResults results = search(local(3, agent("g", "l1"), agent("g", "l2")));

        assertTrue(source(results, "local").getTruncated());
        assertEquals(2, source(results, "local").getCount());
        assertTrue(source(results, "alpha").getTruncated());
        assertEquals(2, source(results, "alpha").getCount());
        assertFalse(source(results, "beta").getTruncated());
    }

    @Test
    void disabledPeersAreNeitherCalledNorListed() {
        registered(peer("alpha", true), peer("off", false));
        peers.answers("alpha", response(0));

        FederatedAgentSearchResults results = search(local(0));

        assertEquals(0, peers.callsTo("off"));
        assertEquals(List.of("local", "alpha"),
                results.getSources().stream().map(FederatedSearchSource::getSource).collect(Collectors.toList()));
    }

    @Test
    void failsInsteadOfSilentlySkippingPeersWhenMoreAreEnabledThanTheMaximum() {
        config.searchMaxPeers = 2;
        registered(peer("a", true), peer("b", true), peer("c", true));
        peers.answers("a", response(0));
        peers.answers("b", response(0));
        peers.answers("c", response(0));

        assertThrows(ConflictException.class, () -> search(local(0)));

        assertEquals(0, peers.callsTo("a") + peers.callsTo("b") + peers.callsTo("c"));
    }

    @Test
    void everyFailureHasItsOwnOutcome() {
        registered(peer("unreachable", true), peer("refused", true), peer("slow", true), peer("broken", true),
                peer("denied", true), peer("garbled", true), peer("busy", true), peer("legacy", true),
                peer("bug", true));
        peers.fails("unreachable", new PeerSearchException.Unreachable("x", null));
        peers.fails("refused", new PeerSearchException.AddressRefused("x"));
        peers.fails("slow", new PeerSearchException.Timeout("x"));
        peers.fails("broken", new PeerSearchException.PeerError("x"));
        peers.fails("denied", new PeerSearchException.Unauthorized("x"));
        peers.fails("garbled", new PeerSearchException.InvalidResponse("x", null));
        peers.fails("busy", new PeerSearchException.CapacityExceeded("x"));
        peers.fails("legacy", new PeerSearchException.Unsupported("x"));
        peers.behaviours.put("bug", query -> CompletableFuture.failedFuture(new IllegalStateException("x")));

        FederatedAgentSearchResults results = search(local(0));

        assertFailed(results, "unreachable", FederatedSearchFailureReason.unreachable);
        assertFailed(results, "refused", FederatedSearchFailureReason.unreachable);
        assertFailed(results, "slow", FederatedSearchFailureReason.timeout);
        assertFailed(results, "broken", FederatedSearchFailureReason.peer_error);
        assertFailed(results, "denied", FederatedSearchFailureReason.unauthorized);
        assertFailed(results, "garbled", FederatedSearchFailureReason.invalid_response);
        assertFailed(results, "busy", FederatedSearchFailureReason.capacity_exceeded);
        assertFailed(results, "bug", FederatedSearchFailureReason.unreachable);
        FederatedSearchSource legacy = source(results, "legacy");
        assertEquals(FederatedSearchOutcome.unsupported, legacy.getOutcome());
        assertNull(legacy.getReason());
        assertEquals(0, legacy.getCount());
        assertTrue(results.getAgents().isEmpty());
    }

    @Test
    void aPeerThatMissesTheDeadlineIsCancelledAndTheOthersStillAnswer() {
        config.searchDeadlineMs = 300;
        registered(peer("hung", true), peer("ok", true));
        peers.neverAnswers("hung");
        peers.answers("ok", response(1, agent("g", "fine")));

        long start = System.nanoTime();
        FederatedAgentSearchResults results = search(local(1, agent("g", "mine")));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertTrue(elapsedMs < 1500, "The search should return soon after the deadline, took " + elapsedMs + " ms.");
        FederatedSearchSource hung = source(results, "hung");
        assertEquals(FederatedSearchOutcome.deadline_exceeded, hung.getOutcome());
        assertNull(hung.getReason());
        assertEquals(1, peers.cancellationsOf("hung"));
        assertEquals(List.of("local:mine", "ok:fine"), agentIds(results));
        assertEquals(FederatedSearchOutcome.ok, source(results, "ok").getOutcome());
    }

    @Test
    void aSlowLocalSearchLeavesNoTimeForPeersThatHaveNotAnswered() {
        config.searchDeadlineMs = 200;
        registered(peer("hung", true));
        peers.neverAnswers("hung");

        FederatedAgentSearchResults results = service.search(QUERY, (limit, checkpoint) -> {
            try {
                Thread.sleep(400);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return local(0);
        });

        assertEquals(FederatedSearchOutcome.deadline_exceeded, source(results, "hung").getOutcome());
        assertEquals(1, peers.cancellationsOf("hung"));
    }

    @Test
    void whenThisRegistrysOwnSearchFailsTheRequestFailsAndPeerCallsAreCancelled() {
        registered(peer("hung", true));
        peers.neverAnswers("hung");

        assertThrows(IllegalStateException.class, () -> service.search(QUERY, (limit, checkpoint) -> {
            throw new IllegalStateException("storage down");
        }));

        assertEquals(1, peers.cancellationsOf("hung"));
    }

    @Test
    void aLocalSearchThatRunsPastTheDeadlineIsStoppedAtItsNextCheckpoint() {
        config.searchDeadlineMs = 150;
        registered(peer("hung", true));
        peers.neverAnswers("hung");
        AtomicInteger units = new AtomicInteger();

        long start = System.nanoTime();
        assertThrows(SearchDeadlineExceededException.class, () -> service.search(QUERY, (limit, checkpoint) -> {
            for (int i = 0; i < 100; i++) {
                units.incrementAndGet();
                try {
                    Thread.sleep(20);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                checkpoint.run();
            }
            return local(0);
        }));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertTrue(units.get() < 100, "The search should have been stopped, ran " + units.get() + " units.");
        assertTrue(elapsedMs < 1500, "Stopping took " + elapsedMs + " ms.");
        assertEquals(1, peers.cancellationsOf("hung"));
    }

    @Test
    void theCheckpointOfALocalSearchThatIsInTimeDoesNotStopIt() {
        registered(peer("alpha", true));
        peers.answers("alpha", response(0));

        FederatedAgentSearchResults results = service.search(QUERY, (limit, checkpoint) -> {
            checkpoint.run();
            checkpoint.run();
            return local(1, agent("g", "mine"));
        });

        assertEquals(List.of("local:mine"), agentIds(results));
    }

    @Test
    void anIsolatedFailureOnTheFirstCallDoesNotOpenTheCircuit() {
        registered(peer("alpha", true));
        AtomicInteger calls = new AtomicInteger();
        peers.behaviours.put("alpha", query -> calls.incrementAndGet() == 1
                ? CompletableFuture.failedFuture(new PeerSearchException.Unreachable("cold", null))
                : CompletableFuture.completedFuture(response(0)));

        assertFailed(search(local(0)), "alpha", FederatedSearchFailureReason.unreachable);
        for (int i = 0; i < 8; i++) {
            assertEquals(FederatedSearchOutcome.ok, source(search(local(0)), "alpha").getOutcome());
        }
        assertEquals(9, peers.callsTo("alpha"));
    }

    @Test
    void aPeerThatKeepsFailingIsSkippedWithoutAffectingAnotherPeer() {
        registered(peer("bad", true), peer("good", true));
        peers.fails("bad", new PeerSearchException.PeerError("503"));
        peers.answers("good", response(1, agent("g", "fine")));

        for (int i = 0; i < 4; i++) {
            assertFailed(search(local(0)), "bad", FederatedSearchFailureReason.peer_error);
        }
        FederatedAgentSearchResults results = search(local(0));

        assertFailed(results, "bad", FederatedSearchFailureReason.circuit_open);
        assertEquals(4, peers.callsTo("bad"), "An open circuit must not reach the peer.");
        assertEquals(FederatedSearchOutcome.ok, source(results, "good").getOutcome());
        assertEquals(5, peers.callsTo("good"));
    }

    @Test
    void aPeerWhoseCircuitIsOpenIsCalledAgainOnceTheDelayHasPassed() throws Exception {
        config.breakerDelayMs = 200;
        registered(peer("bad", true));
        peers.fails("bad", new PeerSearchException.Timeout("slow"));
        for (int i = 0; i < 4; i++) {
            search(local(0));
        }
        assertFailed(search(local(0)), "bad", FederatedSearchFailureReason.circuit_open);

        Thread.sleep(400);
        peers.answers("bad", response(0));

        assertEquals(FederatedSearchOutcome.ok, source(search(local(0)), "bad").getOutcome());
        assertEquals(5, peers.callsTo("bad"));
    }

    @Test
    void onlyTransportFailuresServerErrorsAndTimeoutsCountAgainstAPeer() {
        List<PeerSearchException> notThePeersFault = List.of(
                new PeerSearchException.Unauthorized("401"),
                new PeerSearchException.Unsupported("legacy"),
                new PeerSearchException.InvalidResponse("garbage", null),
                new PeerSearchException.AddressRefused("blocked"),
                new PeerSearchException.CapacityExceeded("busy"),
                new PeerSearchException.Cancelled());
        for (PeerSearchException failure : notThePeersFault) {
            String id = failure.getClass().getSimpleName().toLowerCase(Locale.ROOT);
            registered(peer(id, true));
            peers.fails(id, failure);

            for (int i = 0; i < 12; i++) {
                search(local(0));
            }

            assertEquals(12, peers.callsTo(id), failure.getClass().getSimpleName() + " must not open the circuit");
        }
    }

    @Test
    void callsCancelledAtTheDeadlineDoNotCountAgainstThePeer() {
        config.searchDeadlineMs = 60;
        registered(peer("hung", true));
        peers.neverAnswers("hung");

        for (int i = 0; i < 12; i++) {
            assertEquals(FederatedSearchOutcome.deadline_exceeded, source(search(local(0)), "hung").getOutcome());
        }

        assertEquals(12, peers.callsTo("hung"));
    }

    @Test
    void recordsTheOutcomeOfEverySourceAndTheStateOfEveryCircuit() {
        registered(peer("metrics-good", true), peer("metrics-bad", true));
        peers.answers("metrics-good", response(0));
        peers.fails("metrics-bad", new PeerSearchException.Timeout("slow"));

        search(local(0));

        assertEquals(1, meters.get("apicurio.federation.peer.search").tag("peer", "metrics-good")
                .tag("outcome", "ok").tag("reason", "none").timer().count());
        assertEquals(1, meters.get("apicurio.federation.peer.search").tag("peer", "metrics-bad")
                .tag("outcome", "failed").tag("reason", "timeout").timer().count());
        assertEquals(0.0, meters.get("apicurio.federation.peer.circuit.state").tag("peer", "metrics-bad").gauge().value());

        for (int i = 0; i < 3; i++) {
            search(local(0));
        }
        assertEquals(2.0, meters.get("apicurio.federation.peer.circuit.state").tag("peer", "metrics-bad").gauge().value());
        assertEquals(0.0, meters.get("apicurio.federation.peer.circuit.state").tag("peer", "metrics-good").gauge().value());
    }

    @Test
    void forgetsThePeersThatWereRemoved() {
        registered(peer("gone", true));
        peers.answers("gone", response(0));
        search(local(0));
        assertEquals(1, meters.find("apicurio.federation.peer.circuit.state").gauges().size());

        registered(peer("other", true));
        peers.answers("other", response(0));
        search(local(0));

        assertEquals(List.of("other"), meters.find("apicurio.federation.peer.circuit.state").gauges().stream()
                .map(gauge -> gauge.getId().getTag("peer")).collect(Collectors.toList()));
    }

    @Test
    void aPeerWhoseUrlChangedStartsWithAClosedCircuit() {
        registered(peer("moved", true));
        peers.fails("moved", new PeerSearchException.PeerError("503"));
        for (int i = 0; i < 4; i++) {
            search(local(0));
        }
        assertFailed(search(local(0)), "moved", FederatedSearchFailureReason.circuit_open);

        registered(PeerDto.builder().peerId("moved").url("https://new-home.example.com").enabled(true).build());
        peers.answers("moved", response(0));

        assertEquals(FederatedSearchOutcome.ok, source(search(local(0)), "moved").getOutcome());
    }

    @Test
    void federatedResultsKeepEveryFieldOfTheAgentAndOnlyAddTheSource() {
        registered(peer("alpha", true));
        AgentSearchResult full = AgentSearchResult.builder().groupId("g").artifactId("a").name("Name")
                .description("Description").owner("someone").createdOn(42L).skills(List.of("s1", "s2")).build();
        peers.answers("alpha", response(1, full));

        FederatedAgentSearchResult federated = search(local(0)).getAgents().get(0);

        assertEquals("alpha", federated.getSource());
        assertEquals("g", federated.getGroupId());
        assertEquals("a", federated.getArtifactId());
        assertEquals("Name", federated.getName());
        assertEquals("Description", federated.getDescription());
        assertEquals("someone", federated.getOwner());
        assertEquals(42L, federated.getCreatedOn());
        assertEquals(List.of("s1", "s2"), federated.getSkills());
    }
}
