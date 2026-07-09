/*
 * Copyright 2024 Red Hat
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

package io.apicurio.registry.serde.tracing;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SerDesTracerTest {

    private InMemorySpanExporter spanExporter;
    private SerDesTracer tracer;

    @BeforeEach
    void setUp() {
        GlobalOpenTelemetry.resetForTest();
        spanExporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
        OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .buildAndRegisterGlobal();
        tracer = new SerDesTracer();
    }

    @AfterEach
    void tearDown() {
        GlobalOpenTelemetry.resetForTest();
    }

    @Test
    void traceSerializeCreatesSpanWithCorrectAttributes() {
        byte[] result = tracer.traceSerialize("my-topic", span -> {
            span.setAttribute(SerDesAttributes.DATA_SIZE, 42L);
            return new byte[]{1, 2, 3};
        });

        assertEquals(3, result.length);

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertEquals(1, spans.size());

        SpanData spanData = spans.get(0);
        assertEquals("serde.serialize", spanData.getName());
        assertEquals(StatusCode.OK, spanData.getStatus().getStatusCode());
        assertEquals("my-topic", spanData.getAttributes().get(SerDesAttributes.TOPIC));
        assertEquals("serialize", spanData.getAttributes().get(SerDesAttributes.OPERATION));
        assertEquals(42L, spanData.getAttributes().get(SerDesAttributes.DATA_SIZE));
    }

    @Test
    void traceDeserializeCreatesSpanWithCorrectAttributes() {
        String result = tracer.traceDeserialize("my-topic", span -> "deserialized-value");

        assertEquals("deserialized-value", result);

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertEquals(1, spans.size());

        SpanData spanData = spans.get(0);
        assertEquals("serde.deserialize", spanData.getName());
        assertEquals(StatusCode.OK, spanData.getStatus().getStatusCode());
        assertEquals("my-topic", spanData.getAttributes().get(SerDesAttributes.TOPIC));
        assertEquals("deserialize", spanData.getAttributes().get(SerDesAttributes.OPERATION));
    }

    @Test
    void traceSchemaResolveCreatesSpanWithOperationType() {
        Object result = tracer.traceSchemaResolve("my-topic", "serialize", span -> {
            span.setAttribute(SerDesAttributes.ARTIFACT_ID, "my-schema");
            span.setAttribute(SerDesAttributes.CACHE_HIT, true);
            return "schema-result";
        });

        assertEquals("schema-result", result);

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertEquals(1, spans.size());

        SpanData spanData = spans.get(0);
        assertEquals("serde.resolve_schema", spanData.getName());
        assertEquals("serialize", spanData.getAttributes().get(SerDesAttributes.OPERATION));
        assertEquals("my-schema", spanData.getAttributes().get(SerDesAttributes.ARTIFACT_ID));
        assertEquals(true, spanData.getAttributes().get(SerDesAttributes.CACHE_HIT));
    }

    @Test
    void traceRecordsExceptionOnFailure() {
        RuntimeException thrown = assertThrows(RuntimeException.class, () ->
                tracer.traceSerialize("my-topic", span -> {
                    throw new RuntimeException("serialization failed");
                }));

        assertEquals("serialization failed", thrown.getMessage());

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertEquals(1, spans.size());

        SpanData spanData = spans.get(0);
        assertEquals(StatusCode.ERROR, spanData.getStatus().getStatusCode());
        assertEquals("serialization failed", spanData.getStatus().getDescription());

        List<EventData> events = spanData.getEvents();
        assertEquals(1, events.size());
        EventData exceptionEvent = events.get(0);
        assertEquals("exception", exceptionEvent.getName());
        assertEquals(RuntimeException.class.getName(),
                exceptionEvent.getAttributes().get(AttributeKey.stringKey("exception.type")));
        assertEquals("serialization failed",
                exceptionEvent.getAttributes().get(AttributeKey.stringKey("exception.message")));
    }

    @Test
    void traceCreatesNestedSpans() {
        tracer.traceSerialize("my-topic", outerSpan -> {
            tracer.traceSchemaResolve("my-topic", "serialize", innerSpan -> {
                innerSpan.setAttribute(SerDesAttributes.ARTIFACT_ID, "nested-schema");
                return "resolved";
            });
            return new byte[0];
        });

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertEquals(2, spans.size());

        SpanData innerSpan = spans.get(0);
        SpanData outerSpan = spans.get(1);

        assertEquals("serde.resolve_schema", innerSpan.getName());
        assertEquals("serde.serialize", outerSpan.getName());
        assertEquals(outerSpan.getSpanContext().getSpanId(), innerSpan.getParentSpanId());
    }

    @Test
    void traceWorksWithNoOpWhenOTelNotConfigured() {
        GlobalOpenTelemetry.resetForTest();
        SerDesTracer noOpTracer = new SerDesTracer();

        byte[] result = noOpTracer.traceSerialize("my-topic", span -> new byte[]{1, 2, 3});

        assertEquals(3, result.length);
    }
}
