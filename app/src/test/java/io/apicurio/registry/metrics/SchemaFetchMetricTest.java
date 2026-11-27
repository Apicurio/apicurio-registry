package io.apicurio.registry.metrics;

import io.apicurio.registry.observability.OTelAttributes;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests of the schema fetch counter: exact attributes, finite operation vocabulary, and the series cap
 * that {@code UsageMetricsCustomizer} registers.
 */
class SchemaFetchMetricTest {

    private static final String METRIC = "apicurio.usage.schema.fetches";
    private static final AttributeKey<Boolean> OVERFLOW = AttributeKey.booleanKey("otel.metric.overflow");

    private InMemoryMetricReader reader;
    private OTelMetricsProvider provider;
    private Locale defaultLocale;

    @BeforeEach
    void setUp() {
        defaultLocale = Locale.getDefault();
        GlobalOpenTelemetry.resetForTest();
    }

    @AfterEach
    void tearDown() {
        Locale.setDefault(defaultLocale);
        GlobalOpenTelemetry.resetForTest();
    }

    private void startSdk(int maxSeries) {
        reader = InMemoryMetricReader.create();
        SdkMeterProviderBuilder meterProvider = SdkMeterProvider.builder().registerMetricReader(reader);
        if (maxSeries > 0) {
            UsageMetricsCustomizer.registerCardinalityView(meterProvider, maxSeries);
        }
        OpenTelemetrySdk.builder().setMeterProvider(meterProvider.build()).buildAndRegisterGlobal();
        provider = new OTelMetricsProvider();
        provider.init();
    }

    private List<LongPointData> points() {
        return reader.collectAllMetrics().stream()
                .filter(m -> m.getName().equals(METRIC))
                .flatMap(m -> m.getLongSumData().getPoints().stream())
                .collect(Collectors.toList());
    }

    private static Map<String, String> labels(LongPointData point) {
        Attributes a = point.getAttributes();
        return Map.of(
                "client", a.get(OTelAttributes.ATTR_CLIENT_ID),
                "type", a.get(OTelAttributes.ATTR_IDENTIFIER_TYPE),
                "value", a.get(OTelAttributes.ATTR_IDENTIFIER_VALUE),
                "operation", a.get(OTelAttributes.ATTR_OPERATION));
    }

    @Test
    void recordsExactAttributesAndValue() {
        startSdk(0);
        provider.recordSchemaFetch("payments-svc", MetricsConstants.USAGE_IDENTIFIER_TYPE_GLOBAL_ID, 42L,
                "SERIALIZE");
        provider.recordSchemaFetch("payments-svc", MetricsConstants.USAGE_IDENTIFIER_TYPE_GLOBAL_ID, 42L,
                "SERIALIZE");

        List<LongPointData> points = points();
        assertEquals(1, points.size());
        assertEquals(2L, points.get(0).getValue());
        assertEquals(Map.of("client", "payments-svc", "type", "global_id", "value", "42", "operation",
                "SERIALIZE"), labels(points.get(0)));
    }

    @Test
    void contentIdsAndGlobalIdsWithTheSameValueAreDifferentSeries() {
        startSdk(0);
        provider.recordSchemaFetch("c", MetricsConstants.USAGE_IDENTIFIER_TYPE_GLOBAL_ID, 7L, "SERIALIZE");
        provider.recordSchemaFetch("c", MetricsConstants.USAGE_IDENTIFIER_TYPE_CONTENT_ID, 7L, "SERIALIZE");

        List<LongPointData> points = points();
        assertEquals(2, points.size());
        assertEquals(1L, points.get(0).getValue());
        assertEquals(1L, points.get(1).getValue());
    }

    @Test
    void operationIsNormalizedToAFiniteVocabulary() {
        startSdk(0);
        String[] raw = { "serialize", "Deserialize", "DESERIALIZE", null, "", "DROP TABLE", "x".repeat(500) };
        for (String operation : raw) {
            provider.recordSchemaFetch("c", MetricsConstants.USAGE_IDENTIFIER_TYPE_GLOBAL_ID, 1L, operation);
        }

        Map<String, Long> byOperation = points().stream().collect(Collectors.toMap(
                p -> p.getAttributes().get(OTelAttributes.ATTR_OPERATION), LongPointData::getValue));
        assertEquals(Map.of("SERIALIZE", 1L, "DESERIALIZE", 2L, "UNKNOWN", 4L), byOperation);
    }

    @Test
    void operationNormalizationDoesNotDependOnTheDefaultLocale() {
        startSdk(0);
        // In a Turkish locale "serialize".toUpperCase() contains a dotted capital I and would not match.
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        provider.recordSchemaFetch("c", MetricsConstants.USAGE_IDENTIFIER_TYPE_GLOBAL_ID, 1L, "serialize");

        assertEquals("SERIALIZE", points().get(0).getAttributes().get(OTelAttributes.ATTR_OPERATION));
    }

    @Test
    void overflowKeepsTheTotalAndReplacesDetailWithTheOverflowSeries() {
        int cap = 5;
        int fetches = 12;
        startSdk(cap);
        for (int i = 1; i <= fetches; i++) {
            provider.recordSchemaFetch("client-" + i, MetricsConstants.USAGE_IDENTIFIER_TYPE_GLOBAL_ID, i,
                    "SERIALIZE");
        }

        List<LongPointData> points = points();
        List<LongPointData> overflow = points.stream()
                .filter(p -> Boolean.TRUE.equals(p.getAttributes().get(OVERFLOW))).collect(Collectors.toList());
        List<LongPointData> detailed = points.stream()
                .filter(p -> !Boolean.TRUE.equals(p.getAttributes().get(OVERFLOW))).collect(Collectors.toList());

        // Sequential recording only: the SDK checks the limit and adds a series in two unlocked steps, so
        // concurrent first-seen combinations can exceed it (see the documentation of max-series).
        assertEquals(cap, points.size(), "when recorded one at a time the cap counts the overflow series too");
        assertEquals(1, overflow.size());
        assertEquals(cap - 1, detailed.size());
        assertTrue(detailed.stream().allMatch(p -> p.getValue() == 1L));
        assertEquals(fetches - (cap - 1), overflow.get(0).getValue(),
                "everything past the detailed series lands in the overflow series");
        assertEquals(fetches, points.stream().mapToLong(LongPointData::getValue).sum(),
                "the total over all series is exact");
        assertEquals(Attributes.of(OVERFLOW, true), overflow.get(0).getAttributes(),
                "the overflow series carries no client or identifier labels");
    }

    @Test
    void existingSeriesKeepCountingAfterTheCapIsReached() {
        startSdk(3);
        provider.recordSchemaFetch("a", MetricsConstants.USAGE_IDENTIFIER_TYPE_GLOBAL_ID, 1L, "SERIALIZE");
        provider.recordSchemaFetch("b", MetricsConstants.USAGE_IDENTIFIER_TYPE_GLOBAL_ID, 2L, "SERIALIZE");
        provider.recordSchemaFetch("c", MetricsConstants.USAGE_IDENTIFIER_TYPE_GLOBAL_ID, 3L, "SERIALIZE");
        provider.recordSchemaFetch("a", MetricsConstants.USAGE_IDENTIFIER_TYPE_GLOBAL_ID, 1L, "SERIALIZE");

        Map<String, Long> byClient = points().stream()
                .filter(p -> p.getAttributes().get(OTelAttributes.ATTR_CLIENT_ID) != null)
                .collect(Collectors.toMap(p -> p.getAttributes().get(OTelAttributes.ATTR_CLIENT_ID),
                        LongPointData::getValue));
        assertEquals(Map.of("a", 2L, "b", 1L), byClient);
        assertEquals(1L, points().stream().filter(p -> Boolean.TRUE.equals(p.getAttributes().get(OVERFLOW)))
                .mapToLong(LongPointData::getValue).sum(), "client c was the first one past the cap");
    }

    @Test
    void capOnlyAppliesToTheSchemaFetchCounter() {
        startSdk(2);
        for (int i = 1; i <= 6; i++) {
            provider.recordArtifactCreated("group-" + i, "AVRO");
        }

        MetricData created = reader.collectAllMetrics().stream()
                .filter(m -> m.getName().equals("apicurio.artifacts.created")).findFirst().orElseThrow();
        assertEquals(6, created.getLongSumData().getPoints().size());
    }

    @Test
    void withoutTheViewTheSdkDefaultLimitApplies() {
        startSdk(0);
        for (int i = 1; i <= 2500; i++) {
            provider.recordSchemaFetch("client-" + i, MetricsConstants.USAGE_IDENTIFIER_TYPE_GLOBAL_ID, i,
                    "SERIALIZE");
        }
        // The SDK itself bounds the instrument at 2000 by default, which is why the configured default is 2000.
        assertEquals(2000, points().size());
    }
}
