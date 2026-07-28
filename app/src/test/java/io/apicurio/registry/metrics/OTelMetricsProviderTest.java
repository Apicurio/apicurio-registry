/*
 * Copyright 2025 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.metrics;

import io.apicurio.registry.observability.OTelAttributes;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * End-to-end wiring tests for {@link OTelMetricsProvider}.
 * <p>
 * Verifies that every counter and gauge defined in the provider increments
 * by the exact expected amount, records correct attributes, and does not
 * leak increments into unrelated counters (negative cases).
 */
class OTelMetricsProviderTest {

    private InMemoryMetricReader metricReader;
    private OTelMetricsProvider metricsProvider;

    @BeforeEach
    void setUp() {
        GlobalOpenTelemetry.resetForTest();

        metricReader = InMemoryMetricReader.create();

        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                .registerMetricReader(metricReader)
                .build();

        OpenTelemetrySdk.builder()
                .setMeterProvider(meterProvider)
                .buildAndRegisterGlobal();

        metricsProvider = new OTelMetricsProvider();
        metricsProvider.init();
    }

    @AfterEach
    void tearDown() {
        GlobalOpenTelemetry.resetForTest();
    }

    // -----------------------------------------------------------------------
    // Helper methods
    // -----------------------------------------------------------------------

    private Optional<MetricData> findMetric(String name) {
        return metricReader.collectAllMetrics().stream()
                .filter(m -> m.getName().equals(name))
                .findFirst();
    }

    private long sumMetric(String name) {
        return findMetric(name)
                .map(m -> m.getLongSumData().getPoints().stream()
                        .mapToLong(LongPointData::getValue).sum())
                .orElse(0L);
    }

    // -----------------------------------------------------------------------
    // Artifact created counter
    // -----------------------------------------------------------------------

    @Test
    void testRecordArtifactCreated() {
        metricsProvider.recordArtifactCreated("test-group", "AVRO");

        MetricData metric = findMetric("apicurio.artifacts.created").orElseThrow();
        assertEquals("Total number of artifacts created", metric.getDescription());

        LongPointData point = metric.getLongSumData().getPoints().stream().findFirst().orElseThrow();
        assertEquals(1, point.getValue());
        assertEquals("test-group", point.getAttributes().get(OTelAttributes.ATTR_GROUP_ID));
        assertEquals("AVRO", point.getAttributes().get(OTelAttributes.ATTR_ARTIFACT_TYPE));
    }

    @Test
    void testRecordArtifactCreatedWithNullValues() {
        metricsProvider.recordArtifactCreated(null, null);

        LongPointData point = findMetric("apicurio.artifacts.created").orElseThrow()
                .getLongSumData().getPoints().stream().findFirst().orElseThrow();
        assertEquals("default", point.getAttributes().get(OTelAttributes.ATTR_GROUP_ID));
        assertEquals("unknown", point.getAttributes().get(OTelAttributes.ATTR_ARTIFACT_TYPE));
    }

    // -----------------------------------------------------------------------
    // Artifact deleted counter
    // -----------------------------------------------------------------------

    @Test
    void testRecordArtifactDeleted() {
        metricsProvider.recordArtifactDeleted("my-group", "JSON");

        MetricData metric = findMetric("apicurio.artifacts.deleted").orElseThrow();
        assertEquals("Total number of artifacts deleted", metric.getDescription());

        LongPointData point = metric.getLongSumData().getPoints().stream().findFirst().orElseThrow();
        assertEquals(1, point.getValue());
        assertEquals("my-group", point.getAttributes().get(OTelAttributes.ATTR_GROUP_ID));
        assertEquals("JSON", point.getAttributes().get(OTelAttributes.ATTR_ARTIFACT_TYPE));
    }

    // -----------------------------------------------------------------------
    // Version created counter
    // -----------------------------------------------------------------------

    @Test
    void testRecordVersionCreated() {
        metricsProvider.recordVersionCreated("default", "PROTOBUF");

        MetricData metric = findMetric("apicurio.versions.created").orElseThrow();
        assertEquals("Total number of artifact versions created", metric.getDescription());

        LongPointData point = metric.getLongSumData().getPoints().stream().findFirst().orElseThrow();
        assertEquals(1, point.getValue());
        assertEquals("default", point.getAttributes().get(OTelAttributes.ATTR_GROUP_ID));
        assertEquals("PROTOBUF", point.getAttributes().get(OTelAttributes.ATTR_ARTIFACT_TYPE));
    }

    // -----------------------------------------------------------------------
    // Schema validation counter
    // -----------------------------------------------------------------------

    @Test
    void testRecordSchemaValidationSuccess() {
        metricsProvider.recordSchemaValidation("AVRO", true);

        MetricData metric = findMetric("apicurio.schema.validations").orElseThrow();
        assertEquals("Total number of schema validations performed", metric.getDescription());

        LongPointData point = metric.getLongSumData().getPoints().stream().findFirst().orElseThrow();
        assertEquals(1, point.getValue());
        assertEquals("AVRO", point.getAttributes().get(OTelAttributes.ATTR_ARTIFACT_TYPE));
        assertEquals("success", point.getAttributes().get(OTelAttributes.ATTR_RESULT));
    }

    @Test
    void testRecordSchemaValidationFailure() {
        metricsProvider.recordSchemaValidation("JSON", false);

        LongPointData point = findMetric("apicurio.schema.validations").orElseThrow()
                .getLongSumData().getPoints().stream().findFirst().orElseThrow();
        assertEquals(1, point.getValue());
        assertEquals("JSON", point.getAttributes().get(OTelAttributes.ATTR_ARTIFACT_TYPE));
        assertEquals("failure", point.getAttributes().get(OTelAttributes.ATTR_RESULT));
    }

    @Test
    void testRecordSchemaValidationWithNullType() {
        metricsProvider.recordSchemaValidation(null, true);

        LongPointData point = findMetric("apicurio.schema.validations").orElseThrow()
                .getLongSumData().getPoints().stream().findFirst().orElseThrow();
        assertEquals("unknown", point.getAttributes().get(OTelAttributes.ATTR_ARTIFACT_TYPE));
    }

    // -----------------------------------------------------------------------
    // Rule evaluation counter
    // -----------------------------------------------------------------------

    @Test
    void testRecordRuleEvaluation() {
        metricsProvider.recordRuleEvaluation("CREATE", true);

        MetricData metric = findMetric("apicurio.rules.evaluated").orElseThrow();
        assertEquals("Total number of rule evaluations performed", metric.getDescription());

        LongPointData point = metric.getLongSumData().getPoints().stream().findFirst().orElseThrow();
        assertEquals(1, point.getValue());
        assertEquals("CREATE", point.getAttributes().get(OTelAttributes.ATTR_OPERATION));
        assertEquals("success", point.getAttributes().get(OTelAttributes.ATTR_RESULT));
    }

    @Test
    void testRecordRuleEvaluationFailure() {
        metricsProvider.recordRuleEvaluation("UPDATE", false);

        LongPointData point = findMetric("apicurio.rules.evaluated").orElseThrow()
                .getLongSumData().getPoints().stream().findFirst().orElseThrow();
        assertEquals(1, point.getValue());
        assertEquals("UPDATE", point.getAttributes().get(OTelAttributes.ATTR_OPERATION));
        assertEquals("failure", point.getAttributes().get(OTelAttributes.ATTR_RESULT));
    }

    // -----------------------------------------------------------------------
    // Search request counter
    // -----------------------------------------------------------------------

    @Test
    void testRecordSearchRequest() {
        metricsProvider.recordSearchRequest("artifacts");

        MetricData metric = findMetric("apicurio.search.requests").orElseThrow();
        assertEquals("Total number of search requests", metric.getDescription());

        LongPointData point = metric.getLongSumData().getPoints().stream().findFirst().orElseThrow();
        assertEquals(1, point.getValue());
        assertEquals("artifacts", point.getAttributes().get(OTelAttributes.ATTR_OPERATION));
    }

    @Test
    void testRecordSearchRequestWithNullType() {
        metricsProvider.recordSearchRequest(null);

        LongPointData point = findMetric("apicurio.search.requests").orElseThrow()
                .getLongSumData().getPoints().stream().findFirst().orElseThrow();
        assertEquals("unknown", point.getAttributes().get(OTelAttributes.ATTR_OPERATION));
    }

    // -----------------------------------------------------------------------
    // Iceberg namespace operations counter
    // -----------------------------------------------------------------------

    @Test
    void testRecordIcebergNamespaceOperation() {
        metricsProvider.recordIcebergNamespaceOperation("created");

        MetricData metric = findMetric("apicurio.iceberg.namespace.operations").orElseThrow();
        assertEquals("Iceberg namespace lifecycle operations", metric.getDescription());

        LongPointData point = metric.getLongSumData().getPoints().stream().findFirst().orElseThrow();
        assertEquals(1, point.getValue());
        assertEquals("created", point.getAttributes().get(OTelAttributes.ATTR_OPERATION));
        assertEquals("success", point.getAttributes().get(OTelAttributes.ATTR_RESULT));
    }

    // -----------------------------------------------------------------------
    // Iceberg table operations counter
    // -----------------------------------------------------------------------

    @Test
    void testRecordIcebergTableOperation() {
        metricsProvider.recordIcebergTableOperation("committed");

        MetricData metric = findMetric("apicurio.iceberg.table.operations").orElseThrow();
        assertEquals("Iceberg table lifecycle operations", metric.getDescription());

        LongPointData point = metric.getLongSumData().getPoints().stream().findFirst().orElseThrow();
        assertEquals(1, point.getValue());
        assertEquals("committed", point.getAttributes().get(OTelAttributes.ATTR_OPERATION));
        assertEquals("success", point.getAttributes().get(OTelAttributes.ATTR_RESULT));
    }

    // -----------------------------------------------------------------------
    // Iceberg view operations counter
    // -----------------------------------------------------------------------

    @Test
    void testRecordIcebergViewOperation() {
        metricsProvider.recordIcebergViewOperation("replaced");

        MetricData metric = findMetric("apicurio.iceberg.view.operations").orElseThrow();
        assertEquals("Iceberg view lifecycle operations", metric.getDescription());

        LongPointData point = metric.getLongSumData().getPoints().stream().findFirst().orElseThrow();
        assertEquals(1, point.getValue());
        assertEquals("replaced", point.getAttributes().get(OTelAttributes.ATTR_OPERATION));
        assertEquals("success", point.getAttributes().get(OTelAttributes.ATTR_RESULT));
    }

    // -----------------------------------------------------------------------
    // Iceberg commit conflicts counter
    // -----------------------------------------------------------------------

    @Test
    void testRecordIcebergCommitConflict() {
        metricsProvider.recordIcebergCommitConflict("table");

        MetricData metric = findMetric("apicurio.iceberg.commit.conflicts").orElseThrow();
        assertEquals("Iceberg commit conflicts due to optimistic concurrency",
                metric.getDescription());

        LongPointData point = metric.getLongSumData().getPoints().stream().findFirst().orElseThrow();
        assertEquals(1, point.getValue());
        assertEquals("table", point.getAttributes().get(OTelAttributes.ATTR_ENTITY_TYPE));
    }

    // -----------------------------------------------------------------------
    // Iceberg errors counter
    // -----------------------------------------------------------------------

    @Test
    void testRecordIcebergError() {
        metricsProvider.recordIcebergError("NoSuchNamespaceException");

        MetricData metric = findMetric("apicurio.iceberg.errors").orElseThrow();
        assertEquals("Iceberg-specific errors by type", metric.getDescription());

        LongPointData point = metric.getLongSumData().getPoints().stream().findFirst().orElseThrow();
        assertEquals(1, point.getValue());
        assertEquals("NoSuchNamespaceException",
                point.getAttributes().get(OTelAttributes.ATTR_ERROR_TYPE));
    }

    // -----------------------------------------------------------------------
    // Usage summary gauges (UpDownCounters)
    // -----------------------------------------------------------------------

    @Test
    void testUpdateUsageSummaryCounts() {
        metricsProvider.updateUsageSummaryCounts(10, 5, 2);

        assertEquals(10, sumMetric("apicurio.usage.schemas.active"));
        assertEquals(5, sumMetric("apicurio.usage.schemas.stale"));
        assertEquals(2, sumMetric("apicurio.usage.schemas.dead"));
    }

    @Test
    void testUpdateUsageSummaryCountsAdjustsDeltas() {
        // First update sets the baseline
        metricsProvider.updateUsageSummaryCounts(10, 5, 2);

        // Second update should record only the delta
        metricsProvider.updateUsageSummaryCounts(15, 3, 4);

        assertEquals(15, sumMetric("apicurio.usage.schemas.active"));
        assertEquals(3, sumMetric("apicurio.usage.schemas.stale"));
        assertEquals(4, sumMetric("apicurio.usage.schemas.dead"));
    }

    @Test
    void testUpdateUsageSummaryCountsDecrease() {
        metricsProvider.updateUsageSummaryCounts(20, 10, 5);
        metricsProvider.updateUsageSummaryCounts(8, 3, 1);

        assertEquals(8, sumMetric("apicurio.usage.schemas.active"));
        assertEquals(3, sumMetric("apicurio.usage.schemas.stale"));
        assertEquals(1, sumMetric("apicurio.usage.schemas.dead"));
    }

    // -----------------------------------------------------------------------
    // Multi-increment with exact per-attribute counts
    // -----------------------------------------------------------------------

    @Test
    void testMultipleArtifactCreationsExactCounts() {
        metricsProvider.recordArtifactCreated("group1", "AVRO");
        metricsProvider.recordArtifactCreated("group1", "AVRO");
        metricsProvider.recordArtifactCreated("group2", "JSON");

        MetricData metric = findMetric("apicurio.artifacts.created").orElseThrow();
        // Two distinct attribute sets: (group1,AVRO) and (group2,JSON)
        assertEquals(2, metric.getLongSumData().getPoints().size());

        for (LongPointData point : metric.getLongSumData().getPoints()) {
            String groupId = point.getAttributes().get(OTelAttributes.ATTR_GROUP_ID);
            if ("group1".equals(groupId)) {
                assertEquals(2, point.getValue(),
                        "group1/AVRO should have exactly 2 increments");
            } else {
                assertEquals(1, point.getValue(),
                        "group2/JSON should have exactly 1 increment");
            }
        }

        // Total across all attribute combinations must be 3
        assertEquals(3, sumMetric("apicurio.artifacts.created"));
    }

    @Test
    void testMultipleSearchTypesExactCounts() {
        metricsProvider.recordSearchRequest("artifacts");
        metricsProvider.recordSearchRequest("artifacts");
        metricsProvider.recordSearchRequest("versions");
        metricsProvider.recordSearchRequest("groups");
        metricsProvider.recordSearchRequest("groups");
        metricsProvider.recordSearchRequest("groups");

        assertEquals(6, sumMetric("apicurio.search.requests"));

        MetricData metric = findMetric("apicurio.search.requests").orElseThrow();
        assertEquals(3, metric.getLongSumData().getPoints().size(),
                "Should have 3 distinct search types");

        for (LongPointData point : metric.getLongSumData().getPoints()) {
            String operation = point.getAttributes().get(OTelAttributes.ATTR_OPERATION);
            switch (operation) {
                case "artifacts":
                    assertEquals(2, point.getValue());
                    break;
                case "versions":
                    assertEquals(1, point.getValue());
                    break;
                case "groups":
                    assertEquals(3, point.getValue());
                    break;
                default:
                    throw new AssertionError("Unexpected search type: " + operation);
            }
        }
    }

    @Test
    void testMultipleIcebergTableOperationsExactCounts() {
        metricsProvider.recordIcebergTableOperation("created");
        metricsProvider.recordIcebergTableOperation("deleted");
        metricsProvider.recordIcebergTableOperation("committed");
        metricsProvider.recordIcebergTableOperation("committed");

        assertEquals(4, sumMetric("apicurio.iceberg.table.operations"));

        MetricData metric = findMetric("apicurio.iceberg.table.operations").orElseThrow();
        for (LongPointData point : metric.getLongSumData().getPoints()) {
            String operation = point.getAttributes().get(OTelAttributes.ATTR_OPERATION);
            if ("committed".equals(operation)) {
                assertEquals(2, point.getValue(),
                        "committed operations should be exactly 2");
            } else {
                assertEquals(1, point.getValue(),
                        operation + " should be exactly 1");
            }
        }
    }

    // -----------------------------------------------------------------------
    // Negative cases: operations that should NOT trigger other counters
    // -----------------------------------------------------------------------

    @Test
    void testArtifactCreatedDoesNotIncrementOtherCounters() {
        metricsProvider.recordArtifactCreated("g", "AVRO");

        assertEquals(1, sumMetric("apicurio.artifacts.created"));
        assertEquals(0, sumMetric("apicurio.artifacts.deleted"),
                "artifacts.deleted must not be incremented by recordArtifactCreated");
        assertEquals(0, sumMetric("apicurio.versions.created"),
                "versions.created must not be incremented by recordArtifactCreated");
        assertEquals(0, sumMetric("apicurio.schema.validations"),
                "schema.validations must not be incremented by recordArtifactCreated");
        assertEquals(0, sumMetric("apicurio.search.requests"),
                "search.requests must not be incremented by recordArtifactCreated");
    }

    @Test
    void testSearchDoesNotIncrementArtifactOrVersionCounters() {
        metricsProvider.recordSearchRequest("artifacts");
        metricsProvider.recordSearchRequest("versions");

        assertEquals(2, sumMetric("apicurio.search.requests"));
        assertEquals(0, sumMetric("apicurio.artifacts.created"));
        assertEquals(0, sumMetric("apicurio.artifacts.deleted"));
        assertEquals(0, sumMetric("apicurio.versions.created"));
    }

    @Test
    void testIcebergCountersAreIsolated() {
        metricsProvider.recordIcebergNamespaceOperation("created");
        metricsProvider.recordIcebergTableOperation("created");

        assertEquals(1, sumMetric("apicurio.iceberg.namespace.operations"));
        assertEquals(1, sumMetric("apicurio.iceberg.table.operations"));
        assertEquals(0, sumMetric("apicurio.iceberg.view.operations"));
        assertEquals(0, sumMetric("apicurio.iceberg.commit.conflicts"));
        assertEquals(0, sumMetric("apicurio.iceberg.errors"));

        // Core registry counters must remain at zero
        assertEquals(0, sumMetric("apicurio.artifacts.created"));
        assertEquals(0, sumMetric("apicurio.search.requests"));
    }

    @Test
    void testSchemaValidationDoesNotIncrementRuleEvaluation() {
        metricsProvider.recordSchemaValidation("AVRO", true);

        assertEquals(1, sumMetric("apicurio.schema.validations"));
        assertEquals(0, sumMetric("apicurio.rules.evaluated"));
    }

    @Test
    void testRuleEvaluationDoesNotIncrementSchemaValidation() {
        metricsProvider.recordRuleEvaluation("CREATE", true);

        assertEquals(1, sumMetric("apicurio.rules.evaluated"));
        assertEquals(0, sumMetric("apicurio.schema.validations"));
    }

    // -----------------------------------------------------------------------
    // Mixed operations with exact total verification
    // -----------------------------------------------------------------------

    @Test
    void testMixedOperationsExactTotals() {
        // Simulate a realistic workflow: create 2 artifacts (each with a version),
        // run 3 validations (2 pass, 1 fail), search twice, then delete 1 artifact
        metricsProvider.recordArtifactCreated("g1", "AVRO");
        metricsProvider.recordVersionCreated("g1", "AVRO");
        metricsProvider.recordArtifactCreated("g1", "JSON");
        metricsProvider.recordVersionCreated("g1", "JSON");

        metricsProvider.recordSchemaValidation("AVRO", true);
        metricsProvider.recordSchemaValidation("AVRO", true);
        metricsProvider.recordSchemaValidation("JSON", false);

        metricsProvider.recordSearchRequest("artifacts");
        metricsProvider.recordSearchRequest("versions");

        metricsProvider.recordArtifactDeleted("g1", "JSON");

        assertEquals(2, sumMetric("apicurio.artifacts.created"));
        assertEquals(2, sumMetric("apicurio.versions.created"));
        assertEquals(3, sumMetric("apicurio.schema.validations"));
        assertEquals(2, sumMetric("apicurio.search.requests"));
        assertEquals(1, sumMetric("apicurio.artifacts.deleted"));
        assertEquals(0, sumMetric("apicurio.rules.evaluated"));
    }
}
