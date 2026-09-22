package io.apicurio.registry.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.interceptor.InvocationContext;
import org.eclipse.microprofile.context.ThreadContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StorageMetricsInterceptor}, in particular that the timer-caching optimization does
 * not change observable metric behavior: names/tags/counts/success-failure recording stay the same across
 * repeated and varied (success/failure) invocations of the same method.
 */
class StorageMetricsInterceptorTest {

    private SimpleMeterRegistry registry;
    private StorageMetricsInterceptor interceptor;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        interceptor = new StorageMetricsInterceptor();
        interceptor.registry = registry;
        interceptor.threadContext = ThreadContext.builder().build();
    }

    private InvocationContext contextFor(Method method, Object result) throws Exception {
        InvocationContext context = mock(InvocationContext.class);
        when(context.getMethod()).thenReturn(method);
        when(context.proceed()).thenReturn(result);
        return context;
    }

    @Test
    void repeatedSuccessfulCallsAccumulateOnASingleReusedTimer() throws Exception {
        Method method = TestStorageClass.class.getMethod("getArtifact", String.class);

        interceptor.intercept(contextFor(method, "ok"));
        interceptor.intercept(contextFor(method, "ok"));
        interceptor.intercept(contextFor(method, "ok"));

        double count = registry.find(MetricsConstants.STORAGE_METHOD_CALL)
                .tag(MetricsConstants.STORAGE_METHOD_CALL_TAG_METHOD, "getArtifact(String)")
                .tag(MetricsConstants.STORAGE_METHOD_CALL_TAG_SUCCESS, "true").timer().count();

        assertEquals(3, count);
        // Only one Timer meter should have been registered for this (method, success) pair, not one per call.
        assertEquals(1,
                registry.find(MetricsConstants.STORAGE_METHOD_CALL)
                        .tag(MetricsConstants.STORAGE_METHOD_CALL_TAG_METHOD, "getArtifact(String)")
                        .tag(MetricsConstants.STORAGE_METHOD_CALL_TAG_SUCCESS, "true").timers().size());
    }

    @Test
    void successAndFailureAreRecordedOnDistinctTimersForTheSameMethod() throws Exception {
        Method method = TestStorageClass.class.getMethod("deleteArtifact");

        InvocationContext okContext = mock(InvocationContext.class);
        when(okContext.getMethod()).thenReturn(method);
        when(okContext.proceed()).thenReturn(null);
        interceptor.intercept(okContext);

        InvocationContext failContext = mock(InvocationContext.class);
        when(failContext.getMethod()).thenReturn(method);
        when(failContext.proceed()).thenThrow(new RuntimeException("boom"));
        assertThrows(RuntimeException.class, () -> interceptor.intercept(failContext));

        double successCount = registry.find(MetricsConstants.STORAGE_METHOD_CALL)
                .tag(MetricsConstants.STORAGE_METHOD_CALL_TAG_METHOD, "deleteArtifact()")
                .tag(MetricsConstants.STORAGE_METHOD_CALL_TAG_SUCCESS, "true").timer().count();
        double failureCount = registry.find(MetricsConstants.STORAGE_METHOD_CALL)
                .tag(MetricsConstants.STORAGE_METHOD_CALL_TAG_METHOD, "deleteArtifact()")
                .tag(MetricsConstants.STORAGE_METHOD_CALL_TAG_SUCCESS, "false").timer().count();

        assertEquals(1, successCount);
        assertEquals(1, failureCount);
    }

    @Test
    void asyncResultIsRecordedOnCompletion() throws Exception {
        Method method = TestStorageClass.class.getMethod("getArtifactAsync", String.class);
        CompletableFuture<String> future = new CompletableFuture<>();
        InvocationContext context = mock(InvocationContext.class);
        when(context.getMethod()).thenReturn(method);
        when(context.proceed()).thenReturn(future);

        Object result = interceptor.intercept(context);
        future.complete("ok");

        double count = registry.find(MetricsConstants.STORAGE_METHOD_CALL)
                .tag(MetricsConstants.STORAGE_METHOD_CALL_TAG_METHOD, "getArtifactAsync(String)")
                .tag(MetricsConstants.STORAGE_METHOD_CALL_TAG_SUCCESS, "true").timer().count();

        assertEquals(future, result);
        assertEquals(1, count);
    }

    public static class TestStorageClass {
        public String getArtifact(String groupId) {
            return "artifact";
        }

        public void deleteArtifact() {
            // no-op
        }

        public CompletableFuture<String> getArtifactAsync(String groupId) {
            return CompletableFuture.completedFuture("artifact");
        }
    }
}
