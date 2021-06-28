package io.apicurio.registry.metrics;

import io.apicurio.registry.mt.TenantContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.eclipse.microprofile.context.ThreadContext;

import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;
import java.lang.reflect.Method;
import java.util.concurrent.CompletionStage;

import static io.apicurio.registry.metrics.MetricsConstants.STORAGE_METHOD_CALL;
import static io.apicurio.registry.metrics.MetricsConstants.STORAGE_METHOD_CALL_DESCRIPTION;
import static io.apicurio.registry.metrics.MetricsConstants.STORAGE_METHOD_CALL_TAG_METHOD;
import static io.apicurio.registry.metrics.MetricsConstants.STORAGE_METHOD_CALL_TAG_SUCCESS;
import static io.apicurio.registry.metrics.MetricsConstants.STORAGE_METHOD_CALL_TAG_TENANT;

/**
 * Fail readiness check if the duration of processing a artifactStore operation is too high.
 *
 * @author Jakub Senko 'jsenko@redhat.com'
 */
@Interceptor
@StorageMetricsApply
public class StorageMetricsInterceptor {

    @Inject
    MeterRegistry registry;

    @Inject
    TenantContext tenantContext;

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
            threadContext.withContextCapture(r).whenComplete((ok, ex) ->
                this.record(sample, context.getMethod(), ex == null)); // TODO
            return r;
        }

        this.record(sample, context.getMethod(), true);
        return result;
    }

    private void record(Timer.Sample sample, Method method, boolean success) {
        Timer timer = Timer
            .builder(STORAGE_METHOD_CALL)
            .description(STORAGE_METHOD_CALL_DESCRIPTION)
            .tag(STORAGE_METHOD_CALL_TAG_TENANT, this.tenantContext.getTenantIdOrElse(""))
            .tag(STORAGE_METHOD_CALL_TAG_METHOD, getMethodString(method))
            .tag(STORAGE_METHOD_CALL_TAG_SUCCESS, String.valueOf(success))
            .register(registry);
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
