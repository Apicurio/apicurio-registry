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
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.Scope;

public class SerDesTracer {

    private static final String INSTRUMENTATION_NAME = "io.apicurio.registry.serde";

    private boolean isTracingEnabled() {
        return GlobalOpenTelemetry.getTracerProvider() != TracerProvider.noop();
    }

    public <T> T traceSerialize(String topic, SerDesOperation<T> operation) {
        if (!isTracingEnabled()) {
            return operation.execute(Span.getInvalid());
        }
        return trace("serde.serialize", topic, "serialize", operation);
    }

    public <T> T traceDeserialize(String topic, SerDesOperation<T> operation) {
        if (!isTracingEnabled()) {
            return operation.execute(Span.getInvalid());
        }
        return trace("serde.deserialize", topic, "deserialize", operation);
    }

    public <T> T traceSchemaResolve(String topic, String operationType,
            SerDesOperation<T> operation) {
        if (!isTracingEnabled()) {
            return operation.execute(Span.getInvalid());
        }
        return trace("serde.resolve_schema", topic, operationType, operation);
    }

    private <T> T trace(String spanName, String topic, String operationType,
            SerDesOperation<T> operation) {
        Tracer tracer = GlobalOpenTelemetry.getTracer(INSTRUMENTATION_NAME);
        Span span = tracer.spanBuilder(spanName)
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(SerDesAttributes.TOPIC, topic)
                .setAttribute(SerDesAttributes.OPERATION, operationType)
                .startSpan();

        try (Scope scope = span.makeCurrent()) {
            T result = operation.execute(span);
            span.setStatus(StatusCode.OK);
            return result;
        } catch (RuntimeException e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    @FunctionalInterface
    public interface SerDesOperation<T> {
        T execute(Span span);
    }
}
