package io.apicurio.registry.client.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link AdapterDetector}.
 *
 * Note: These tests run with both adapters available on the classpath
 * (since this is the test configuration). Tests for missing adapters
 * would require classloader isolation which is beyond the scope of unit tests.
 */
class AdapterDetectorTest {

    @Test
    void testIsVertxAvailable() {
        // Both adapters should be available in the test classpath
        assertTrue(AdapterDetector.isVertxAvailable(),
                "Vert.x adapter should be available in test classpath");
    }

    @Test
    void testIsJdkAdapterAvailable() {
        // Both adapters should be available in the test classpath
        assertTrue(AdapterDetector.isJdkAdapterAvailable(),
                "JDK adapter should be available in test classpath");
    }

    @Test
    void testResolveAdapterTypeAuto() {
        // With both adapters available, AUTO should resolve to VERTX (preferred)
        HttpAdapterType resolved = AdapterDetector.resolveAdapterType(HttpAdapterType.AUTO);

        assertEquals(HttpAdapterType.VERTX, resolved,
                "AUTO should resolve to VERTX when both adapters are available");
    }

    @Test
    void testResolveAdapterTypeNull() {
        // Null should be treated as AUTO
        HttpAdapterType resolved = AdapterDetector.resolveAdapterType(null);

        assertEquals(HttpAdapterType.VERTX, resolved,
                "null should be treated as AUTO and resolve to VERTX");
    }

    @Test
    void testResolveAdapterTypeVertxExplicit() {
        HttpAdapterType resolved = AdapterDetector.resolveAdapterType(HttpAdapterType.VERTX);

        assertEquals(HttpAdapterType.VERTX, resolved);
    }

    @Test
    void testResolveAdapterTypeJdkExplicit() {
        HttpAdapterType resolved = AdapterDetector.resolveAdapterType(HttpAdapterType.JDK);

        assertEquals(HttpAdapterType.JDK, resolved);
    }

    @Test
    void testHttpAdapterTypeEnumValues() {
        // Verify all enum values exist
        assertNotNull(HttpAdapterType.AUTO);
        assertNotNull(HttpAdapterType.VERTX);
        assertNotNull(HttpAdapterType.JDK);

        // Verify valueOf works
        assertEquals(HttpAdapterType.AUTO, HttpAdapterType.valueOf("AUTO"));
        assertEquals(HttpAdapterType.VERTX, HttpAdapterType.valueOf("VERTX"));
        assertEquals(HttpAdapterType.JDK, HttpAdapterType.valueOf("JDK"));
    }

    @Test
    void testHttpAdapterTypeEnumValuesCount() {
        // Verify there are exactly 3 adapter types
        assertEquals(3, HttpAdapterType.values().length);
    }

    @Test
    void testInvalidEnumValueThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                HttpAdapterType.valueOf("INVALID"));
    }
}
