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
import java.util.concurrent.ConcurrentHashMap;

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

    /**
     * Precomputed, immutable span metadata (span name, method signature, and target class name) for each
     * intercepted method. Even with a no-op tracer, building this data involves string concatenation and a
     * reflective {@code getSimpleName()} call on every invocation; since the set of intercepted methods (and
     * their target class) is fixed per deployment, computing it once and reusing it avoids repeating that
     * work on every storage call.
     */
    private final ConcurrentHashMap<Method, SpanMetadata> spanMetadataCache = new ConcurrentHashMap<>();

    @AroundInvoke
    public Object intercept(InvocationContext context) throws Exception {
        Tracer tracer = GlobalOpenTelemetry.getTracer(INSTRUMENTATION_NAME, INSTRUMENTATION_VERSION);
        SpanMetadata metadata = spanMetadataCache.computeIfAbsent(context.getMethod(),
                method -> buildSpanMetadata(method, context.getTarget()));

        Span span = tracer.spanBuilder(metadata.spanName).setSpanKind(SpanKind.INTERNAL)
                .setAttribute(OTelAttributes.ATTR_STORAGE_METHOD, metadata.methodName)
                .setAttribute(OTelAttributes.ATTR_STORAGE_CLASS, metadata.className)
                .setAttribute(OTelAttributes.ATTR_STORAGE_METHOD_SIGNATURE, metadata.methodSignature)
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

    private static SpanMetadata buildSpanMetadata(Method method, Object target) {
        return new SpanMetadata("storage." + method.getName(), method.getName(),
                target.getClass().getSimpleName(), StorageMethodSignatureCache.of(method));
    }

    private static final class SpanMetadata {
        private final String spanName;
        private final String methodName;
        private final String className;
        private final String methodSignature;

        private SpanMetadata(String spanName, String methodName, String className, String methodSignature) {
            this.spanName = spanName;
            this.methodName = methodName;
            this.className = className;
            this.methodSignature = methodSignature;
        }
    }
}
