/*
 * Copyright 2026 Red Hat
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

package io.apicurio.registry.resolver.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
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
        assertFalse(config.getRetryTransientErrors());
        assertEquals(Duration.ZERO, config.getRetryTotalTimeout());
        // Must stay aligned with RegistryClientOptions#retry() defaults
        // (pinned by RegistryClientOptionsRetryDefaultsTest in java-sdk/common).
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
        originals.put(SchemaResolverConfig.RETRY_TRANSIENT_ERRORS, "true");

        SchemaResolverConfig config = new SchemaResolverConfig(originals);

        assertFalse(config.getClientRetryEnabled());
        assertTrue(config.getRetryTransientErrors());
        assertEquals(30L, config.getClientRetryMaxAttempts());
        assertEquals(1000L, config.getClientRetryDelayMs());
        assertEquals(1.5d, config.getClientRetryBackoffMultiplier());
        assertEquals(30000L, config.getClientRetryMaxDelayMs());
    }

    @Test
    void maxAttemptsRejectsValuesThatDoNotFitInt() {
        Map<String, Object> originals = new HashMap<>();
        originals.put(SchemaResolverConfig.CLIENT_RETRY_MAX_ATTEMPTS, "5000000000");
        SchemaResolverConfig config = new SchemaResolverConfig(originals);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                config::getClientRetryMaxAttempts);
        assertTrue(ex.getMessage().contains(SchemaResolverConfig.CLIENT_RETRY_MAX_ATTEMPTS));
    }

    @Test
    void maxAttemptsRejectsZero() {
        Map<String, Object> originals = new HashMap<>();
        originals.put(SchemaResolverConfig.CLIENT_RETRY_MAX_ATTEMPTS, "0");
        SchemaResolverConfig config = new SchemaResolverConfig(originals);
        assertThrows(IllegalArgumentException.class, config::getClientRetryMaxAttempts);
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
        SchemaResolverConfig nan = configWithBackoff("NaN");
        SchemaResolverConfig negative = configWithBackoff("-1");
        SchemaResolverConfig one = configWithBackoff("1.0");
        SchemaResolverConfig notANumber = configWithBackoff("not-a-number");

        assertThrows(IllegalArgumentException.class, nan::getClientRetryBackoffMultiplier);
        assertThrows(IllegalArgumentException.class, negative::getClientRetryBackoffMultiplier);
        assertThrows(IllegalArgumentException.class, one::getClientRetryBackoffMultiplier);
        assertThrows(IllegalArgumentException.class, notANumber::getClientRetryBackoffMultiplier);
    }

    private static SchemaResolverConfig configWithBackoff(Object value) {
        Map<String, Object> originals = new HashMap<>();
        originals.put(SchemaResolverConfig.CLIENT_RETRY_BACKOFF_MULTIPLIER, value);
        return new SchemaResolverConfig(originals);
    }
}
