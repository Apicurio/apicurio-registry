package io.apicurio.registry.metrics.usage;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base of the profiles of the usage metrics integration tests: an enabled OpenTelemetry SDK that exports every
 * second over OTLP/HTTP to the receiver of {@link OtlpReceiverResource}, with SQL usage telemetry and the usage
 * metric at their defaults. A subclass adds what the scenario needs. It has no shared state, because Quarkus
 * loads profiles in a different class loader than the tests.
 */
public abstract class UsageMetricsTestProfile implements QuarkusTestProfile {

    /** The settings of the scenario, applied on top of the exporting SDK. */
    protected Map<String, String> scenario() {
        return Map.of();
    }

    @Override
    public final Map<String, String> getConfigOverrides() {
        Map<String, String> config = new HashMap<>();
        config.put("apicurio.features.experimental.enabled", "true");
        config.put("quarkus.otel.sdk.disabled", "false");
        config.put("quarkus.otel.exporter.otlp.protocol", "http/protobuf");
        config.put("quarkus.otel.exporter.otlp.timeout", "2s");
        config.put("quarkus.otel.metric.export.interval", "1s");
        config.put("quarkus.otel.traces.exporter", "none");
        config.put("quarkus.otel.logs.exporter", "none");
        config.putAll(scenario());
        return config;
    }

    @Override
    public List<TestResourceEntry> testResources() {
        return List.of(new TestResourceEntry(OtlpReceiverResource.class));
    }
}
