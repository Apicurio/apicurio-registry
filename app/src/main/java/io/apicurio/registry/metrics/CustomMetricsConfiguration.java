package io.apicurio.registry.metrics;

import io.apicurio.common.apps.config.Info;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_OBSERVABILITY;

@Singleton
public class CustomMetricsConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CustomMetricsConfiguration.class);

    // Micrometer Timer records values in nanoseconds.
    private static final double SECONDS_TO_NANOSECONDS = 1_000_000_000.0;

    @Info(description = """
                    The distribution type for REST API request metrics. \
                    Use `histogram` (default) for explicit bucket histogram with SLO boundaries \
                    and pre-computed percentiles. \
                    Use `summary` for pre-computed percentiles only, without histogram buckets. \
                    Datadog users should use `summary` for accurate latency percentiles, \
                    since Datadog re-computes percentiles from histogram buckets \
                    and the default bucket boundaries may not provide sufficient granularity.
            """, category = CATEGORY_OBSERVABILITY, availableSince = "3.3.1")
    @ConfigProperty(name = "apicurio.metrics.rest.distribution.type", defaultValue = "histogram")
    String distributionType;

    @Info(description = """
                    Comma-separated list of percentile values to pre-compute \
                    for REST API request metrics. \
                    Each value must be between 0.0 and 1.0 (e.g. 0.5 for the median, \
                    0.95 for the 95th percentile). \
                    These percentiles are computed client-side and exported as individual gauge metrics.
            """, category = CATEGORY_OBSERVABILITY, availableSince = "3.3.1")
    @ConfigProperty(name = "apicurio.metrics.rest.distribution.percentiles",
            defaultValue = "0.5,0.95,0.99,0.9995")
    List<Double> percentiles;

    @Info(description = """
                    Comma-separated list of service level objective (SLO) boundary values \
                    in seconds for REST API request histogram buckets. \
                    Only used when the distribution type is `histogram`. \
                    Each value defines a histogram bucket boundary. \
                    Adjust these boundaries to match your monitoring system's expectations \
                    for accurate percentile computation from histogram data.
            """, category = CATEGORY_OBSERVABILITY, availableSince = "3.3.1")
    @ConfigProperty(name = "apicurio.metrics.rest.distribution.slo",
            defaultValue = "0.1,0.25,0.5,1.0,2.0,5.0,10.0")
    List<Double> slo;

    /**
     * Produces a MeterFilter that configures the distribution statistics for REST request metrics.
     * The returned filter captures immutable snapshots of the config values, making it thread-safe.
     */
    @Produces
    @Singleton
    public MeterFilter configureDistribution() {
        String type = distributionType.toLowerCase(Locale.ROOT);
        if (!"histogram".equals(type) && !"summary".equals(type)) {
            throw new IllegalArgumentException(
                    "Invalid value for 'apicurio.metrics.rest.distribution.type': '"
                            + distributionType + "'. Valid values are: histogram, summary");
        }
        percentiles.stream().filter(p -> p < 0.0 || p > 1.0).findFirst().ifPresent(p -> {
            throw new IllegalArgumentException(
                    "Invalid percentile value " + p
                            + " in 'apicurio.metrics.rest.distribution.percentiles'. "
                            + "Each value must be between 0.0 and 1.0.");
        });
        slo.stream().filter(s -> s <= 0.0).findFirst().ifPresent(s -> {
            throw new IllegalArgumentException(
                    "Invalid SLO boundary value " + s
                            + " in 'apicurio.metrics.rest.distribution.slo'. "
                            + "Each value must be a positive number representing seconds.");
        });

        double[] percentilesArray = toDoubleArray(percentiles);
        double[] sloNanosArray = toSloNanosArray(slo);

        log.debug("REST metrics distribution type: {}, percentiles: {}, SLO boundaries (seconds): {}",
                type, percentiles, slo);

        return new MeterFilter() {
            @Override
            public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
                if (id.getName().startsWith(MetricsConstants.REST_REQUESTS)) {
                    DistributionStatisticConfig.Builder builder = DistributionStatisticConfig.builder()
                            .percentiles(percentilesArray);
                    if ("histogram".equals(type)) {
                        builder.serviceLevelObjectives(sloNanosArray);
                    }
                    return builder.build().merge(config);
                }
                return config;
            }
        };
    }

    private static double[] toDoubleArray(List<Double> values) {
        if (values.isEmpty()) {
            return new double[0];
        }
        return values.stream().mapToDouble(Double::doubleValue).toArray();
    }

    private static double[] toSloNanosArray(List<Double> sloSeconds) {
        if (sloSeconds.isEmpty()) {
            return new double[0];
        }
        return sloSeconds.stream()
                .mapToDouble(s -> s * SECONDS_TO_NANOSECONDS)
                .toArray();
    }
}
