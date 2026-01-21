/*
 * Copyright 2024 Red Hat Inc
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

package io.apicurio.registry.examples.debezium.converter;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.connector.ConnectRecord;
import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.transforms.Transformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;
import java.util.Map;
import java.util.Properties;

/**
 * OpenTelemetry-based SMT for activating trace context in Debezium.
 *
 * This SMT extracts trace context from a configurable field in the message
 * and activates an OpenTelemetry span. Unlike the built-in ActivateTracingSpan
 * which uses OpenTracing, this uses OpenTelemetry directly, ensuring that
 * the OTEL Java Agent can properly propagate the trace to HTTP calls
 * made by subsequent components (like the Apicurio Registry serializer).
 *
 * Configuration:
 * - tracing.span.context.field: Field containing W3C trace context (default: tracingspancontext)
 * - tracing.operation.name: Name for the Debezium CDC span (default: debezium-cdc)
 */
public class OtelActivateTracingSpan<R extends ConnectRecord<R>> implements Transformation<R> {

    private static final Logger LOG = LoggerFactory.getLogger(OtelActivateTracingSpan.class);

    private static final String TRACE_CONTEXT_FIELD_CONFIG = "tracing.span.context.field";
    private static final String TRACE_CONTEXT_FIELD_DEFAULT = "tracingspancontext";
    private static final String OPERATION_NAME_CONFIG = "tracing.operation.name";
    private static final String OPERATION_NAME_DEFAULT = "debezium-cdc";

    private String traceContextField;
    private String operationName;
    private Tracer tracer;

    // Store the current span to properly close it
    private Span currentSpan;

    @Override
    public void configure(Map<String, ?> configs) {
        this.traceContextField = configs.containsKey(TRACE_CONTEXT_FIELD_CONFIG)
            ? configs.get(TRACE_CONTEXT_FIELD_CONFIG).toString()
            : TRACE_CONTEXT_FIELD_DEFAULT;

        this.operationName = configs.containsKey(OPERATION_NAME_CONFIG)
            ? configs.get(OPERATION_NAME_CONFIG).toString()
            : OPERATION_NAME_DEFAULT;

        this.tracer = GlobalOpenTelemetry.getTracer("debezium-otel-smt", "1.0.0");

        LOG.info("OtelActivateTracingSpan configured: field={}, operation={}",
            traceContextField, operationName);
    }

    @Override
    public R apply(R record) {
        if (record == null) {
            return null;
        }

        // Close any previously opened span
        if (currentSpan != null) {
            currentSpan.end();
            currentSpan = null;
        }

        // Extract trace context from the record value
        SpanContext parentContext = extractTraceContext(record.value());

        if (parentContext != null && parentContext.isValid()) {
            // Create a parent context with the extracted span
            Context parentOtelContext = Context.current().with(Span.wrap(parentContext));

            // Create and activate a span for this CDC event
            currentSpan = tracer.spanBuilder(operationName)
                    .setParent(parentOtelContext)
                    .setSpanKind(SpanKind.CONSUMER)
                    .setAttribute("messaging.system", "debezium")
                    .setAttribute("messaging.destination", record.topic())
                    .setAttribute("messaging.operation", "process")
                    .startSpan();

            // Make this span current so subsequent operations (like HTTP calls) use it
            currentSpan.makeCurrent();

            LOG.debug("Activated OTEL trace context: traceId={}, spanId={}, topic={}",
                parentContext.getTraceId(), parentContext.getSpanId(), record.topic());
        } else {
            LOG.debug("No valid trace context found in record for topic: {}", record.topic());
        }

        return record;
    }

    @Override
    public ConfigDef config() {
        return new ConfigDef()
            .define(TRACE_CONTEXT_FIELD_CONFIG, ConfigDef.Type.STRING, TRACE_CONTEXT_FIELD_DEFAULT,
                ConfigDef.Importance.MEDIUM, "Field containing W3C trace context")
            .define(OPERATION_NAME_CONFIG, ConfigDef.Type.STRING, OPERATION_NAME_DEFAULT,
                ConfigDef.Importance.MEDIUM, "Name for the Debezium CDC operation span");
    }

    @Override
    public void close() {
        if (currentSpan != null) {
            currentSpan.end();
            currentSpan = null;
        }
    }

    /**
     * Extract the trace context from the Connect record value.
     * Handles both unwrapped records (direct field access) and
     * Debezium envelope format (field in 'after' section).
     */
    private SpanContext extractTraceContext(Object value) {
        if (value == null) {
            return null;
        }

        String traceContextValue = null;

        if (value instanceof Struct) {
            Struct struct = (Struct) value;
            Schema schema = struct.schema();

            // First try direct field access (for unwrapped records)
            Field field = schema.field(traceContextField);
            if (field != null) {
                Object fieldValue = struct.get(field);
                if (fieldValue != null) {
                    traceContextValue = fieldValue.toString();
                }
            }

            // If not found, try looking in the 'after' section (Debezium envelope format)
            if (traceContextValue == null) {
                Field afterField = schema.field("after");
                if (afterField != null) {
                    Object afterValue = struct.get("after");
                    if (afterValue instanceof Struct) {
                        Struct afterStruct = (Struct) afterValue;
                        Field ctxField = afterStruct.schema().field(traceContextField);
                        if (ctxField != null) {
                            Object ctxValue = afterStruct.get(ctxField);
                            if (ctxValue != null) {
                                traceContextValue = ctxValue.toString();
                            }
                        }
                    }
                }
            }

            // Also try 'source' section where Debezium stores tracing context
            if (traceContextValue == null) {
                Field sourceField = schema.field("source");
                if (sourceField != null) {
                    Object sourceValue = struct.get("source");
                    if (sourceValue instanceof Struct) {
                        Struct sourceStruct = (Struct) sourceValue;
                        Field ctxField = sourceStruct.schema().field(traceContextField);
                        if (ctxField != null) {
                            Object ctxValue = sourceStruct.get(ctxField);
                            if (ctxValue != null) {
                                traceContextValue = ctxValue.toString();
                            }
                        }
                    }
                }
            }
        } else if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) value;
            Object fieldValue = map.get(traceContextField);
            if (fieldValue != null) {
                traceContextValue = fieldValue.toString();
            }
        }

        if (traceContextValue != null && !traceContextValue.isEmpty()) {
            return parseTraceparent(traceContextValue);
        }

        return null;
    }

    /**
     * Parse W3C traceparent from Properties format or key=value format.
     * Format: traceparent=00-{traceId}-{spanId}-{flags}
     */
    private SpanContext parseTraceparent(String tracingSpanContext) {
        if (tracingSpanContext == null || tracingSpanContext.isEmpty()) {
            return null;
        }

        try {
            String traceparent = null;

            // Try Properties format first
            if (tracingSpanContext.contains("=")) {
                Properties props = new Properties();
                // Handle both newline-separated and comma-separated formats
                String normalized = tracingSpanContext.replace(",", "\n");
                props.load(new StringReader(normalized));
                traceparent = props.getProperty("traceparent");
            }

            if (traceparent == null) {
                LOG.debug("No traceparent found in context: {}", tracingSpanContext);
                return null;
            }

            // Parse traceparent: 00-{traceId}-{spanId}-{flags}
            String[] parts = traceparent.split("-");
            if (parts.length < 4) {
                LOG.debug("Invalid traceparent format: {}", traceparent);
                return null;
            }

            String version = parts[0];
            String traceId = parts[1];
            String spanId = parts[2];
            String flags = parts[3];

            if (!"00".equals(version) || traceId.length() != 32 || spanId.length() != 16) {
                LOG.debug("Invalid traceparent values: version={}, traceId={}, spanId={}",
                    version, traceId, spanId);
                return null;
            }

            TraceFlags traceFlags = "01".equals(flags) ? TraceFlags.getSampled() : TraceFlags.getDefault();

            SpanContext context = SpanContext.createFromRemoteParent(
                    traceId,
                    spanId,
                    traceFlags,
                    TraceState.getDefault()
            );

            LOG.debug("Parsed trace context: traceId={}, spanId={}", traceId, spanId);
            return context;

        } catch (Exception e) {
            LOG.debug("Failed to parse traceparent: {} - {}", tracingSpanContext, e.getMessage());
            return null;
        }
    }
}
