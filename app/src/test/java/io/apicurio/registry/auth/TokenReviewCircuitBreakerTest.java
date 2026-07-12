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

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenReviewCircuitBreakerTest {

    private static final Duration OPEN_DURATION = Duration.ofSeconds(30);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));

    private TokenReviewCircuitBreaker newBreaker(int threshold) {
        return new TokenReviewCircuitBreaker(threshold, OPEN_DURATION, clock,
                LoggerFactory.getLogger(TokenReviewCircuitBreakerTest.class));
    }

    @Test
    void staysClosedBelowThreshold() {
        TokenReviewCircuitBreaker cb = newBreaker(3);
        cb.recordFailure();
        cb.recordFailure();
        assertEquals(TokenReviewCircuitBreaker.State.CLOSED, cb.getState());
        assertTrue(cb.allowRequest());
    }

    @Test
    void opensAfterThresholdFailures() {
        TokenReviewCircuitBreaker cb = newBreaker(3);
        cb.recordFailure();
        cb.recordFailure();
        cb.recordFailure();
        assertEquals(TokenReviewCircuitBreaker.State.OPEN, cb.getState());
        assertFalse(cb.allowRequest());
    }

    @Test
    void successResetsFailureCounter() {
        TokenReviewCircuitBreaker cb = newBreaker(3);
        cb.recordFailure();
        cb.recordFailure();
        cb.recordSuccess();
        cb.recordFailure();
        cb.recordFailure();
        assertEquals(TokenReviewCircuitBreaker.State.CLOSED, cb.getState());
        assertTrue(cb.allowRequest());
    }

    @Test
    void halfOpenAllowsExactlyOneProbe() {
        TokenReviewCircuitBreaker cb = newBreaker(2);
        cb.recordFailure();
        cb.recordFailure();
        assertFalse(cb.allowRequest());

        clock.advance(OPEN_DURATION);

        // First request after the window becomes the single probe.
        assertTrue(cb.allowRequest());
        assertEquals(TokenReviewCircuitBreaker.State.HALF_OPEN, cb.getState());
        // A concurrent request while the probe is undecided is rejected.
        assertFalse(cb.allowRequest());
    }

    @Test
    void probeSuccessClosesCircuit() {
        TokenReviewCircuitBreaker cb = newBreaker(2);
        cb.recordFailure();
        cb.recordFailure();
        clock.advance(OPEN_DURATION);
        assertTrue(cb.allowRequest());

        cb.recordSuccess();

        assertEquals(TokenReviewCircuitBreaker.State.CLOSED, cb.getState());
        assertTrue(cb.allowRequest());
    }

    @Test
    void probeFailureReopensCircuit() {
        TokenReviewCircuitBreaker cb = newBreaker(2);
        cb.recordFailure();
        cb.recordFailure();
        clock.advance(OPEN_DURATION);
        assertTrue(cb.allowRequest());

        // A single failure in half-open reopens the circuit and restarts the timer.
        cb.recordFailure();
        assertEquals(TokenReviewCircuitBreaker.State.OPEN, cb.getState());
        assertFalse(cb.allowRequest());

        clock.advance(OPEN_DURATION);
        assertTrue(cb.allowRequest());
        assertEquals(TokenReviewCircuitBreaker.State.HALF_OPEN, cb.getState());
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration amount) {
            this.now = this.now.plus(amount);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
