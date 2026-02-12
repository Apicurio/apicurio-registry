package io.apicurio.registry.metrics;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link OTelMetricsProvider}.
 * Tests verify that OpenTelemetry metrics are correctly recorded with proper attributes.
 *
 * Note: This test uses an isolated SdkMeterProvider instead of GlobalOpenTelemetry
 * to avoid classloader conflicts when running alongside Quarkus tests.
 */
class OTelMetricsProviderTest {

    private static final String INSTRUMENTATION_NAME = "io.apicurio.registry";

    private InMemoryMetricReader metricReader;
    private SdkMeterProvider meterProvider;
    private OTelMetricsProvider metricsProvider;

    @BeforeEach
    void setUp() {
        // Create an in-memory metric reader for testing
        metricReader = InMemoryMetricReader.create();

        // Create SDK with in-memory exporter - isolated from GlobalOpenTelemetry
        meterProvider = SdkMeterProvider.builder()
                .registerMetricReader(metricReader)
                .build();

        // Get a meter from the isolated SDK
        Meter meter = meterProvider.get(INSTRUMENTATION_NAME);

        // Create the metrics provider with the test meter (uses package-private constructor)
        metricsProvider = new OTelMetricsProvider(meter);
        metricsProvider.init();
    }

    @AfterEach
    void tearDown() {
        if (meterProvider != null) {
            meterProvider.close();
        }
    }

    @Test
    void testRecordArtifactCreated() {
        // Record artifact creation
        metricsProvider.recordArtifactCreated("test-group", "AVRO");

        // Collect metrics
        Collection<MetricData> metrics = metricReader.collectAllMetrics();

        // Find the artifact created counter
        Optional<MetricData> artifactCreatedMetric = metrics.stream()
                .filter(m -> m.getName().equals("apicurio.artifacts.created"))
                .findFirst();

        assertTrue(artifactCreatedMetric.isPresent(), "apicurio.artifacts.created metric should exist");

        // Verify the metric value and attributes
        MetricData metricData = artifactCreatedMetric.get();
        assertEquals("Total number of artifacts created", metricData.getDescription());

        LongPointData point = metricData.getLongSumData().getPoints().stream().findFirst().orElseThrow();
        assertEquals(1, point.getValue());
        assertEquals("test-group", point.getAttributes().get(AttributeKey.stringKey("groupId")));
        assertEquals("AVRO", point.getAttributes().get(AttributeKey.stringKey("artifactType")));
    }

    @Test
    void testRecordArtifactCreatedWithNullValues() {
        // Record artifact creation with null values
        metricsProvider.recordArtifactCreated(null, null);

        // Collect metrics
        Collection<MetricData> metrics = metricReader.collectAllMetrics();

        Optional<MetricData> artifactCreatedMetric = metrics.stream()
                .filter(m -> m.getName().equals("apicurio.artifacts.created"))
                .findFirst();

        assertTrue(artifactCreatedMetric.isPresent());

        LongPointData point = artifactCreatedMetric.get().getLongSumData().getPoints().stream().findFirst().orElseThrow();
        assertEquals("default", point.getAttributes().get(AttributeKey.stringKey("groupId")));
        assertEquals("unknown", point.getAttributes().get(AttributeKey.stringKey("artifactType")));
    }

    @Test
    void testRecordArtifactDeleted() {
        metricsProvider.recordArtifactDeleted("my-group", "JSON");

        Collection<MetricData> metrics = metricReader.collectAllMetrics();

        Optional<MetricData> artifactDeletedMetric = metrics.stream()
                .filter(m -> m.getName().equals("apicurio.artifacts.deleted"))
                .findFirst();

        assertTrue(artifactDeletedMetric.isPresent(), "apicurio.artifacts.deleted metric should exist");

        LongPointData point = artifactDeletedMetric.get().getLongSumData().getPoints().stream().findFirst().orElseThrow();
        assertEquals(1, point.getValue());
        assertEquals("my-group", point.getAttributes().get(AttributeKey.stringKey("groupId")));
        assertEquals("JSON", point.getAttributes().get(AttributeKey.stringKey("artifactType")));
    }

    @Test
    void testRecordVersionCreated() {
        metricsProvider.recordVersionCreated("default", "PROTOBUF");

        Collection<MetricData> metrics = metricReader.collectAllMetrics();

        Optional<MetricData> versionCreatedMetric = metrics.stream()
                .filter(m -> m.getName().equals("apicurio.versions.created"))
                .findFirst();

        assertTrue(versionCreatedMetric.isPresent(), "apicurio.versions.created metric should exist");

        LongPointData point = versionCreatedMetric.get().getLongSumData().getPoints().stream().findFirst().orElseThrow();
        assertEquals(1, point.getValue());
    }

    @Test
    void testRecordSchemaValidationSuccess() {
        metricsProvider.recordSchemaValidation("AVRO", true);

        Collection<MetricData> metrics = metricReader.collectAllMetrics();

        Optional<MetricData> validationMetric = metrics.stream()
                .filter(m -> m.getName().equals("apicurio.schema.validations"))
                .findFirst();

        assertTrue(validationMetric.isPresent(), "apicurio.schema.validations metric should exist");

        LongPointData point = validationMetric.get().getLongSumData().getPoints().stream().findFirst().orElseThrow();
        assertEquals(1, point.getValue());
        assertEquals("AVRO", point.getAttributes().get(AttributeKey.stringKey("artifactType")));
        assertEquals("success", point.getAttributes().get(AttributeKey.stringKey("result")));
    }

    @Test
    void testRecordSchemaValidationFailure() {
        metricsProvider.recordSchemaValidation("JSON", false);

        Collection<MetricData> metrics = metricReader.collectAllMetrics();

        Optional<MetricData> validationMetric = metrics.stream()
                .filter(m -> m.getName().equals("apicurio.schema.validations"))
                .findFirst();

        assertTrue(validationMetric.isPresent());

        LongPointData point = validationMetric.get().getLongSumData().getPoints().stream().findFirst().orElseThrow();
        assertEquals("failure", point.getAttributes().get(AttributeKey.stringKey("result")));
    }

    @Test
    void testRecordRuleEvaluation() {
        metricsProvider.recordRuleEvaluation("CREATE", true);

        Collection<MetricData> metrics = metricReader.collectAllMetrics();

        Optional<MetricData> ruleMetric = metrics.stream()
                .filter(m -> m.getName().equals("apicurio.rules.evaluated"))
                .findFirst();

        assertTrue(ruleMetric.isPresent(), "apicurio.rules.evaluated metric should exist");

        LongPointData point = ruleMetric.get().getLongSumData().getPoints().stream().findFirst().orElseThrow();
        assertEquals(1, point.getValue());
        assertEquals("CREATE", point.getAttributes().get(AttributeKey.stringKey("operation")));
        assertEquals("success", point.getAttributes().get(AttributeKey.stringKey("result")));
    }

    @Test
    void testRecordSearchRequest() {
        metricsProvider.recordSearchRequest("artifacts");

        Collection<MetricData> metrics = metricReader.collectAllMetrics();

        Optional<MetricData> searchMetric = metrics.stream()
                .filter(m -> m.getName().equals("apicurio.search.requests"))
                .findFirst();

        assertTrue(searchMetric.isPresent(), "apicurio.search.requests metric should exist");

        LongPointData point = searchMetric.get().getLongSumData().getPoints().stream().findFirst().orElseThrow();
        assertEquals(1, point.getValue());
        assertEquals("artifacts", point.getAttributes().get(AttributeKey.stringKey("operation")));
    }

    @Test
    void testMultipleRecordings() {
        // Record multiple artifacts
        metricsProvider.recordArtifactCreated("group1", "AVRO");
        metricsProvider.recordArtifactCreated("group1", "AVRO");
        metricsProvider.recordArtifactCreated("group2", "JSON");

        Collection<MetricData> metrics = metricReader.collectAllMetrics();

        Optional<MetricData> artifactCreatedMetric = metrics.stream()
                .filter(m -> m.getName().equals("apicurio.artifacts.created"))
                .findFirst();

        assertTrue(artifactCreatedMetric.isPresent());

        // Should have points for different attribute combinations
        long totalPoints = artifactCreatedMetric.get().getLongSumData().getPoints().size();
        assertTrue(totalPoints >= 1, "Should have metric points recorded");

        // Sum all values
        long totalValue = artifactCreatedMetric.get().getLongSumData().getPoints().stream()
                .mapToLong(LongPointData::getValue)
                .sum();
        assertEquals(3, totalValue, "Total artifact count should be 3");
    }
}
