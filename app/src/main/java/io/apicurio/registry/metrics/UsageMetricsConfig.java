package io.apicurio.registry.metrics;

import io.apicurio.common.apps.config.Info;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_USAGE;

/**
 * Configuration of the OpenTelemetry schema-fetch usage metric. It is independent of
 * {@code apicurio.usage.telemetry.enabled}, which only controls the SQL usage storage.
 */
@ApplicationScoped
public class UsageMetricsConfig {

    private static final Logger log = LoggerFactory.getLogger(UsageMetricsConfig.class);

    @Inject
    Config config;

    @ConfigProperty(name = "apicurio.usage.metrics.enabled", defaultValue = "false")
    @Info(category = CATEGORY_USAGE, description = "Export a per-client schema fetch counter (apicurio.usage.schema.fetches) through OpenTelemetry. This does not store usage data in Registry and is independent of apicurio.usage.telemetry.enabled. Requires OpenTelemetry metrics to be enabled at build time (quarkus.otel.metrics.enabled=true) and the SDK enabled at runtime (quarkus.otel.sdk.disabled=false)", availableSince = "3.4.0", experimental = true)
    boolean enabled;

    @ConfigProperty(name = "apicurio.usage.metrics.max-series", defaultValue = "2000")
    @Info(category = CATEGORY_USAGE, description = "Threshold for the number of distinct series (client, identifier type, identifier value and operation combinations) that the OpenTelemetry SDK keeps for the schema fetch counter in each Registry process. Once it is reached, a measurement for a combination that has no series yet is added to the overflow series (otel.metric.overflow=true), which has no client or identifier series labels, so the total stays exact. Sampled OTLP exemplars can still carry those attributes. The SDK checks the threshold without locking: when requests for new combinations arrive one at a time at most this value minus one of them keep their labels, but requests that arrive at the same moment can create a few series more than the value. Must be positive", availableSince = "3.4.0")
    int maxSeries;

    public boolean isEnabled() {
        return enabled;
    }

    public int getMaxSeries() {
        return maxSeries;
    }

    void onStartup(@Observes StartupEvent event) {
        validate();
    }

    /**
     * Fails startup on an unusable series limit while the metric is enabled, and warns when the metric
     * is enabled but the OpenTelemetry SDK that would export it is not.
     */
    void validate() {
        if (!enabled) {
            return;
        }
        if (maxSeries < 1) {
            throw new IllegalStateException("apicurio.usage.metrics.max-series must be a positive integer, but was "
                    + maxSeries);
        }
        boolean sdkDisabled = config.getOptionalValue("quarkus.otel.sdk.disabled", Boolean.class).orElse(true);
        if (sdkDisabled) {
            log.warn("apicurio.usage.metrics.enabled is true but the OpenTelemetry SDK is disabled "
                    + "(quarkus.otel.sdk.disabled=true), so no schema fetch usage metric is exported");
        }
    }
}
