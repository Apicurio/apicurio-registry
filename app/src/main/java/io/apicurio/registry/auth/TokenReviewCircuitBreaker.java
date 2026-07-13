/*
 * Copyright 2025 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.auth;

import org.slf4j.Logger;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * A small, thread-safe circuit breaker guarding the Kubernetes TokenReview API.
 *
 * <p>Failures represent TokenReview API errors (exceptions) rather than any specific token, so the
 * breaker state is global. After {@code threshold} consecutive failures the circuit opens and calls
 * are skipped for {@code openDuration}; the first request after that window becomes a single probe
 * (half-open). A successful probe closes the circuit, a failing probe reopens it. State transitions
 * are logged so operators can correlate them with K8s API issues.
 */
class TokenReviewCircuitBreaker {

    enum State {
        CLOSED, OPEN, HALF_OPEN
    }

    private final int threshold;
    private final Duration openDuration;
    private final Clock clock;
    private final Logger log;

    private State state = State.CLOSED;
    private int consecutiveFailures = 0;
    private Instant openedAt;

    TokenReviewCircuitBreaker(int threshold, Duration openDuration, Clock clock, Logger log) {
        this.threshold = Math.max(1, threshold);
        this.openDuration = Objects.requireNonNull(openDuration, "openDuration");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * Decides whether a TokenReview API call may proceed. When the circuit is open and the open
     * window has elapsed, the circuit moves to half-open and this call becomes the single probe.
     */
    synchronized boolean allowRequest() {
        if (state == State.CLOSED) {
            return true;
        }
        if (state == State.OPEN) {
            if (!clock.instant().isBefore(openedAt.plus(openDuration))) {
                state = State.HALF_OPEN;
                log.info("Kubernetes TokenReview circuit breaker HALF_OPEN: allowing one probe request");
                return true;
            }
            return false;
        }
        // HALF_OPEN: a probe request is already in flight
        return false;
    }

    /**
     * Records a successful TokenReview API call (including {@code authenticated=false} responses,
     * which are healthy API replies). Resets the failure counter and closes the circuit.
     */
    synchronized void recordSuccess() {
        if (state != State.CLOSED) {
            log.info("Kubernetes TokenReview circuit breaker CLOSED: TokenReview API recovered");
        }
        state = State.CLOSED;
        consecutiveFailures = 0;
    }

    /**
     * Records a failed TokenReview API call (an exception). Opens the circuit once the failure
     * threshold is reached, and reopens immediately on a failed half-open probe.
     */
    synchronized void recordFailure() {
        consecutiveFailures++;
        if (state == State.HALF_OPEN || (state == State.CLOSED && consecutiveFailures >= threshold)) {
            state = State.OPEN;
            openedAt = clock.instant();
            log.warn("Kubernetes TokenReview circuit breaker OPEN after {} consecutive API failures; "
                    + "skipping TokenReview calls for {}s", consecutiveFailures, openDuration.getSeconds());
        }
    }

    synchronized State getState() {
        return state;
    }
}
