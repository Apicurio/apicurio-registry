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

import static io.apicurio.registry.metrics.MetricsConstants.STORAGE_METHOD_CALL;
import static io.apicurio.registry.metrics.MetricsConstants.STORAGE_METHOD_CALL_DESCRIPTION;
import static io.apicurio.registry.metrics.MetricsConstants.STORAGE_METHOD_CALL_TAG_METHOD;
import static io.apicurio.registry.metrics.MetricsConstants.STORAGE_METHOD_CALL_TAG_SUCCESS;

/**
 * Fail readiness check if the duration of processing a artifactStore operation is too high.
 */
@Interceptor
@StorageMetricsApply
public class StorageMetricsInterceptor {

    @Inject
    MeterRegistry registry;

    @Inject
    ThreadContext threadContext;

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
        Timer timer = Timer.builder(STORAGE_METHOD_CALL).description(STORAGE_METHOD_CALL_DESCRIPTION)
                .tag(STORAGE_METHOD_CALL_TAG_METHOD, getMethodString(method))
                .tag(STORAGE_METHOD_CALL_TAG_SUCCESS, String.valueOf(success)).register(registry);
        sample.stop(timer);
    }

    private static String getMethodString(Method method) {
        StringBuilder res = new StringBuilder();
        res.append(method.getName());
        res.append('(');
        Class<?>[] types = method.getParameterTypes();
        for (int i = 0; i < types.length; i++) {
            res.append(types[i].getSimpleName());
            if (i != types.length - 1)
                res.append(',');
        }
        res.append(')');
        return res.toString();
    }
}
