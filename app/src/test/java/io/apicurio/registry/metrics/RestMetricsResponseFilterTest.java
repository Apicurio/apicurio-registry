package io.apicurio.registry.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import static io.apicurio.registry.metrics.MetricsConstants.REST_REQUESTS;
import static io.apicurio.registry.metrics.MetricsConstants.REST_REQUESTS_TAG_STATUS_CODE_GROUP;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
        for (String statusGroup : expectedStatusGroups) {
            var timer = registry.find(REST_REQUESTS)
                    .tags(REST_REQUESTS_TAG_STATUS_CODE_GROUP, statusGroup)
                    .timer();
            Assertions.assertNotNull(timer);
            double value = timer.count();
            assertEquals(0, value);
        }
        assertEquals(registry.find(REST_REQUESTS).timers().size(), expectedStatusGroups.length);
    }
}
