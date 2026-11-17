package io.apicurio.registry.resolver.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers {@code CLIENT_RETRY_MAX_ATTEMPTS}'s {@code [1, CLIENT_RETRY_MAX_ATTEMPTS_MAX]} ceiling and
 * the new {@code RETRY_TRANSIENT_ERRORS} / {@code RETRY_TOTAL_TIMEOUT_MS} properties. Other
 * {@code CLIENT_RETRY_*} validation (defaults, overrides, NaN/<=1.0 backoff multiplier rejection,
 * values exceeding {@code Integer.MAX_VALUE}) is already covered by
 * {@code SchemaResolverConfigTest}.
 * <p>
 * The {@code [1, CLIENT_RETRY_MAX_ATTEMPTS_MAX]} ceiling (tighter than {@code Integer.MAX_VALUE})
 * exists so {@code AbstractSchemaResolver#estimateClientRetryLadderSleepMs}, which loops up to
 * {@code max-attempts - 1} times, cannot hang {@code configure()} on a fat-fingered value.
 */
class ClientRetryConfigTest {

    @Test
    void retryTransientErrorsAndTotalTimeoutDefaultToDisabled() {
        SchemaResolverConfig config = new SchemaResolverConfig(Map.of());

        assertFalse(config.getRetryTransientErrors());
        assertEquals(Duration.ZERO, config.getRetryTotalTimeout());
    }

    @Test
    void retryTransientErrorsAndTotalTimeoutCanBeOverridden() {
        Map<String, Object> originals = new HashMap<>();
        originals.put(SchemaResolverConfig.RETRY_TRANSIENT_ERRORS, "true");
        originals.put(SchemaResolverConfig.RETRY_TOTAL_TIMEOUT_MS, "5000");

        SchemaResolverConfig config = new SchemaResolverConfig(originals);

        assertEquals(true, config.getRetryTransientErrors());
        assertEquals(Duration.ofMillis(5000), config.getRetryTotalTimeout());
    }

    @Test
    void maxAttemptsRejectsAboveSaneCeiling() {
        // 2e9 fits in int (Integer.MAX_VALUE is ~2.1e9) but would hang configure() if
        // estimateClientRetryLadderSleepMs looped maxAttempts times.
        Map<String, Object> originals = new HashMap<>();
        originals.put(SchemaResolverConfig.CLIENT_RETRY_MAX_ATTEMPTS, "2000000000");
        SchemaResolverConfig config = new SchemaResolverConfig(originals);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                config::getClientRetryMaxAttempts);
        assertEquals(true, ex.getMessage().contains("1000"));
    }

    @Test
    void maxAttemptsAcceptsCeiling() {
        Map<String, Object> originals = new HashMap<>();
        originals.put(SchemaResolverConfig.CLIENT_RETRY_MAX_ATTEMPTS,
                String.valueOf(SchemaResolverConfig.CLIENT_RETRY_MAX_ATTEMPTS_MAX));
        assertEquals(SchemaResolverConfig.CLIENT_RETRY_MAX_ATTEMPTS_MAX,
                new SchemaResolverConfig(originals).getClientRetryMaxAttempts());
    }

    @Test
    void maxAttemptsRejectsZero() {
        Map<String, Object> originals = new HashMap<>();
        originals.put(SchemaResolverConfig.CLIENT_RETRY_MAX_ATTEMPTS, "0");
        SchemaResolverConfig config = new SchemaResolverConfig(originals);
        assertThrows(IllegalArgumentException.class, config::getClientRetryMaxAttempts);
    }
}
