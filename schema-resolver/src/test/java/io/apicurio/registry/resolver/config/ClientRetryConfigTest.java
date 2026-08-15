package io.apicurio.registry.resolver.config;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientRetryConfigTest {

    @Test
    void clientRetryDefaultsMatchRegistryClientOptionsRetry() {
        SchemaResolverConfig config = new SchemaResolverConfig(Map.of());

        assertTrue(config.getClientRetryEnabled());
        // Must stay aligned with RegistryClientOptions#retry() defaults.
        assertEquals(3L, config.getClientRetryMaxAttempts());
        assertEquals(250L, config.getClientRetryDelayMs());
        assertEquals(2.0d, config.getClientRetryBackoffMultiplier());
        assertEquals(10000L, config.getClientRetryMaxDelayMs());
    }

    @Test
    void clientRetryKeysCanBeOverridden() {
        Map<String, Object> originals = new HashMap<>();
        originals.put(SchemaResolverConfig.CLIENT_RETRY_ENABLED, "false");
        originals.put(SchemaResolverConfig.CLIENT_RETRY_MAX_ATTEMPTS, "30");
        originals.put(SchemaResolverConfig.CLIENT_RETRY_DELAY_MS, "1000");
        originals.put(SchemaResolverConfig.CLIENT_RETRY_BACKOFF_MULTIPLIER, "1.5");
        originals.put(SchemaResolverConfig.CLIENT_RETRY_MAX_DELAY_MS, "30000");

        SchemaResolverConfig config = new SchemaResolverConfig(originals);

        assertFalse(config.getClientRetryEnabled());
        assertEquals(30L, config.getClientRetryMaxAttempts());
        assertEquals(1000L, config.getClientRetryDelayMs());
        assertEquals(1.5d, config.getClientRetryBackoffMultiplier());
        assertEquals(30000L, config.getClientRetryMaxDelayMs());
    }

    @Test
    void backoffMultiplierAcceptsNumberAndString() {
        Map<String, Object> numberCfg = new HashMap<>();
        numberCfg.put(SchemaResolverConfig.CLIENT_RETRY_BACKOFF_MULTIPLIER, 3);
        assertEquals(3.0d, new SchemaResolverConfig(numberCfg).getClientRetryBackoffMultiplier());

        Map<String, Object> stringCfg = new HashMap<>();
        stringCfg.put(SchemaResolverConfig.CLIENT_RETRY_BACKOFF_MULTIPLIER, "2.5");
        assertEquals(2.5d, new SchemaResolverConfig(stringCfg).getClientRetryBackoffMultiplier());
    }

    @Test
    void backoffMultiplierRejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class,
                () -> configWithBackoff("NaN").getClientRetryBackoffMultiplier());
        assertThrows(IllegalArgumentException.class,
                () -> configWithBackoff("-1").getClientRetryBackoffMultiplier());
        assertThrows(IllegalArgumentException.class,
                () -> configWithBackoff("1.0").getClientRetryBackoffMultiplier());
        assertThrows(IllegalArgumentException.class,
                () -> configWithBackoff("not-a-number").getClientRetryBackoffMultiplier());
    }

    private static SchemaResolverConfig configWithBackoff(Object value) {
        Map<String, Object> originals = new HashMap<>();
        originals.put(SchemaResolverConfig.CLIENT_RETRY_BACKOFF_MULTIPLIER, value);
        return new SchemaResolverConfig(originals);
    }
}
