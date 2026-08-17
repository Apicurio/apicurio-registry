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

package io.apicurio.registry.client.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link RegistryClientOptions#retry()} defaults so schema-resolver
 * {@code CLIENT_RETRY_*_DEFAULT} values stay aligned when the enabled-with-defaults
 * path passes explicit values instead of calling the no-arg {@code retry()}.
 */
class RegistryClientOptionsRetryDefaultsTest {

    @Test
    void retryNoArgDefaults() {
        RegistryClientOptions options = RegistryClientOptions.create().retry();

        assertTrue(options.isRetryEnabled());
        assertEquals(3, options.getMaxRetryAttempts());
        assertEquals(250L, options.getRetryDelayMs());
        assertEquals(2.0d, options.getBackoffMultiplier());
        assertEquals(10000L, options.getMaxRetryDelayMs());
    }

    @Test
    void retryRejectsMultiplierOfOneSameAsSchemaResolverConfig() {
        assertThrows(IllegalArgumentException.class,
                () -> RegistryClientOptions.create().retry(true, 3, 250, 1.0, 10000));
    }
}
