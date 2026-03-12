package io.apicurio.registry.metrics;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import jakarta.interceptor.InvocationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StorageTracingInterceptor}.
 * Tests verify that OpenTelemetry spans are correctly created with proper attributes.
 */
class StorageTracingInterceptorTest {

    private InMemorySpanExporter spanExporter;
    private StorageTracingInterceptor interceptor;

    @BeforeEach
    void setUp() {
        // Reset GlobalOpenTelemetry to ensure clean state
        GlobalOpenTelemetry.resetForTest();

        // Create an in-memory span exporter for testing
        spanExporter = InMemorySpanExporter.create();

        // Create SDK with in-memory exporter
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();

        OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .buildAndRegisterGlobal();

        // Create the interceptor
        interceptor = new StorageTracingInterceptor();
    }

    @AfterEach
    void tearDown() {
        GlobalOpenTelemetry.resetForTest();
    }

    @Test
    void testInterceptCreatesSpan() throws Exception {
        // Mock the InvocationContext
        InvocationContext context = mock(InvocationContext.class);
        Method method = TestStorageClass.class.getMethod("getArtifact", String.class, String.class);
        when(context.getMethod()).thenReturn(method);
        when(context.getTarget()).thenReturn(new TestStorageClass());
        when(context.proceed()).thenReturn("result");

        // Execute the interceptor
        Object result = interceptor.intercept(context);

        // Verify the result
        assertEquals("result", result);

        // Verify span was created
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertEquals(1, spans.size());

        SpanData span = spans.get(0);
        assertEquals("storage.getArtifact", span.getName());
        assertEquals(StatusCode.OK, span.getStatus().getStatusCode());
    }

    @Test
    void testInterceptSetsStorageAttributes() throws Exception {
        InvocationContext context = mock(InvocationContext.class);
        Method method = TestStorageClass.class.getMethod("createArtifact", String.class);
        when(context.getMethod()).thenReturn(method);
        when(context.getTarget()).thenReturn(new TestStorageClass());
        when(context.proceed()).thenReturn(null);

        interceptor.intercept(context);

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertEquals(1, spans.size());

        SpanData span = spans.get(0);

        // Verify attributes
        assertEquals("createArtifact", span.getAttributes().get(AttributeKey.stringKey("storage.method")));
        assertEquals("TestStorageClass", span.getAttributes().get(AttributeKey.stringKey("storage.class")));
        assertEquals("createArtifact(String)", span.getAttributes().get(AttributeKey.stringKey("storage.method.signature")));
    }

    @Test
    void testInterceptRecordsExceptionOnError() throws Exception {
        InvocationContext context = mock(InvocationContext.class);
        Method method = TestStorageClass.class.getMethod("deleteArtifact");
        when(context.getMethod()).thenReturn(method);
        when(context.getTarget()).thenReturn(new TestStorageClass());
        when(context.proceed()).thenThrow(new RuntimeException("Storage error"));

        // Execute and expect exception
        RuntimeException exception = assertThrows(RuntimeException.class, () -> interceptor.intercept(context));
        assertEquals("Storage error", exception.getMessage());

        // Verify span was created with error status
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertEquals(1, spans.size());

        SpanData span = spans.get(0);
        assertEquals("storage.deleteArtifact", span.getName());
        assertEquals(StatusCode.ERROR, span.getStatus().getStatusCode());
        assertEquals("Storage error", span.getStatus().getDescription());

        // Verify exception was recorded
        assertFalse(span.getEvents().isEmpty());
        assertTrue(span.getEvents().stream()
                .anyMatch(event -> event.getName().equals("exception")));
    }

    @Test
    void testInterceptWithMultipleParameters() throws Exception {
        InvocationContext context = mock(InvocationContext.class);
        Method method = TestStorageClass.class.getMethod("updateVersion", String.class, String.class, int.class);
        when(context.getMethod()).thenReturn(method);
        when(context.getTarget()).thenReturn(new TestStorageClass());
        when(context.proceed()).thenReturn(true);

        Object result = interceptor.intercept(context);

        assertEquals(true, result);

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        SpanData span = spans.get(0);

        assertEquals("updateVersion(String,String,int)", span.getAttributes().get(AttributeKey.stringKey("storage.method.signature")));
    }

    @Test
    void testInterceptWithNoParameters() throws Exception {
        InvocationContext context = mock(InvocationContext.class);
        Method method = TestStorageClass.class.getMethod("deleteArtifact");
        when(context.getMethod()).thenReturn(method);
        when(context.getTarget()).thenReturn(new TestStorageClass());
        when(context.proceed()).thenReturn(null);

        interceptor.intercept(context);

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        SpanData span = spans.get(0);

        assertEquals("deleteArtifact()", span.getAttributes().get(AttributeKey.stringKey("storage.method.signature")));
    }

    @Test
    void testSpanEndedAfterExecution() throws Exception {
        InvocationContext context = mock(InvocationContext.class);
        Method method = TestStorageClass.class.getMethod("getArtifact", String.class, String.class);
        when(context.getMethod()).thenReturn(method);
        when(context.getTarget()).thenReturn(new TestStorageClass());
        when(context.proceed()).thenReturn("result");

        interceptor.intercept(context);

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        SpanData span = spans.get(0);

        // Verify span has end time set (meaning it was properly ended)
        assertNotNull(span.getEndEpochNanos());
        assertTrue(span.getEndEpochNanos() > span.getStartEpochNanos());
    }

    /**
     * Test class to simulate storage operations
     */
    public static class TestStorageClass {
        public String getArtifact(String groupId, String artifactId) {
            return "artifact";
        }

        public void createArtifact(String content) {
            // no-op
        }

        public void deleteArtifact() {
            // no-op
        }

        public boolean updateVersion(String groupId, String artifactId, int version) {
            return true;
        }
    }
}
