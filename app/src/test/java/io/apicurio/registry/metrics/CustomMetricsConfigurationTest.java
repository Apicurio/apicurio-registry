package io.apicurio.registry.metrics;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link CustomMetricsConfiguration}.
 * Tests verify that the MeterFilter is correctly configured based on properties.
 */
class CustomMetricsConfigurationTest {

    private static final double SECONDS_TO_NANOSECONDS = 1_000_000_000.0;
    private static final double DELTA = 0.001;

    @Test
    void testDefaultHistogramMode() throws Exception {
        CustomMetricsConfiguration config = createConfig(
                "histogram",
                List.of(0.5, 0.95, 0.99, 0.9995),
                List.of(0.1, 0.25, 0.5, 1.0, 2.0, 5.0, 10.0)
        );

        MeterFilter filter = config.enableHistogram();
        DistributionStatisticConfig result = applyFilter(filter, MetricsConstants.REST_REQUESTS);

        // Verify percentiles are set
        assertNotNull(result.getPercentiles(), "Percentiles should be set in histogram mode");
        assertArrayEquals(
                new double[]{0.5, 0.95, 0.99, 0.9995},
                result.getPercentiles(),
                DELTA,
                "Default percentiles should match"
        );

        // Verify SLO boundaries are set (converted to nanoseconds)
        assertNotNull(result.getServiceLevelObjectiveBoundaries(),
                "SLO boundaries should be set in histogram mode");
        double[] expectedSlos = {
                0.1 * SECONDS_TO_NANOSECONDS,
                0.25 * SECONDS_TO_NANOSECONDS,
                0.5 * SECONDS_TO_NANOSECONDS,
                1.0 * SECONDS_TO_NANOSECONDS,
                2.0 * SECONDS_TO_NANOSECONDS,
                5.0 * SECONDS_TO_NANOSECONDS,
                10.0 * SECONDS_TO_NANOSECONDS
        };
        assertArrayEquals(expectedSlos, result.getServiceLevelObjectiveBoundaries(), DELTA,
                "Default SLO boundaries should match (in nanoseconds)");
    }

    @Test
    void testSummaryMode() throws Exception {
        CustomMetricsConfiguration config = createConfig(
                "summary",
                List.of(0.5, 0.95, 0.99),
                List.of(0.1, 0.25, 0.5, 1.0, 2.0, 5.0, 10.0)
        );

        MeterFilter filter = config.enableHistogram();
        DistributionStatisticConfig result = applyFilter(filter, MetricsConstants.REST_REQUESTS);

        // Verify percentiles are set
        assertNotNull(result.getPercentiles(), "Percentiles should be set in summary mode");
        assertArrayEquals(
                new double[]{0.5, 0.95, 0.99},
                result.getPercentiles(),
                DELTA,
                "Percentiles should match configured values"
        );

        // Verify SLO boundaries are NOT set in summary mode
        assertNull(result.getServiceLevelObjectiveBoundaries(),
                "SLO boundaries should not be set in summary mode");
    }

    @Test
    void testCustomBucketBoundaries() throws Exception {
        List<Double> customSlo = List.of(0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1.0);
        CustomMetricsConfiguration config = createConfig(
                "histogram",
                List.of(0.5, 0.99),
                customSlo
        );

        MeterFilter filter = config.enableHistogram();
        DistributionStatisticConfig result = applyFilter(filter, MetricsConstants.REST_REQUESTS);

        // Verify custom percentiles
        assertArrayEquals(
                new double[]{0.5, 0.99},
                result.getPercentiles(),
                DELTA,
                "Custom percentiles should be applied"
        );

        // Verify custom SLO boundaries
        double[] expectedSlos = customSlo.stream()
                .mapToDouble(s -> s * SECONDS_TO_NANOSECONDS)
                .toArray();
        assertArrayEquals(expectedSlos, result.getServiceLevelObjectiveBoundaries(), DELTA,
                "Custom SLO boundaries should be applied (in nanoseconds)");
    }

    @Test
    void testNonRestMetricsUnaffected() throws Exception {
        CustomMetricsConfiguration config = createConfig(
                "histogram",
                List.of(0.5, 0.95, 0.99, 0.9995),
                List.of(0.1, 0.25, 0.5, 1.0, 2.0, 5.0, 10.0)
        );

        MeterFilter filter = config.enableHistogram();
        DistributionStatisticConfig result = applyFilter(filter, "some.other.metric");

        // Non-REST metrics should get the default empty config back
        assertNull(result.getPercentiles(), "Non-REST metrics should not have percentiles set");
        assertNull(result.getServiceLevelObjectiveBoundaries(),
                "Non-REST metrics should not have SLO boundaries set");
    }

    @Test
    void testDistributionTypeCaseInsensitive() throws Exception {
        CustomMetricsConfiguration config = createConfig(
                "SUMMARY",
                List.of(0.5, 0.95),
                List.of(0.1)
        );

        MeterFilter filter = config.enableHistogram();
        DistributionStatisticConfig result = applyFilter(filter, MetricsConstants.REST_REQUESTS);

        // Should treat "SUMMARY" same as "summary"
        assertNull(result.getServiceLevelObjectiveBoundaries(),
                "Summary mode should work regardless of case");
    }

    private CustomMetricsConfiguration createConfig(String distributionType,
            List<Double> percentiles, List<Double> slo) throws Exception {
        CustomMetricsConfiguration config = new CustomMetricsConfiguration();
        setField(config, "distributionType", distributionType);
        setField(config, "percentiles", percentiles);
        setField(config, "slo", slo);
        return config;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private DistributionStatisticConfig applyFilter(MeterFilter filter, String metricName) {
        Meter.Id id = new Meter.Id(metricName, io.micrometer.core.instrument.Tags.empty(),
                null, null, Meter.Type.TIMER);
        return filter.configure(id, DistributionStatisticConfig.NONE);
    }
}
