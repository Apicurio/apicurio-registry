package io.apicurio.registry.federation;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A failure guard keyed by peer.
 *
 * <p>MicroProfile's {@code @CircuitBreaker} binds its state to the annotated <em>method</em>, not to
 * the arguments. Placing it on a method that takes the peer URL as a parameter therefore trips one
 * breaker shared by every peer: four failures against a single dead registry would start rejecting
 * calls to healthy ones. This keeps one independent counter per peer instead.
 *
 * <p>SPIKE: a deliberately small implementation. Production should build per-peer guards through
 * SmallRye's programmatic {@code FaultTolerance} API rather than hand-rolling the state machine.
 */
@ApplicationScoped
public class PeerCircuitBreaker {

    private static final int FAILURE_THRESHOLD = 4;
    private static final long OPEN_DURATION_MS = 30_000L;

    private final Map<String, State> states = new ConcurrentHashMap<>();

    /**
     * True when the peer should be skipped because it has failed repeatedly and the cool-off has
     * not yet elapsed.
     */
    public boolean isOpen(String peer) {
        State state = states.get(peer);
        if (state == null) {
            return false;
        }
        long openUntil = state.openUntil.get();
        if (openUntil == 0L) {
            return false;
        }
        if (System.currentTimeMillis() >= openUntil) {
            // Cool-off elapsed. Half-open: allow the next call through to probe the peer.
            state.openUntil.set(0L);
            state.consecutiveFailures.set(0);
            return false;
        }
        return true;
    }

    public void recordSuccess(String peer) {
        State state = states.get(peer);
        if (state != null) {
            state.consecutiveFailures.set(0);
            state.openUntil.set(0L);
        }
    }

    public void recordFailure(String peer) {
        State state = states.computeIfAbsent(peer, k -> new State());
        if (state.consecutiveFailures.incrementAndGet() >= FAILURE_THRESHOLD) {
            state.openUntil.set(System.currentTimeMillis() + OPEN_DURATION_MS);
        }
    }

    private static final class State {
        private final AtomicInteger consecutiveFailures = new AtomicInteger();
        private final AtomicLong openUntil = new AtomicLong();
    }
}
