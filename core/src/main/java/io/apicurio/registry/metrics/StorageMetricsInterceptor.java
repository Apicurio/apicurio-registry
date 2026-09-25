package io.apicurio.registry.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.eclipse.microprofile.context.ThreadContext;

import java.lang.reflect.Method;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

import static io.apicurio.registry.metrics.MetricsConstants.STORAGE_METHOD_CALL;
import static io.apicurio.registry.metrics.MetricsConstants.STORAGE_METHOD_CALL_DESCRIPTION;
import static io.apicurio.registry.metrics.MetricsConstants.STORAGE_METHOD_CALL_TAG_METHOD;
import static io.apicurio.registry.metrics.MetricsConstants.STORAGE_METHOD_CALL_TAG_SUCCESS;

/**
 * Fail readiness check if the duration of processing a artifactStore operation is too high.
 * <p>
 * For KafkaSQL, both the federated {@code KafkaSqlRegistryStorage} method and the underlying
 * {@code SqlRegistryStorage} method it delegates reads to are annotated with {@link StorageMetricsApply},
 * so a single logical read intentionally produces two timer recordings (one per layer). This mirrors a
 * parent/child span relationship and is left as-is; collapsing it would require changing how
 * {@code KafkaSqlRegistryStorage} invokes its injected {@code sqlStore}, which is out of scope here.
 */
@Interceptor
@StorageMetricsApply
public class StorageMetricsInterceptor {

    @Inject
    MeterRegistry registry;

    @Inject
    ThreadContext threadContext;

    /**
     * Caches the two {@link Timer} instances (failure/success) for each intercepted method, keyed by
     * {@link Method}. Building a {@code Timer} involves constructing tags and doing a registry lookup by
     * {@code Meter.Id}; since the set of intercepted methods is fixed, precomputing and reusing the timers
     * avoids repeating that work on every storage call.
     */
    private final ConcurrentHashMap<Method, Timer[]> timerCache = new ConcurrentHashMap<>();

    @AroundInvoke
    public Object intercept(InvocationContext context) throws Exception {

        Exception exception = null;
        Object result = null;

        Timer.Sample sample = Timer.start(registry);

        try {
            result = context.proceed();
        } catch (Exception ex) {
            exception = ex;
        }

        if (exception != null) {
            this.record(sample, context.getMethod(), false);
            throw exception;
        }

        if (result instanceof CompletionStage) {
            CompletionStage<?> r = (CompletionStage<?>) result;
            threadContext.withContextCapture(r)
                    .whenComplete((ok, ex) -> this.record(sample, context.getMethod(), ex == null)); // TODO
            return r;
        }

        this.record(sample, context.getMethod(), true);
        return result;
    }

    private void record(Timer.Sample sample, Method method, boolean success) {
        sample.stop(timerFor(method, success));
    }

    private Timer timerFor(Method method, boolean success) {
        Timer[] timers = timerCache.computeIfAbsent(method, this::buildTimers);
        return timers[success ? 1 : 0];
    }

    private Timer[] buildTimers(Method method) {
        String methodTag = StorageMethodSignatureCache.of(method);
        return new Timer[] { buildTimer(methodTag, false), buildTimer(methodTag, true) };
    }

    private Timer buildTimer(String methodTag, boolean success) {
        return Timer.builder(STORAGE_METHOD_CALL).description(STORAGE_METHOD_CALL_DESCRIPTION)
                .tag(STORAGE_METHOD_CALL_TAG_METHOD, methodTag)
                .tag(STORAGE_METHOD_CALL_TAG_SUCCESS, String.valueOf(success)).register(registry);
    }
}
