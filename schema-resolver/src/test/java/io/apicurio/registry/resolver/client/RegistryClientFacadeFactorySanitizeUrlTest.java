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

package io.apicurio.registry.resolver.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RegistryClientFacadeFactorySanitizeUrlTest {

    @Test
    void stripsUserInfoFromRegistryUrl() {
        assertEquals("https://registry.example.com/apis/registry/v3",
                RegistryClientFacadeFactory.sanitizeRegistryUrl(
                        "https://user:secret@registry.example.com/apis/registry/v3"));
    }

    @Test
    void passesThroughUrlWithoutUserInfo() {
        String url = "https://registry.example.com/apis/registry/v3";
        assertEquals(url, RegistryClientFacadeFactory.sanitizeRegistryUrl(url));
    }

    @Test
    void returnsNullAndBlankUnchanged() {
        assertNull(RegistryClientFacadeFactory.sanitizeRegistryUrl(null));
        assertEquals("", RegistryClientFacadeFactory.sanitizeRegistryUrl(""));
        assertEquals("   ", RegistryClientFacadeFactory.sanitizeRegistryUrl("   "));
    }

    @Test
    void malformedUrlReturnsInvalidMarkerWithoutEchoingInput() {
        // IllegalArgumentException from URI.create; must not echo raw input (may contain secrets).
        String malformed = "http://user:s ecret@[:::";
        assertEquals("<invalid-url>", RegistryClientFacadeFactory.sanitizeRegistryUrl(malformed));
    }
}
