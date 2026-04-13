package io.apicurio.registry.iceberg.metrics;

import io.apicurio.registry.metrics.OTelMetricsProvider;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.apicurio.common.apps.config.Info;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_ICEBERG;

import static io.apicurio.registry.metrics.MetricsConstants.ICEBERG_COMMIT_CONFLICTS;
import static io.apicurio.registry.metrics.MetricsConstants.ICEBERG_COMMIT_CONFLICTS_DESCRIPTION;
import static io.apicurio.registry.metrics.MetricsConstants.ICEBERG_COMMIT_DURATION;
import static io.apicurio.registry.metrics.MetricsConstants.ICEBERG_COMMIT_DURATION_DESCRIPTION;
import static io.apicurio.registry.metrics.MetricsConstants.ICEBERG_ERRORS;
import static io.apicurio.registry.metrics.MetricsConstants.ICEBERG_ERRORS_DESCRIPTION;
import static io.apicurio.registry.metrics.MetricsConstants.ICEBERG_NAMESPACE_OPERATIONS;
import static io.apicurio.registry.metrics.MetricsConstants.ICEBERG_NAMESPACE_OPERATIONS_DESCRIPTION;
import static io.apicurio.registry.metrics.MetricsConstants.ICEBERG_TABLE_OPERATIONS;
import static io.apicurio.registry.metrics.MetricsConstants.ICEBERG_TABLE_OPERATIONS_DESCRIPTION;
import static io.apicurio.registry.metrics.MetricsConstants.ICEBERG_TAG_ENTITY_TYPE;
import static io.apicurio.registry.metrics.MetricsConstants.ICEBERG_TAG_ERROR_TYPE;
import static io.apicurio.registry.metrics.MetricsConstants.ICEBERG_TAG_OPERATION;
import static io.apicurio.registry.metrics.MetricsConstants.ICEBERG_TAG_RESULT;
import static io.apicurio.registry.metrics.MetricsConstants.ICEBERG_VIEW_OPERATIONS;
import static io.apicurio.registry.metrics.MetricsConstants.ICEBERG_VIEW_OPERATIONS_DESCRIPTION;

/**
 * Metrics service for Iceberg REST Catalog operations. Tracks business-level events such as namespace/table/view
 * lifecycle operations, commit durations, commit conflicts, and error types. Records metrics via both Micrometer
 * (Prometheus) and OpenTelemetry (OTLP) for full observability coverage.
 */
@ApplicationScoped
public class IcebergMetricsService {

    @Inject
    MeterRegistry registry;

    @Inject
    OTelMetricsProvider otelMetrics;

    @ConfigProperty(name = "apicurio.metrics.iceberg.enabled", defaultValue = "true")
    @Info(category = CATEGORY_ICEBERG, description = "Enable Iceberg-specific metrics collection", availableSince = "3.0.0")
    boolean enabled;

    private boolean active;

    @PostConstruct
    void init() {
        active = enabled && registry != null;
    }

    // Namespace operations

    public void recordNamespaceCreated() {
        incrementOperationCounter(ICEBERG_NAMESPACE_OPERATIONS, ICEBERG_NAMESPACE_OPERATIONS_DESCRIPTION,
                "created", "success");
        otelMetrics.recordIcebergNamespaceOperation("created");
    }

    public void recordNamespaceDeleted() {
        incrementOperationCounter(ICEBERG_NAMESPACE_OPERATIONS, ICEBERG_NAMESPACE_OPERATIONS_DESCRIPTION,
                "deleted", "success");
        otelMetrics.recordIcebergNamespaceOperation("deleted");
    }

    public void recordNamespaceUpdated() {
        incrementOperationCounter(ICEBERG_NAMESPACE_OPERATIONS, ICEBERG_NAMESPACE_OPERATIONS_DESCRIPTION,
                "updated", "success");
        otelMetrics.recordIcebergNamespaceOperation("updated");
    }

    // Table operations

    public void recordTableCreated() {
        incrementOperationCounter(ICEBERG_TABLE_OPERATIONS, ICEBERG_TABLE_OPERATIONS_DESCRIPTION, "created",
                "success");
        otelMetrics.recordIcebergTableOperation("created");
    }

    public void recordTableDeleted() {
        incrementOperationCounter(ICEBERG_TABLE_OPERATIONS, ICEBERG_TABLE_OPERATIONS_DESCRIPTION, "deleted",
                "success");
        otelMetrics.recordIcebergTableOperation("deleted");
    }

    public void recordTableRenamed() {
        incrementOperationCounter(ICEBERG_TABLE_OPERATIONS, ICEBERG_TABLE_OPERATIONS_DESCRIPTION, "renamed",
                "success");
        otelMetrics.recordIcebergTableOperation("renamed");
    }

    public void recordTableCommitted() {
        incrementOperationCounter(ICEBERG_TABLE_OPERATIONS, ICEBERG_TABLE_OPERATIONS_DESCRIPTION,
                "committed", "success");
        otelMetrics.recordIcebergTableOperation("committed");
    }

    // View operations

    public void recordViewCreated() {
        incrementOperationCounter(ICEBERG_VIEW_OPERATIONS, ICEBERG_VIEW_OPERATIONS_DESCRIPTION, "created",
                "success");
        otelMetrics.recordIcebergViewOperation("created");
    }

    public void recordViewDeleted() {
        incrementOperationCounter(ICEBERG_VIEW_OPERATIONS, ICEBERG_VIEW_OPERATIONS_DESCRIPTION, "deleted",
                "success");
        otelMetrics.recordIcebergViewOperation("deleted");
    }

    public void recordViewRenamed() {
        incrementOperationCounter(ICEBERG_VIEW_OPERATIONS, ICEBERG_VIEW_OPERATIONS_DESCRIPTION, "renamed",
                "success");
        otelMetrics.recordIcebergViewOperation("renamed");
    }

    public void recordViewReplaced() {
        incrementOperationCounter(ICEBERG_VIEW_OPERATIONS, ICEBERG_VIEW_OPERATIONS_DESCRIPTION, "replaced",
                "success");
        otelMetrics.recordIcebergViewOperation("replaced");
    }

    // Commit conflicts

    public void recordCommitConflict(String entityType) {
        if (!active) {
            return;
        }
        Counter.builder(ICEBERG_COMMIT_CONFLICTS).description(ICEBERG_COMMIT_CONFLICTS_DESCRIPTION)
                .tag(ICEBERG_TAG_ENTITY_TYPE, entityType).register(registry).increment();
        otelMetrics.recordIcebergCommitConflict(entityType);
    }

    // Commit duration timer

    public Timer.Sample startCommitTimer() {
        if (!active) {
            return null;
        }
        return Timer.start(registry);
    }

    public void stopCommitTimer(Timer.Sample sample, String entityType, String result) {
        if (!active || sample == null) {
            return;
        }
        Timer timer = Timer.builder(ICEBERG_COMMIT_DURATION).description(ICEBERG_COMMIT_DURATION_DESCRIPTION)
                .tag(ICEBERG_TAG_ENTITY_TYPE, entityType).tag(ICEBERG_TAG_RESULT, result).register(registry);
        sample.stop(timer);
    }

    // Error tracking

    public void recordIcebergError(String errorType) {
        if (!active) {
            return;
        }
        Counter.builder(ICEBERG_ERRORS).description(ICEBERG_ERRORS_DESCRIPTION)
                .tag(ICEBERG_TAG_ERROR_TYPE, errorType).register(registry).increment();
        otelMetrics.recordIcebergError(errorType);
    }

    // Internal helpers

    private void incrementOperationCounter(String metricName, String description, String operation,
            String result) {
        if (!active) {
            return;
        }
        Counter.builder(metricName).description(description).tag(ICEBERG_TAG_OPERATION, operation)
                .tag(ICEBERG_TAG_RESULT, result).register(registry).increment();
    }
}
