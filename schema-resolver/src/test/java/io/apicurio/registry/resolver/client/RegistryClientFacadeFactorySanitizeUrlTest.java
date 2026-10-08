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
