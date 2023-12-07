package io.apicurio.registry.metrics;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

@Singleton
public class CustomMetricsConfiguration {

    @Produces
    @Singleton
    public MeterFilter enableHistogram() {
        double factor = 1000000000; //to convert slos to seconds
        return new MeterFilter() {
            @Override
            public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
                if(id.getName().startsWith(MetricsConstants.REST_REQUESTS)) {
                    return DistributionStatisticConfig.builder()
                        .percentiles(0.5, 0.95, 0.99, 0.9995)
                        .serviceLevelObjectives(0.1 * factor, 0.25 * factor, 0.5 * factor, 1.0 * factor, 2.0 * factor, 5.0 * factor, 10.0 * factor)
                        .build()
                        .merge(config);
                }
                return config;
            }
        };
    }

}
