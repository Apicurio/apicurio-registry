package io.apicurio.registry.storage;

import io.apicurio.common.apps.config.Info;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_USAGE;

@ApplicationScoped
public class UsageTelemetryConfig {

    @ConfigProperty(name = "apicurio.usage.telemetry.enabled", defaultValue = "false")
    @Info(category = CATEGORY_USAGE, description = "Enable usage telemetry collection from SerDes clients", availableSince = "3.1.0")
    boolean enabled;

    @ConfigProperty(name = "apicurio.usage.active-threshold-days", defaultValue = "7")
    @Info(category = CATEGORY_USAGE, description = "Number of days within which a schema is classified as ACTIVE", availableSince = "3.1.0")
    int activeThresholdDays;

    @ConfigProperty(name = "apicurio.usage.stale-threshold-days", defaultValue = "30")
    @Info(category = CATEGORY_USAGE, description = "Number of days after which a schema is classified as STALE", availableSince = "3.1.0")
    int staleThresholdDays;

    @ConfigProperty(name = "apicurio.usage.dead-threshold-days", defaultValue = "90")
    @Info(category = CATEGORY_USAGE, description = "Number of days after which a schema is classified as DEAD", availableSince = "3.1.0")
    int deadThresholdDays;

    @ConfigProperty(name = "apicurio.usage.drift-alert-threshold", defaultValue = "2")
    @Info(category = CATEGORY_USAGE, description = "Number of versions behind latest before triggering a drift alert", availableSince = "3.1.0")
    int driftAlertThreshold;

    public boolean isEnabled() {
        return enabled;
    }

    public int getActiveThresholdDays() {
        return activeThresholdDays;
    }

    public int getStaleThresholdDays() {
        return staleThresholdDays;
    }

    public int getDeadThresholdDays() {
        return deadThresholdDays;
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

    public long getDeadMsThreshold() {
        return Duration.ofDays(deadThresholdDays).toMillis();
    }
}
