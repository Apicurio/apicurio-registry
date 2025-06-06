package io.apicurio.registry.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import static io.apicurio.registry.metrics.MetricsConstants.REST_REQUESTS_COUNTER;
import static io.apicurio.registry.metrics.MetricsConstants.REST_REQUESTS_TAG_STATUS_CODE_FAMILY;

/**
 * @author Thomas Thornton
 */
public class RestMetricsResponseFilterTest {

    @Test
    public void testInit() {
        RestMetricsResponseFilter filter = new RestMetricsResponseFilter();
        MeterRegistry registry = new SimpleMeterRegistry();
        filter.registry = registry;
        filter.initializeCounters();
        String[] expectedStatusGroups = {"1xx", "2xx", "3xx", "4xx", "5xx"};
        for (String statusGroup: expectedStatusGroups) {
            Counter counter = registry.find(REST_REQUESTS_COUNTER)
                    .tags(REST_REQUESTS_TAG_STATUS_CODE_FAMILY, statusGroup)
                    .counter();
            Assertions.assertNotNull(counter);
            double value = counter.count();
            Assertions.assertEquals(0, value);
        }
        Assertions.assertEquals(registry.find(REST_REQUESTS_COUNTER).counters().size(), expectedStatusGroups.length);
    }

}
