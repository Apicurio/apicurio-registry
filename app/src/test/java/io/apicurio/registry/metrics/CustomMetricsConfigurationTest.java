package io.apicurio.registry.metrics;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link CustomMetricsConfiguration}.
 */
class CustomMetricsConfigurationTest {

    private static final double SECONDS_TO_NANOSECONDS = 1_000_000_000.0;
    private static final double DELTA = 0.001;

    @Test
    void testDefaultHistogramMode() {
        CustomMetricsConfiguration config = createConfig(
                "histogram",
                List.of(0.5, 0.95, 0.99, 0.9995),
                List.of(0.1, 0.25, 0.5, 1.0, 2.0, 5.0, 10.0)
        );

        MeterFilter filter = config.configureDistribution();
        DistributionStatisticConfig result = applyFilter(filter, MetricsConstants.REST_REQUESTS);

        assertNotNull(result.getPercentiles(), "Percentiles should be set in histogram mode");
        assertArrayEquals(
                new double[]{0.5, 0.95, 0.99, 0.9995},
                result.getPercentiles(),
                DELTA
        );

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
        assertArrayEquals(expectedSlos, result.getServiceLevelObjectiveBoundaries(), DELTA);
    }

    @Test
    void testSummaryMode() {
        CustomMetricsConfiguration config = createConfig(
                "summary",
                List.of(0.5, 0.95, 0.99),
                List.of(0.1, 0.25, 0.5, 1.0, 2.0, 5.0, 10.0)
        );

        MeterFilter filter = config.configureDistribution();
        DistributionStatisticConfig result = applyFilter(filter, MetricsConstants.REST_REQUESTS);

        assertNotNull(result.getPercentiles(), "Percentiles should be set in summary mode");
        assertArrayEquals(new double[]{0.5, 0.95, 0.99}, result.getPercentiles(), DELTA);

        assertNull(result.getServiceLevelObjectiveBoundaries(),
                "SLO boundaries should not be set in summary mode");
    }

    @Test
    void testCustomBucketBoundaries() {
        List<Double> customSlo = List.of(0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1.0);
        CustomMetricsConfiguration config = createConfig(
                "histogram",
                List.of(0.5, 0.99),
                customSlo
        );

        MeterFilter filter = config.configureDistribution();
        DistributionStatisticConfig result = applyFilter(filter, MetricsConstants.REST_REQUESTS);

        assertArrayEquals(new double[]{0.5, 0.99}, result.getPercentiles(), DELTA);

        double[] expectedSlos = customSlo.stream()
                .mapToDouble(s -> s * SECONDS_TO_NANOSECONDS)
                .toArray();
        assertArrayEquals(expectedSlos, result.getServiceLevelObjectiveBoundaries(), DELTA);
    }

    @Test
    void testNonRestMetricsUnaffected() {
        CustomMetricsConfiguration config = createConfig(
                "histogram",
                List.of(0.5, 0.95, 0.99, 0.9995),
                List.of(0.1, 0.25, 0.5, 1.0, 2.0, 5.0, 10.0)
        );

        MeterFilter filter = config.configureDistribution();
        DistributionStatisticConfig result = applyFilter(filter, "some.other.metric");

        assertNull(result.getPercentiles(), "Non-REST metrics should not have percentiles set");
        assertNull(result.getServiceLevelObjectiveBoundaries(),
                "Non-REST metrics should not have SLO boundaries set");
    }

    @Test
    void testDistributionTypeCaseInsensitive() {
        CustomMetricsConfiguration config = createConfig(
                "SUMMARY",
                List.of(0.5, 0.95),
                List.of(0.1)
        );

        MeterFilter filter = config.configureDistribution();
        DistributionStatisticConfig result = applyFilter(filter, MetricsConstants.REST_REQUESTS);

        assertNull(result.getServiceLevelObjectiveBoundaries(),
                "Summary mode should work regardless of case");
    }

    @Test
    void testUnknownTypeTreatedAsSummary() {
        CustomMetricsConfiguration config = createConfig(
                "foobar",
                List.of(0.5, 0.95),
                List.of(0.1)
        );

        MeterFilter filter = config.configureDistribution();
        DistributionStatisticConfig result = applyFilter(filter, MetricsConstants.REST_REQUESTS);

        assertNotNull(result.getPercentiles(), "Percentiles should still be set for unknown type");
        assertNull(result.getServiceLevelObjectiveBoundaries(),
                "Unknown type should not produce SLO boundaries (only histogram does)");
    }

    @Test
    void testEmptyPercentiles() {
        CustomMetricsConfiguration config = createConfig(
                "histogram",
                List.of(),
                List.of(0.1, 0.5, 1.0)
        );

        MeterFilter filter = config.configureDistribution();
        DistributionStatisticConfig result = applyFilter(filter, MetricsConstants.REST_REQUESTS);

        assertEquals(0, result.getPercentiles().length, "Empty percentiles should produce empty array");
        assertNotNull(result.getServiceLevelObjectiveBoundaries(),
                "SLO boundaries should still be set");
    }

    @Test
    void testEmptySlo() {
        CustomMetricsConfiguration config = createConfig(
                "histogram",
                List.of(0.5, 0.95),
                List.of()
        );

        MeterFilter filter = config.configureDistribution();
        DistributionStatisticConfig result = applyFilter(filter, MetricsConstants.REST_REQUESTS);

        assertNotNull(result.getPercentiles(), "Percentiles should still be set");
        assertEquals(0, result.getServiceLevelObjectiveBoundaries().length,
                "Empty SLO should produce empty array");
    }

    private CustomMetricsConfiguration createConfig(String distributionType,
            List<Double> percentiles, List<Double> slo) {
        CustomMetricsConfiguration config = new CustomMetricsConfiguration();
        config.distributionType = distributionType;
        config.percentiles = percentiles;
        config.slo = slo;
        return config;
    }

    private DistributionStatisticConfig applyFilter(MeterFilter filter, String metricName) {
        Meter.Id id = new Meter.Id(metricName, Tags.empty(), null, null, Meter.Type.TIMER);
        return filter.configure(id, DistributionStatisticConfig.NONE);
    }
}
