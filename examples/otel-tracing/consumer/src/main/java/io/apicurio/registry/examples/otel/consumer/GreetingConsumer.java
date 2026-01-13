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

package io.apicurio.registry.examples.otel.consumer;

import io.apicurio.registry.examples.otel.Greeting;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.smallrye.reactive.messaging.kafka.Record;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Consumer service that receives Greeting messages from Kafka with OpenTelemetry tracing.
 *
 * This class demonstrates:
 * - Automatic trace context extraction from Kafka message headers
 * - Custom span creation for message processing
 * - Schema lookup from Apicurio Registry via the Avro deserializer
 * - Correlation of traces across producer and consumer
 */
@ApplicationScoped
public class GreetingConsumer {

    private static final Logger LOG = Logger.getLogger(GreetingConsumer.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());

    private final AtomicLong messagesReceived = new AtomicLong(0);
    private final AtomicLong messagesProcessed = new AtomicLong(0);
    private final AtomicLong processingErrors = new AtomicLong(0);

    @Inject
    Tracer tracer;

    /**
     * Consumes greeting messages from Kafka.
     *
     * The trace context is automatically extracted from Kafka headers by the
     * OpenTelemetry instrumentation, creating a parent-child relationship with
     * the producer's trace. The Avro deserializer automatically retrieves the
     * schema from Apicurio Registry.
     *
     * @param record The Kafka record containing the greeting
     * @return CompletionStage for async processing
     */
    @Incoming("greetings-in")
    public CompletionStage<Void> consume(Record<String, Greeting> record) {
        messagesReceived.incrementAndGet();

        // Create a custom span for message processing
        Span span = tracer.spanBuilder("process-greeting")
                .setAttribute("kafka.key", record.key() != null ? record.key() : "null")
                .startSpan();

        try (Scope scope = span.makeCurrent()) {
            Greeting greeting = record.value();
            String currentTraceId = Span.current().getSpanContext().getTraceId();

            // Add attributes to the span
            span.setAttribute("greeting.message", greeting.getMessage());
            span.setAttribute("greeting.source", greeting.getSource());
            span.setAttribute("greeting.originalTraceId", greeting.getTraceId() != null ? greeting.getTraceId() : "unknown");

            // Format the timestamp
            String formattedTime = FORMATTER.format(Instant.ofEpochMilli(greeting.getTimestamp()));

            LOG.infof("Received greeting: '%s' from '%s' at %s (currentTraceId: %s, originalTraceId: %s)",
                    greeting.getMessage(),
                    greeting.getSource(),
                    formattedTime,
                    currentTraceId,
                    greeting.getTraceId());

            // Simulate some processing
            processGreeting(greeting, span);

            messagesProcessed.incrementAndGet();
            span.addEvent("greeting-processed");

            return java.util.concurrent.CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            processingErrors.incrementAndGet();
            span.recordException(e);
            LOG.errorf(e, "Error processing greeting: %s", e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    /**
     * Simulates greeting processing with additional spans.
     */
    private void processGreeting(Greeting greeting, Span parentSpan) {
        // Create a child span for validation
        Span validationSpan = tracer.spanBuilder("validate-greeting")
                .startSpan();

        try (Scope scope = validationSpan.makeCurrent()) {
            // Validate the greeting
            if (greeting.getMessage() == null || greeting.getMessage().isEmpty()) {
                validationSpan.setAttribute("validation.result", "failed");
                throw new IllegalArgumentException("Greeting message cannot be empty");
            }
            validationSpan.setAttribute("validation.result", "passed");
            validationSpan.addEvent("validation-complete");
        } finally {
            validationSpan.end();
        }

        // Create a child span for any business logic
        Span businessLogicSpan = tracer.spanBuilder("business-logic")
                .startSpan();

        try (Scope scope = businessLogicSpan.makeCurrent()) {
            // Simulate some business logic processing time
            simulateProcessingDelay();

            businessLogicSpan.setAttribute("greeting.length", greeting.getMessage().length());
            businessLogicSpan.addEvent("business-logic-complete");
        } finally {
            businessLogicSpan.end();
        }
    }

    /**
     * Simulates processing delay for demonstration purposes.
     */
    private void simulateProcessingDelay() {
        try {
            Thread.sleep(10 + (long) (Math.random() * 40));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Returns statistics about message consumption.
     */
    public ConsumerStats getStats() {
        return new ConsumerStats(
                messagesReceived.get(),
                messagesProcessed.get(),
                processingErrors.get()
        );
    }

    /**
     * Simple record for consumer statistics.
     */
    public record ConsumerStats(long received, long processed, long errors) {}
}
