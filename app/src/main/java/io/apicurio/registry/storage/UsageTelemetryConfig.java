package io.apicurio.registry.storage;

import io.apicurio.common.apps.config.Info;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_USAGE;

@ApplicationScoped
public class UsageTelemetryConfig {

    @ConfigProperty(name = "apicurio.usage.telemetry.enabled", defaultValue = "false")
    @Info(category = CATEGORY_USAGE, description = "Enable usage telemetry collection from SerDes clients", availableSince = "3.3.0", experimental = true)
    boolean enabled;

    @ConfigProperty(name = "apicurio.usage.flush.every", defaultValue = "10s")
    @Info(category = CATEGORY_USAGE, description = "How often buffered usage events are written to storage (Quarkus scheduler expression)", availableSince = "3.3.0")
    String flushEvery;

    @ConfigProperty(name = "apicurio.usage.active-threshold-days", defaultValue = "7")
    @Info(category = CATEGORY_USAGE, description = "Number of days within which a schema is classified as ACTIVE", availableSince = "3.3.0")
    int activeThresholdDays;

    @ConfigProperty(name = "apicurio.usage.stale-threshold-days", defaultValue = "30")
    @Info(category = CATEGORY_USAGE, description = "A schema whose last fetch is older than the active threshold and no older than this many days is classified as STALE. Older than this is DEAD.", availableSince = "3.3.0")
    int staleThresholdDays;

    /**
     * @deprecated It has no effect: a schema is classified as DEAD once its last fetch is older than the
     *             stale threshold. It will be removed in 4.0.0.
     */
    @Deprecated(since = "3.4.0", forRemoval = true)
    @ConfigProperty(name = "apicurio.usage.dead-threshold-days", defaultValue = "90")
    @Info(category = CATEGORY_USAGE, description = "Deprecated, has no effect, and will be removed in 4.0.0: a schema is classified as DEAD once its last fetch is older than the stale threshold", availableSince = "3.3.0")
    int deadThresholdDays;

    @ConfigProperty(name = "apicurio.usage.drift-alert-threshold", defaultValue = "2")
    @Info(category = CATEGORY_USAGE, description = "Number of versions behind latest before triggering a drift alert", availableSince = "3.3.0")
    int driftAlertThreshold;

    @ConfigProperty(name = "apicurio.usage.aggregation.every", defaultValue = "1h")
    @Info(category = CATEGORY_USAGE, description = "How often the usage metrics OTel gauges are updated (Quarkus scheduler expression)", availableSince = "3.3.0")
    String aggregationEvery;

    @ConfigProperty(name = "apicurio.usage.retention-days", defaultValue = "180")
    @Info(category = CATEGORY_USAGE, description = "Number of days to retain raw usage events before cleanup", availableSince = "3.3.0")
    int retentionDays;

    public boolean isEnabled() {
        return enabled;
    }

    public int getActiveThresholdDays() {
        return activeThresholdDays;
    }

    public int getStaleThresholdDays() {
        return staleThresholdDays;
    }

    public int getDriftAlertThreshold() {
        return driftAlertThreshold;
    }

    public long getActiveMsThreshold() {
        return Duration.ofDays(activeThresholdDays).toMillis();
    }

    public long getStaleMsThreshold() {
        return Duration.ofDays(staleThresholdDays).toMillis();
    }

    public long getRetentionMs() {
        return Duration.ofDays(retentionDays).toMillis();
    }

}
