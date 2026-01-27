package io.apicurio.registry.rest;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TracingFilter}.
 * Tests verify that spans are enriched with Apicurio-specific attributes.
 */
class TracingFilterTest {

    private InMemorySpanExporter spanExporter;
    private TracingFilter filter;
    private Tracer tracer;

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

        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .buildAndRegisterGlobal();

        tracer = sdk.getTracer("test");

        // Create the filter
        filter = new TracingFilter();
    }

    @AfterEach
    void tearDown() {
        GlobalOpenTelemetry.resetForTest();
    }

    @Test
    void testFilterAddsHeaderAttributes() {
        // Create a span to be enriched
        Span span = tracer.spanBuilder("test-request").startSpan();

        try (Scope scope = span.makeCurrent()) {
            // Mock the request context with headers
            ContainerRequestContext context = createMockContext(
                    "test-group", "test-artifact", "1.0", "AVRO",
                    "/apis/registry/v3/groups/test-group/artifacts/test-artifact",
                    new MultivaluedHashMap<>()
            );

            // Apply the filter
            filter.filter(context);
        } finally {
            span.end();
        }

        // Verify attributes were added
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertEquals(1, spans.size());

        SpanData spanData = spans.get(0);
        assertEquals("test-group", spanData.getAttributes().get(AttributeKey.stringKey("apicurio.groupId")));
        assertEquals("test-artifact", spanData.getAttributes().get(AttributeKey.stringKey("apicurio.artifactId")));
        assertEquals("1.0", spanData.getAttributes().get(AttributeKey.stringKey("apicurio.version")));
        assertEquals("AVRO", spanData.getAttributes().get(AttributeKey.stringKey("apicurio.artifactType")));
    }

    @Test
    void testFilterAddsRequestPath() {
        Span span = tracer.spanBuilder("test-request").startSpan();

        try (Scope scope = span.makeCurrent()) {
            ContainerRequestContext context = createMockContext(
                    null, null, null, null,
                    "/apis/registry/v3/groups",
                    new MultivaluedHashMap<>()
            );

            filter.filter(context);
        } finally {
            span.end();
        }

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        SpanData spanData = spans.get(0);
        assertEquals("/apis/registry/v3/groups", spanData.getAttributes().get(AttributeKey.stringKey("apicurio.request.path")));
    }

    @Test
    void testFilterAddsPathParameters() {
        Span span = tracer.spanBuilder("test-request").startSpan();

        try (Scope scope = span.makeCurrent()) {
            MultivaluedMap<String, String> pathParams = new MultivaluedHashMap<>();
            pathParams.putSingle("groupId", "path-group");
            pathParams.putSingle("artifactId", "path-artifact");
            pathParams.putSingle("version", "2.0");

            ContainerRequestContext context = createMockContext(
                    null, null, null, null,
                    "/apis/registry/v3/groups/path-group/artifacts/path-artifact/versions/2.0",
                    pathParams
            );

            filter.filter(context);
        } finally {
            span.end();
        }

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        SpanData spanData = spans.get(0);
        assertEquals("path-group", spanData.getAttributes().get(AttributeKey.stringKey("apicurio.path.groupId")));
        assertEquals("path-artifact", spanData.getAttributes().get(AttributeKey.stringKey("apicurio.path.artifactId")));
        assertEquals("2.0", spanData.getAttributes().get(AttributeKey.stringKey("apicurio.path.version")));
    }

    @Test
    void testFilterWithNoHeaders() {
        Span span = tracer.spanBuilder("test-request").startSpan();

        try (Scope scope = span.makeCurrent()) {
            ContainerRequestContext context = createMockContext(
                    null, null, null, null,
                    "/apis/registry/v3/system/info",
                    new MultivaluedHashMap<>()
            );

            filter.filter(context);
        } finally {
            span.end();
        }

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        SpanData spanData = spans.get(0);

        // Header attributes should not be set
        assertNull(spanData.getAttributes().get(AttributeKey.stringKey("apicurio.groupId")));
        assertNull(spanData.getAttributes().get(AttributeKey.stringKey("apicurio.artifactId")));

        // Path should still be set
        assertEquals("/apis/registry/v3/system/info", spanData.getAttributes().get(AttributeKey.stringKey("apicurio.request.path")));
    }

    @Test
    void testFilterWithEmptyHeaders() {
        Span span = tracer.spanBuilder("test-request").startSpan();

        try (Scope scope = span.makeCurrent()) {
            ContainerRequestContext context = mock(ContainerRequestContext.class);
            UriInfo uriInfo = mock(UriInfo.class);
            when(context.getUriInfo()).thenReturn(uriInfo);
            when(uriInfo.getPath()).thenReturn("/test");
            when(uriInfo.getPathParameters()).thenReturn(new MultivaluedHashMap<>());

            // Return empty strings for headers
            when(context.getHeaderString("X-Registry-GroupId")).thenReturn("");
            when(context.getHeaderString("X-Registry-ArtifactId")).thenReturn("");
            when(context.getHeaderString("X-Registry-Version")).thenReturn("");
            when(context.getHeaderString("X-Registry-ArtifactType")).thenReturn("");

            filter.filter(context);
        } finally {
            span.end();
        }

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        SpanData spanData = spans.get(0);

        // Empty headers should not create attributes
        assertNull(spanData.getAttributes().get(AttributeKey.stringKey("apicurio.groupId")));
    }

    @Test
    void testFilterWhenNoActiveSpan() {
        // No active span - filter should handle gracefully
        ContainerRequestContext context = createMockContext(
                "test-group", "test-artifact", null, null,
                "/test",
                new MultivaluedHashMap<>()
        );

        // Should not throw exception
        filter.filter(context);

        // No spans should be created or modified
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertEquals(0, spans.size());
    }

    @Test
    void testFilterWithBothHeadersAndPathParams() {
        Span span = tracer.spanBuilder("test-request").startSpan();

        try (Scope scope = span.makeCurrent()) {
            MultivaluedMap<String, String> pathParams = new MultivaluedHashMap<>();
            pathParams.putSingle("groupId", "path-group");
            pathParams.putSingle("artifactId", "path-artifact");

            ContainerRequestContext context = createMockContext(
                    "header-group", "header-artifact", "1.0", "JSON",
                    "/apis/registry/v3/groups/path-group/artifacts/path-artifact",
                    pathParams
            );

            filter.filter(context);
        } finally {
            span.end();
        }

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        SpanData spanData = spans.get(0);

        // Both header and path attributes should be set
        assertEquals("header-group", spanData.getAttributes().get(AttributeKey.stringKey("apicurio.groupId")));
        assertEquals("path-group", spanData.getAttributes().get(AttributeKey.stringKey("apicurio.path.groupId")));
    }

    private ContainerRequestContext createMockContext(String groupId, String artifactId,
            String version, String artifactType, String path, MultivaluedMap<String, String> pathParams) {

        ContainerRequestContext context = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);

        when(context.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getPath()).thenReturn(path);
        when(uriInfo.getPathParameters()).thenReturn(pathParams);

        when(context.getHeaderString("X-Registry-GroupId")).thenReturn(groupId);
        when(context.getHeaderString("X-Registry-ArtifactId")).thenReturn(artifactId);
        when(context.getHeaderString("X-Registry-Version")).thenReturn(version);
        when(context.getHeaderString("X-Registry-ArtifactType")).thenReturn(artifactType);

        return context;
    }
}
