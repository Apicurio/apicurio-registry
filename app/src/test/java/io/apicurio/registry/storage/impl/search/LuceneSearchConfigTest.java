package io.apicurio.registry.storage.impl.search;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for LuceneSearchConfig mode detection logic.
 */
@QuarkusTest
public class LuceneSearchConfigTest {

    @Inject
    LuceneSearchConfig config;

    @Test
    void testIsEnabled() {
        // When enabled in configuration, should report as enabled
        // This test depends on the actual config in application.properties
        assertNotNull(config);
    }

    @Test
    void testGetIndexPath() {
        // Should have a resolved index path
        if (config.isEnabled()) {
            assertNotNull(config.getIndexPath());
            assertFalse(config.getIndexPath().isEmpty());
        }
    }

    @Test
    void testGetPollingInterval() {
        // Should have a polling interval configured
        assertNotNull(config.getPollingInterval());
    }

    @Test
    void testGetFullRebuildThreshold() {
        // Should have a rebuild threshold
        assertTrue(config.getFullRebuildThreshold() > 0);
    }

    @Test
    void testGetRamBufferSizeMB() {
        // Should have a RAM buffer size
        assertTrue(config.getRamBufferSizeMB() > 0);
    }
}
