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

package io.apicurio.multitenant.metrics;

import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Meter.Id;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;

/**
 * @author Fabian Martinez
 */
@Singleton
public class CustomMetricsConfiguration {

    private static final String REQUESTS_TIMER_METRIC = "http.server.requests";

    @Produces
    @Singleton
    public MeterFilter enableHistogram() {
        double factor = 1000000000; //to convert slos to seconds
        return new MeterFilter() {

            @Override
            public Id map(Id id) {
                if(id.getName().startsWith(REQUESTS_TIMER_METRIC) && isTenantManagerApiCall(id)) {
                    return id.withTag(Tag.of("api", "tenant-manager"));
                }
                return id;
            }

            @Override
            public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
                if(id.getName().startsWith(REQUESTS_TIMER_METRIC) && isTenantManagerApiCall(id)) {
                    return DistributionStatisticConfig.builder()
                        .percentiles(0.5, 0.95, 0.99)
                        .serviceLevelObjectives(0.1 * factor, 1.0 * factor, 2.0 * factor, 5.0 * factor, 10.0 * factor, 30.0 * factor)
                        .build()
                        .merge(config);
                }
                return config;
            }
        };
    }

    private boolean isTenantManagerApiCall(Meter.Id id) {
        String uri = id.getTag("uri");
        return uri != null && uri.startsWith("/api");
    }

}
