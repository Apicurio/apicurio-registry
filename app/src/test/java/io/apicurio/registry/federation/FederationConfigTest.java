package io.apicurio.registry.federation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The startup check of the federated search settings.
 */
class FederationConfigTest {

    /** Enabled, with every setting at its default. */
    private static FederationConfig defaults() {
        FederationConfig config = new FederationConfig();
        config.enabled = true;
        config.searchDeadlineMs = 5000;
        config.searchPeerTimeoutMs = 3000;
        config.searchMaxPeers = 16;
        config.searchMaxConcurrentCalls = 64;
        config.searchMaxSearchesPerCaller = 2;
        config.searchMaxResponseBytes = 1048576;
        config.searchPerSourceLimitMax = 100;
        config.breakerRequestVolumeThreshold = 4;
        config.breakerFailureRatio = 0.5;
        config.breakerDelayMs = 30000;
        return config;
    }

    private static String problem(FederationConfig config) {
        return assertThrows(IllegalStateException.class, config::validate).getMessage();
    }

    @Test
    void theDefaultsAreValid() {
        assertDoesNotThrow(defaults()::validate);
    }

    @Test
    void thePeerTimeoutMustBeBelowTheDeadline() {
        FederationConfig config = defaults();
        config.searchPeerTimeoutMs = 5000;
        assertEquals("Invalid federation configuration: apicurio.federation.search.peer-timeout-ms (5000) must be "
                + "below apicurio.federation.search.deadline-ms (5000).", problem(config));

        config.searchPeerTimeoutMs = 6000;
        assertEquals("Invalid federation configuration: apicurio.federation.search.peer-timeout-ms (6000) must be "
                + "below apicurio.federation.search.deadline-ms (5000).", problem(config));

        config.searchPeerTimeoutMs = 4999;
        assertDoesNotThrow(config::validate);
    }

    @Test
    void timesMustBePositive() {
        FederationConfig config = defaults();
        config.searchPeerTimeoutMs = 0;
        assertEquals("Invalid federation configuration: apicurio.federation.search.peer-timeout-ms (0) must be "
                + "at least 1.", problem(config));

        config = defaults();
        config.searchDeadlineMs = 0;
        config.searchPeerTimeoutMs = -1;
        assertEquals("Invalid federation configuration: apicurio.federation.search.deadline-ms (0) must be at "
                + "least 1; apicurio.federation.search.peer-timeout-ms (-1) must be at least 1.", problem(config));
    }

    @Test
    void limitsMustBeAtLeastOne() {
        FederationConfig config = defaults();
        config.searchMaxPeers = 0;
        config.searchMaxConcurrentCalls = 0;
        config.searchMaxSearchesPerCaller = 0;
        config.searchMaxResponseBytes = 0;
        config.searchPerSourceLimitMax = 0;
        config.breakerRequestVolumeThreshold = 0;
        assertEquals("Invalid federation configuration: apicurio.federation.search.max-peers (0) must be at least 1; "
                + "apicurio.federation.search.max-concurrent-calls (0) must be at least 1; "
                + "apicurio.federation.search.max-searches-per-caller (0) must be at least 1; "
                + "apicurio.federation.search.max-response-bytes (0) must be at least 1; "
                + "apicurio.federation.search.per-source-limit.max (0) must be at least 1; "
                + "apicurio.federation.search.breaker.request-volume-threshold (0) must be at least 1.",
                problem(config));
    }

    @Test
    void theBreakerSettingsMustBeInRange() {
        FederationConfig config = defaults();
        config.breakerFailureRatio = 0;
        config.breakerDelayMs = -1;
        assertEquals("Invalid federation configuration: apicurio.federation.search.breaker.failure-ratio (0.0) "
                + "must be above 0 and at most 1; apicurio.federation.search.breaker.delay-ms (-1) must not be "
                + "negative.", problem(config));

        config = defaults();
        config.breakerFailureRatio = 1.5;
        assertEquals("Invalid federation configuration: apicurio.federation.search.breaker.failure-ratio (1.5) "
                + "must be above 0 and at most 1.", problem(config));

        config.breakerFailureRatio = 1;
        config.breakerDelayMs = 0;
        assertDoesNotThrow(config::validate);
    }

    @Test
    void nothingIsCheckedWhileFederationIsDisabled() {
        FederationConfig config = defaults();
        config.enabled = false;
        config.searchPeerTimeoutMs = 0;
        assertDoesNotThrow(config::validate);
    }
}
