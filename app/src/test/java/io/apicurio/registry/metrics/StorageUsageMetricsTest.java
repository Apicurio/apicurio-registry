package io.apicurio.registry.metrics;

import io.apicurio.registry.storage.metrics.StorageMetricsStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link StorageUsageMetrics}.
 */
class StorageUsageMetricsTest {

    /**
     * The names are asserted as literals rather than through {@link MetricsConstants}, so that renaming a
     * constant fails here. The Operator reads these off the Prometheus endpoint as `storage_artifacts` and
     * `storage_artifact_versions`, and it has no way to notice a rename on its own.
     */
    private static final String ARTIFACTS = "storage.artifacts";
    private static final String ARTIFACT_VERSIONS = "storage.artifact.versions";

    @Test
    void testGaugesReportTheStoredCounts() {
        var registry = new SimpleMeterRegistry();
        var store = counting(40, 120);
        var metrics = metrics(registry, store, true);

        metrics.onStart(null);

        assertEquals(40.0, registry.get(ARTIFACTS).gauge().value());
        assertEquals(120.0, registry.get(ARTIFACT_VERSIONS).gauge().value());
    }

    /**
     * A gauge is read while a scrape is in flight, which may be before storage is ready or while it is
     * unavailable. Reporting NaN leaves the sample out of the scrape entirely, where a zero would be
     * published and read as "nothing stored".
     */
    @Test
    void testAnUnreadableCountIsReportedAsNaNRatherThanZero() {
        var registry = new SimpleMeterRegistry();
        var store = failing();
        var metrics = metrics(registry, store, true);

        metrics.onStart(null);

        assertTrue(Double.isNaN(registry.get(ARTIFACTS).gauge().value()));
        assertTrue(Double.isNaN(registry.get(ARTIFACT_VERSIONS).gauge().value()));
    }

    /**
     * Off unless asked for. Every storage limit defaults to disabled and returns before touching these
     * counters, so on a default deployment nothing loads them, and registering the gauges would introduce an
     * aggregate count over the whole dataset on every cache expiry rather than share an existing one.
     */
    @Test
    void testNoGaugesAreRegisteredWhenDisabled() {
        var registry = new SimpleMeterRegistry();
        var store = counting(40, 120);
        var metrics = metrics(registry, store, false);

        metrics.onStart(null);

        assertNull(registry.find(ARTIFACTS).gauge());
        assertNull(registry.find(ARTIFACT_VERSIONS).gauge());
    }

    private static StorageUsageMetrics metrics(SimpleMeterRegistry registry, StorageMetricsStore store,
            boolean enabled) {
        var metrics = new StorageUsageMetrics();
        metrics.log = LoggerFactory.getLogger(StorageUsageMetricsTest.class);
        metrics.registry = registry;
        metrics.storageMetricsStore = store;
        metrics.enabled = enabled;
        return metrics;
    }

    private static StorageMetricsStore counting(long artifacts, long artifactVersions) {
        return new StorageMetricsStore() {
            @Override
            public long getOrInitializeArtifactsCounter() {
                return artifacts;
            }

            @Override
            public long getOrInitializeTotalSchemasCounter() {
                return artifactVersions;
            }
        };
    }

    private static StorageMetricsStore failing() {
        return new StorageMetricsStore() {
            @Override
            public long getOrInitializeArtifactsCounter() {
                throw new IllegalStateException("storage is not ready");
            }

            @Override
            public long getOrInitializeTotalSchemasCounter() {
                throw new IllegalStateException("storage is not ready");
            }
        };
    }
}
