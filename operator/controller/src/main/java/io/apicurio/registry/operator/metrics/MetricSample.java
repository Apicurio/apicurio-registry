package io.apicurio.registry.operator.metrics;

import java.util.Map;

/**
 * A single sample parsed from the Prometheus text exposition format.
 *
 * @param name   the metric name, without labels
 * @param labels the label set, empty when the sample carries no labels
 * @param value  the sample value, which may be {@link Double#NaN} or infinite
 */
public record MetricSample(String name, Map<String, String> labels, double value) {

    public String label(String key) {
        return labels.get(key);
    }
}
