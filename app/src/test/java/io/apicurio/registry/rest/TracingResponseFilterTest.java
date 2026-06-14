/*
 * Copyright 2025 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.rest;

import io.apicurio.registry.observability.OTelAttributes;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TracingResponseFilterTest {

    private InMemorySpanExporter spanExporter;
    private TracingResponseFilter filter;
    private Tracer tracer;

    @BeforeEach
    void setUp() {
        GlobalOpenTelemetry.resetForTest();

        spanExporter = InMemorySpanExporter.create();

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();

        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .buildAndRegisterGlobal();

        tracer = sdk.getTracer("test");
        filter = new TracingResponseFilter();
    }

    @AfterEach
    void tearDown() {
        GlobalOpenTelemetry.resetForTest();
    }

    @Test
    void testSuccessResponseSetsStatusCodeAttribute() {
        Span span = tracer.spanBuilder("test-request").startSpan();

        try (Scope scope = span.makeCurrent()) {
            filter.filter(mockRequest(), mockResponse(200));
        } finally {
            span.end();
        }

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertEquals(1, spans.size());

        SpanData spanData = spans.get(0);
        assertEquals(200L, spanData.getAttributes().get(OTelAttributes.ATTR_HTTP_RESPONSE_STATUS_CODE));
        assertEquals(StatusCode.UNSET, spanData.getStatus().getStatusCode());
    }

    @Test
    void testRedirectDoesNotSetErrorStatus() {
        Span span = tracer.spanBuilder("test-request").startSpan();

        try (Scope scope = span.makeCurrent()) {
            filter.filter(mockRequest(), mockResponse(301));
        } finally {
            span.end();
        }

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        SpanData spanData = spans.get(0);
        assertEquals(301L, spanData.getAttributes().get(OTelAttributes.ATTR_HTTP_RESPONSE_STATUS_CODE));
        assertEquals(StatusCode.UNSET, spanData.getStatus().getStatusCode());
    }

    @Test
    void testClientErrorDoesNotSetErrorStatus() {
        Span span = tracer.spanBuilder("test-request").startSpan();

        try (Scope scope = span.makeCurrent()) {
            filter.filter(mockRequest(), mockResponse(404));
        } finally {
            span.end();
        }

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        SpanData spanData = spans.get(0);
        assertEquals(404L, spanData.getAttributes().get(OTelAttributes.ATTR_HTTP_RESPONSE_STATUS_CODE));
        assertEquals(StatusCode.UNSET, spanData.getStatus().getStatusCode());
    }

    @Test
    void testServerErrorSetsErrorStatus() {
        Span span = tracer.spanBuilder("test-request").startSpan();

        try (Scope scope = span.makeCurrent()) {
            filter.filter(mockRequest(), mockResponse(500));
        } finally {
            span.end();
        }

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        SpanData spanData = spans.get(0);
        assertEquals(500L, spanData.getAttributes().get(OTelAttributes.ATTR_HTTP_RESPONSE_STATUS_CODE));
        assertEquals(StatusCode.ERROR, spanData.getStatus().getStatusCode());
        assertEquals("HTTP 500", spanData.getStatus().getDescription());
    }

    @Test
    void testServiceUnavailableSetsErrorStatus() {
        Span span = tracer.spanBuilder("test-request").startSpan();

        try (Scope scope = span.makeCurrent()) {
            filter.filter(mockRequest(), mockResponse(503));
        } finally {
            span.end();
        }

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        SpanData spanData = spans.get(0);
        assertEquals(503L, spanData.getAttributes().get(OTelAttributes.ATTR_HTTP_RESPONSE_STATUS_CODE));
        assertEquals(StatusCode.ERROR, spanData.getStatus().getStatusCode());
        assertEquals("HTTP 503", spanData.getStatus().getDescription());
    }

    @Test
    void testBoundary499DoesNotSetErrorStatus() {
        Span span = tracer.spanBuilder("test-request").startSpan();

        try (Scope scope = span.makeCurrent()) {
            filter.filter(mockRequest(), mockResponse(499));
        } finally {
            span.end();
        }

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        SpanData spanData = spans.get(0);
        assertEquals(499L, spanData.getAttributes().get(OTelAttributes.ATTR_HTTP_RESPONSE_STATUS_CODE));
        assertEquals(StatusCode.UNSET, spanData.getStatus().getStatusCode());
    }

    @Test
    void testNoActiveSpanDoesNotThrow() {
        filter.filter(mockRequest(), mockResponse(200));

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertEquals(0, spans.size());
    }

    private ContainerRequestContext mockRequest() {
        return mock(ContainerRequestContext.class);
    }

    private ContainerResponseContext mockResponse(int statusCode) {
        ContainerResponseContext response = mock(ContainerResponseContext.class);
        when(response.getStatus()).thenReturn(statusCode);
        return response;
    }
}
