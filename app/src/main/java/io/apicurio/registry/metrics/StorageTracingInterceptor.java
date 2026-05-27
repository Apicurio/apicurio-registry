package io.apicurio.registry.metrics;

import io.apicurio.registry.observability.OTelAttributes;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

import java.lang.reflect.Method;

/**
 * Interceptor that creates OpenTelemetry spans for storage operations.
 * This interceptor works alongside StorageMetricsInterceptor to provide
 * distributed tracing capabilities for storage layer operations.
 *
 * When OpenTelemetry is disabled, the GlobalOpenTelemetry.getTracer() returns
 * a no-op tracer that has minimal overhead.
 */
@Interceptor
@StorageMetricsApply
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 10)
public class StorageTracingInterceptor {

    private static final String INSTRUMENTATION_NAME = "io.apicurio.registry.storage";
    private static final String INSTRUMENTATION_VERSION = "3.x";

    @AroundInvoke
    public Object intercept(InvocationContext context) throws Exception {
        Tracer tracer = GlobalOpenTelemetry.getTracer(INSTRUMENTATION_NAME, INSTRUMENTATION_VERSION);
        String spanName = "storage." + context.getMethod().getName();

        Span span = tracer.spanBuilder(spanName)
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(OTelAttributes.ATTR_STORAGE_METHOD, context.getMethod().getName())
                .setAttribute(OTelAttributes.ATTR_STORAGE_CLASS, context.getTarget().getClass().getSimpleName())
                .setAttribute(OTelAttributes.ATTR_STORAGE_METHOD_SIGNATURE, getMethodString(context.getMethod()))
                .startSpan();

        try (Scope scope = span.makeCurrent()) {
            Object result = context.proceed();
            span.setStatus(StatusCode.OK);
            return result;
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
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
