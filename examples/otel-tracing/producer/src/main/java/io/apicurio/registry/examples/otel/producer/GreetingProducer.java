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

package io.apicurio.registry.examples.otel.producer;

import io.apicurio.registry.examples.otel.Greeting;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.smallrye.reactive.messaging.kafka.Record;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.logging.Logger;

import java.util.concurrent.CompletionStage;

/**
 * Producer service that sends Greeting messages to Kafka with OpenTelemetry tracing.
 *
 * This class demonstrates:
 * - Automatic trace context propagation to Kafka messages
 * - Custom span creation for business logic
 * - Schema registration with Apicurio Registry via the Avro serializer
 */
@ApplicationScoped
public class GreetingProducer {

    private static final Logger LOG = Logger.getLogger(GreetingProducer.class);

    @Inject
    Tracer tracer;

    @Inject
    @Channel("greetings-out")
    Emitter<Record<String, Greeting>> greetingsEmitter;

    /**
     * Sends a greeting message to Kafka.
     *
     * The trace context is automatically propagated to Kafka headers by the
     * OpenTelemetry instrumentation. The Avro serializer automatically registers
     * the schema with Apicurio Registry on first use.
     *
     * @param name The name to greet
     * @return CompletionStage that completes when the message is acknowledged
     */
    public CompletionStage<Void> sendGreeting(String name) {
        // Create a custom span for the business logic
        Span span = tracer.spanBuilder("create-greeting")
                .setAttribute("greeting.name", name)
                .startSpan();

        try (Scope scope = span.makeCurrent()) {
            // Get the current trace ID for logging correlation
            String traceId = Span.current().getSpanContext().getTraceId();

            // Create the greeting message
            Greeting greeting = Greeting.newBuilder()
                    .setMessage("Hello, " + name + "!")
                    .setTimestamp(System.currentTimeMillis())
                    .setSource("greeting-producer")
                    .setTraceId(traceId)
                    .build();

            LOG.infof("Sending greeting: %s (traceId: %s)", greeting.getMessage(), traceId);

            // Add event to the span
            span.addEvent("greeting-created");

            // Use the name as the Kafka message key for partitioning
            String key = name.toLowerCase().replaceAll("\\s+", "-");

            // Send the message - trace context is automatically propagated
            return greetingsEmitter.send(Record.of(key, greeting))
                    .thenAccept(v -> {
                        span.addEvent("greeting-sent");
                        LOG.infof("Greeting sent successfully for: %s", name);
                    })
                    .exceptionally(throwable -> {
                        span.recordException(throwable);
                        LOG.errorf(throwable, "Failed to send greeting for: %s", name);
                        throw new RuntimeException("Failed to send greeting", throwable);
                    });
        } finally {
            span.end();
        }
    }

    /**
     * Sends multiple greeting messages for load testing.
     *
     * @param baseName The base name to use for greetings
     * @param count The number of messages to send
     */
    public void sendMultipleGreetings(String baseName, int count) {
        Span span = tracer.spanBuilder("send-batch-greetings")
                .setAttribute("batch.size", count)
                .setAttribute("batch.baseName", baseName)
                .startSpan();

        try (Scope scope = span.makeCurrent()) {
            for (int i = 1; i <= count; i++) {
                String name = baseName + " #" + i;
                sendGreeting(name);
            }
            span.addEvent("batch-complete");
        } finally {
            span.end();
        }
    }
}
