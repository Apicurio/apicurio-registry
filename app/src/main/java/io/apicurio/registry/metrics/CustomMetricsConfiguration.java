/*
 * Copyright 2021 Red Hat
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

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;

import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

/**
 * @author Fabian Martinez
 */
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
