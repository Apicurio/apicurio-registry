package io.apicurio.registry;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Pins the request-timeout bounds that every SDK client built by the test base classes carries,
 * so a stalled request fails the test after the 30 s connect or 60 s read-idle bound instead of
 * hanging until the CI class timeout (#10424). The values are literals on purpose: asserting
 * against the constants would keep passing if someone raised them.
 */
class BoundedClientOptionsTest {

    @Test
    void helperCarriesConnectAndReadIdleBounds() {
        // The helper only stores the Vert.x instance, so none is needed to read the bounds back.
        var options = AbstractResourceTestBase.boundedClientOptions("http://localhost:1", null);
        Assertions.assertEquals(30_000, options.getConnectTimeoutMs());
        Assertions.assertEquals(60_000, options.getReadIdleTimeoutMs());
        Assertions.assertEquals("http://localhost:1", options.getRegistryUrl());
    }
}
