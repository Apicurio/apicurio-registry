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
 * The counts come from {@link StorageMetricsStore}, which keeps them behind a cache that expires after
 * {@code apicurio.storage.metrics.cache.check-period.ms}. Reading them here issues no queries of its own,
 * but it does drive that cache: a gauge is evaluated on every scrape, so each expiry costs one
 * {@code countArtifacts} and one {@code countTotalArtifactVersions}. Those are aggregate counts over the
 * whole dataset, and on a large registry they are not cheap.
 * <p>
 * That is why this is off by default. Every storage limit defaults to disabled and returns before touching
 * these counters, so on a default deployment nothing loads them today, and turning this on would introduce
 * that load rather than share it. Enable it when the numbers are worth the queries, for instance when the
 * Operator is configured to report them in the custom resource status.
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
                    Publish the number of stored artifacts and artifact versions as metrics. Disabled by \
                    default: each cache expiry costs an aggregate count over the whole dataset, which is \
                    not cheap on a large registry. The cache period is controlled by \
                    apicurio.storage.metrics.cache.check-period.ms.
            """, category = CATEGORY_OBSERVABILITY, availableSince = "3.3.2")
    @ConfigProperty(name = "apicurio.metrics.storage-usage.enabled", defaultValue = "false")
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
