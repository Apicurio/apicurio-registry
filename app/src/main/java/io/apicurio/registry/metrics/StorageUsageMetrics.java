package io.apicurio.registry.metrics;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.storage.metrics.StorageMetricsStore;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import java.util.function.ToDoubleFunction;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_OBSERVABILITY;
import static io.apicurio.registry.metrics.MetricsConstants.STORAGE_ARTIFACTS;
import static io.apicurio.registry.metrics.MetricsConstants.STORAGE_ARTIFACTS_DESCRIPTION;
import static io.apicurio.registry.metrics.MetricsConstants.STORAGE_ARTIFACT_VERSIONS;
import static io.apicurio.registry.metrics.MetricsConstants.STORAGE_ARTIFACT_VERSIONS_DESCRIPTION;

/**
 * Publishes how much is stored in the registry as Prometheus gauges.
 * <p>
 * The counts come from {@link StorageMetricsStore}, which already keeps them behind a short-lived cache for
 * the limits and health checks. Reading them here adds no queries of its own beyond that cache's refresh.
 */
@ApplicationScoped
public class StorageUsageMetrics {

    @Inject
    Logger log;

    @Inject
    MeterRegistry registry;

    @Inject
    StorageMetricsStore storageMetricsStore;

    @Info(description = """
                    Publish the number of stored artifacts and artifact versions as metrics.
            """, category = CATEGORY_OBSERVABILITY, availableSince = "3.3.2")
    @ConfigProperty(name = "apicurio.metrics.storage-usage.enabled", defaultValue = "true")
    boolean enabled;

    void onStart(@Observes StartupEvent ev) {
        if (!enabled) {
            log.debug("Storage usage metrics are disabled.");
            return;
        }
        register(STORAGE_ARTIFACTS, STORAGE_ARTIFACTS_DESCRIPTION,
                store -> store.getOrInitializeArtifactsCounter());
        register(STORAGE_ARTIFACT_VERSIONS, STORAGE_ARTIFACT_VERSIONS_DESCRIPTION,
                store -> store.getOrInitializeTotalSchemasCounter());
    }

    private void register(String name, String description, ToDoubleFunction<StorageMetricsStore> value) {
        Gauge.builder(name, storageMetricsStore, store -> safely(name, store, value))
                .description(description)
                .register(registry);
    }

    /**
     * A gauge is read while a scrape is in flight, which may be before storage is ready or while it is
     * unavailable. Reporting NaN leaves the sample out of the scrape rather than failing it, and is
     * preferable to reporting a zero that reads as "nothing stored".
     */
    private double safely(String name, StorageMetricsStore store, ToDoubleFunction<StorageMetricsStore> value) {
        try {
            return value.applyAsDouble(store);
        } catch (Exception ex) {
            log.debug("Could not read {}: {}", name, ex.getMessage());
            return Double.NaN;
        }
    }
}
