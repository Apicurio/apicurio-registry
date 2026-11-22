package io.apicurio.registry.metrics;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests the customizer against a real {@link AutoConfiguredOpenTelemetrySdkBuilder}, the type Quarkus hands
 * to it. The CDI wiring inside a running Quarkus application is covered by the usage metrics integration
 * tests.
 */
class UsageMetricsCustomizerTest {

    private static final AttributeKey<String> INSTANCE_ID = AttributeKey.stringKey("service.instance.id");
    private static final AttributeKey<String> SERVICE_NAME = AttributeKey.stringKey("service.name");

    @BeforeEach
    @AfterEach
    void resetGlobal() {
        GlobalOpenTelemetry.resetForTest();
    }

    private static UsageMetricsCustomizer customizer(boolean enabled, int maxSeries) {
        UsageMetricsConfig config = new UsageMetricsConfig();
        config.enabled = enabled;
        config.maxSeries = maxSeries;
        UsageMetricsCustomizer customizer = new UsageMetricsCustomizer();
        customizer.config = config;
        return customizer;
    }

    /** Builds an SDK the way Quarkus does: the customizer is applied to the autoconfigure builder. */
    private static MetricData recordFetches(UsageMetricsCustomizer customizer, InMemoryMetricReader reader,
            Map<String, String> properties, int distinctClients) {
        AutoConfiguredOpenTelemetrySdkBuilder builder = AutoConfiguredOpenTelemetrySdk.builder()
                .setResultAsGlobal()
                .disableShutdownHook()
                .addPropertiesSupplier(() -> properties)
                .addMeterProviderCustomizer((meterProvider, config) -> meterProvider.registerMetricReader(reader));
        customizer.customize(builder);
        builder.build();

        OTelMetricsProvider provider = new OTelMetricsProvider();
        provider.init();
        for (int i = 1; i <= distinctClients; i++) {
            provider.recordSchemaFetch("client-" + i, MetricsConstants.USAGE_IDENTIFIER_TYPE_GLOBAL_ID, i,
                    "SERIALIZE");
        }
        return reader.collectAllMetrics().stream()
                .filter(m -> m.getName().equals("apicurio.usage.schema.fetches")).findFirst().orElseThrow();
    }

    private static final Map<String, String> NO_EXPORTERS = Map.of("otel.metrics.exporter", "none",
            "otel.traces.exporter", "none", "otel.logs.exporter", "none");

    @Test
    void enabledAppliesTheCapAndAddsAnInstanceId() {
        MetricData metric = recordFetches(customizer(true, 4), InMemoryMetricReader.create(), NO_EXPORTERS, 9);

        List<LongPointData> points = List.copyOf(metric.getLongSumData().getPoints());
        assertEquals(4, points.size());
        assertEquals(9L, points.stream().mapToLong(LongPointData::getValue).sum());
        String instanceId = metric.getResource().getAttribute(INSTANCE_ID);
        assertEquals(UUID.fromString(instanceId).toString(), instanceId);
    }

    @Test
    void disabledChangesNeitherTheCapNorTheResource() {
        MetricData metric = recordFetches(customizer(false, 4), InMemoryMetricReader.create(), NO_EXPORTERS, 9);

        assertEquals(9, metric.getLongSumData().getPoints().size());
        assertNull(metric.getResource().getAttribute(INSTANCE_ID));
    }

    @Test
    void anInstanceIdFromTheEnvironmentStyleResourceConfigurationWins() {
        Map<String, String> properties = Map.of("otel.metrics.exporter", "none", "otel.traces.exporter", "none",
                "otel.logs.exporter", "none", "otel.resource.attributes",
                "service.instance.id=operator-pod-3,service.name=registry-eu");

        MetricData metric = recordFetches(customizer(true, 100), InMemoryMetricReader.create(), properties, 1);

        assertEquals("operator-pod-3", metric.getResource().getAttribute(INSTANCE_ID));
        assertEquals("registry-eu", metric.getResource().getAttribute(SERVICE_NAME));
    }

    @Test
    void aRestartedProcessWithReusedIdsExportsADifferentInstanceId() {
        // The first and the second process both hand out global ID 1, as an in-memory database does after a
        // restart. The measurements are identical, only the resource tells the two processes apart.
        MetricData first = recordFetches(customizer(true, 100), InMemoryMetricReader.create(), NO_EXPORTERS, 1);
        GlobalOpenTelemetry.resetForTest();
        MetricData second = recordFetches(customizer(true, 100), InMemoryMetricReader.create(), NO_EXPORTERS, 1);

        assertEquals(first.getLongSumData().getPoints().iterator().next().getAttributes(),
                second.getLongSumData().getPoints().iterator().next().getAttributes());
        assertNotEquals(first.getResource().getAttribute(INSTANCE_ID), second.getResource().getAttribute(INSTANCE_ID));
    }

    @Test
    void anOperatorDefinedInstanceIdIsReturnedUntouched() {
        Resource operator = Resource.create(Attributes.of(SERVICE_NAME, "registry-eu", INSTANCE_ID, "pod-7"));

        assertSame(operator, UsageMetricsCustomizer.withInstanceId(operator));
    }

    @Test
    void aMissingInstanceIdIsAddedWithoutChangingOtherAttributes() {
        Resource quarkus = Resource.create(Attributes.of(SERVICE_NAME, "registry-eu"));

        Resource result = UsageMetricsCustomizer.withInstanceId(quarkus);

        assertEquals("registry-eu", result.getAttribute(SERVICE_NAME));
        assertEquals(UUID.fromString(result.getAttribute(INSTANCE_ID)).toString(),
                result.getAttribute(INSTANCE_ID));
    }

    @Test
    void everyProcessStartGetsADifferentInstanceId() {
        // Each call stands for the SDK being built by one Registry process. An in-memory registry that
        // restarts hands out global and content IDs from 1 again, so only this value keeps the series of
        // the old and the new process apart.
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            ids.add(UsageMetricsCustomizer.withInstanceId(Resource.empty()).getAttribute(INSTANCE_ID));
        }
        assertEquals(50, ids.size());
    }
}
