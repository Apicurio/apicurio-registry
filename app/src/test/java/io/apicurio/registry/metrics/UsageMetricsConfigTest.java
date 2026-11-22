package io.apicurio.registry.metrics;

import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UsageMetricsConfigTest {

    private static UsageMetricsConfig config(boolean enabled, int maxSeries) {
        UsageMetricsConfig config = new UsageMetricsConfig();
        config.config = ConfigProvider.getConfig();
        config.enabled = enabled;
        config.maxSeries = maxSeries;
        return config;
    }

    @Test
    void enabledWithZeroSeriesFailsStartup() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> config(true, 0).validate());
        assertEquals("apicurio.usage.metrics.max-series must be a positive integer, but was 0", e.getMessage());
    }

    @Test
    void enabledWithNegativeSeriesFailsStartup() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> config(true, -5).validate());
        assertEquals("apicurio.usage.metrics.max-series must be a positive integer, but was -5", e.getMessage());
    }

    @Test
    void enabledWithOneSeriesIsAccepted() {
        assertDoesNotThrow(() -> config(true, 1).validate());
    }

    @Test
    void disabledIgnoresTheSeriesLimit() {
        assertDoesNotThrow(() -> config(false, 0).validate());
    }
}
