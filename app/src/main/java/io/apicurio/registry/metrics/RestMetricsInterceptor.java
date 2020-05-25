package io.apicurio.registry.metrics;

import org.eclipse.microprofile.metrics.ConcurrentGauge;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;
import org.eclipse.microprofile.metrics.annotation.RegistryType;

import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

import static io.apicurio.registry.metrics.MetricIDs.*;
import static org.eclipse.microprofile.metrics.MetricRegistry.Type.APPLICATION;
import static org.eclipse.microprofile.metrics.MetricType.*;
import static org.eclipse.microprofile.metrics.MetricUnits.MILLISECONDS;

/**
 * Interceptor that tracks metrics across all REST resources.
 *
 * @author Jakub Senko <jsenko@redhat.com>
 */
@Interceptor
@RestMetricsApply
public class RestMetricsInterceptor {


    @Inject
    @RegistryType(type = APPLICATION)
    MetricRegistry metricRegistry;

    private Counter counter;

    private ConcurrentGauge gauge;

    private Timer timer;

    private boolean init = false;

    synchronized void init() {
        // Total counter
        final Metadata m1 = Metadata.builder()
                .withName(REST_REQUEST_COUNT)
                .withDescription(REST_REQUEST_COUNT_DESC + " Across all endpoints.")
                .withType(COUNTER)
                .build();
        final Tag[] tags1 = {new Tag("group", REST_GROUP_TAG), new Tag("metric", REST_REQUEST_COUNT)};
        counter = metricRegistry.counter(m1, tags1);
        // Concurrent gauge
        final Metadata m2 = Metadata.builder()
                .withName(REST_CONCURRENT_REQUEST_COUNT)
                .withDescription(REST_CONCURRENT_REQUEST_COUNT_DESC + " Across all endpoints.")
                .withType(CONCURRENT_GAUGE)
                .build();
        final Tag[] tags2 = {new Tag("group", REST_GROUP_TAG), new Tag("metric", REST_CONCURRENT_REQUEST_COUNT)};
        gauge = metricRegistry.concurrentGauge(m2, tags2);
        // Timer
        final Metadata m3 = Metadata.builder()
                .withName(REST_REQUEST_RESPONSE_TIME)
                .withDescription(REST_REQUEST_RESPONSE_TIME_DESC + " Across all endpoints.")
                .withType(TIMER)
                .withUnit(MILLISECONDS)
                .build();
        final Tag[] tags3 = {new Tag("group", REST_GROUP_TAG), new Tag("metric", REST_REQUEST_RESPONSE_TIME)};
        timer = metricRegistry.timer(m3, tags3);

        init = true;
    }

    @AroundInvoke
    public Object intercept(InvocationContext context) throws Exception {
        if (!init) {
            init(); // @PostConstruct causes MethodNotFound ex.
        }
        counter.inc();
        gauge.inc();
        Timer.Context time = timer.time();
        try {
            return context.proceed();
        } finally {
            time.stop();
            gauge.dec();
        }
    }
}
