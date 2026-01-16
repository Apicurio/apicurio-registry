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
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A message store that maintains a background consumer for realistic trace continuity.
 * This pattern demonstrates how traces flow through a long-running consumer process.
 *
 * Key tracing features demonstrated:
 * - Background consumer with continuous trace extraction
 * - Span attributes for Kafka partition and offset
 * - Message processing with custom spans
 * - Statistics tracking for observability
 */
@ApplicationScoped
public class GreetingMessageStore {

    private static final Logger LOG = Logger.getLogger(GreetingMessageStore.class);
    private static final String TOPIC = "greetings";

    @Inject
    GreetingConsumer consumerFactory;

    @Inject
    Tracer tracer;

    private final ConcurrentLinkedQueue<ReceivedGreeting> messages = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong totalReceived = new AtomicLong(0);
    private final AtomicLong totalProcessed = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);

    private ExecutorService executor;
    private Consumer<String, Greeting> consumer;

    /**
     * Wrapper class to store greeting with Kafka metadata for tracing.
     */
    public static class ReceivedGreeting {
        private final Greeting greeting;
        private final int partition;
        private final long offset;
        private final String key;
        private final long receivedTimestamp;
        private final String extractedTraceId;
        private final String tenantId;

        public ReceivedGreeting(Greeting greeting, int partition, long offset, String key,
                                String extractedTraceId, String tenantId) {
            this.greeting = greeting;
            this.partition = partition;
            this.offset = offset;
            this.key = key;
            this.receivedTimestamp = System.currentTimeMillis();
            this.extractedTraceId = extractedTraceId;
            this.tenantId = tenantId;
        }

        public Greeting getGreeting() { return greeting; }
        public int getPartition() { return partition; }
        public long getOffset() { return offset; }
        public String getKey() { return key; }
        public long getReceivedTimestamp() { return receivedTimestamp; }
        public String getExtractedTraceId() { return extractedTraceId; }
        public String getTenantId() { return tenantId; }
    }

    @PostConstruct
    void init() {
        LOG.info("Initializing GreetingMessageStore with background consumer");
        startBackgroundConsumer();
    }

    @PreDestroy
    void cleanup() {
        LOG.info("Shutting down GreetingMessageStore");
        stopBackgroundConsumer();
    }

    private void startBackgroundConsumer() {
        if (running.compareAndSet(false, true)) {
            executor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "greeting-consumer-thread");
                t.setDaemon(true);
                return t;
            });
            executor.submit(this::consumeLoop);
            LOG.info("Background consumer started");
        }
    }

    private void stopBackgroundConsumer() {
        running.set(false);
        if (consumer != null) {
            try {
                consumer.wakeup();
            } catch (Exception e) {
                LOG.debug("Error waking up consumer", e);
            }
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void consumeLoop() {
        try {
            consumer = consumerFactory.createConsumer("greeting-store-group", TOPIC);
            LOG.info("Background consumer connected to topic: " + TOPIC);

            while (running.get()) {
                try {
                    ConsumerRecords<String, Greeting> records = consumer.poll(Duration.ofMillis(1000));

                    for (ConsumerRecord<String, Greeting> record : records) {
                        processRecord(record);
                    }
                } catch (org.apache.kafka.common.errors.WakeupException e) {
                    if (running.get()) {
                        LOG.warn("Consumer wakeup while still running");
                    }
                } catch (Exception e) {
                    LOG.errorf("Error in consumer loop: %s", e.getMessage());
                    totalErrors.incrementAndGet();
                }
            }
        } catch (Exception e) {
            LOG.errorf("Failed to start consumer: %s", e.getMessage());
        } finally {
            if (consumer != null) {
                try {
                    consumer.close();
                } catch (Exception e) {
                    LOG.debug("Error closing consumer", e);
                }
            }
        }
    }

    /**
     * Process a single Kafka record with detailed span attributes.
     * This demonstrates how to add Kafka-specific metadata to traces.
     */
    private void processRecord(ConsumerRecord<String, Greeting> record) {
        // The KafkaTelemetry wrapper already creates a span for us,
        // but we create a child span for processing with additional attributes
        Span processSpan = tracer.spanBuilder("process-greeting-message")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute("kafka.topic", record.topic())
                .setAttribute("kafka.partition", record.partition())
                .setAttribute("kafka.offset", record.offset())
                .setAttribute("kafka.key", record.key() != null ? record.key() : "null")
                .setAttribute("kafka.timestamp", record.timestamp())
                .startSpan();

        try (Scope scope = processSpan.makeCurrent()) {
            Greeting greeting = record.value();

            // Extract baggage (tenant ID if set by producer)
            String tenantId = Baggage.current().getEntryValue("tenant.id");
            if (tenantId != null) {
                processSpan.setAttribute("tenant.id", tenantId);
            }

            // Add greeting-specific attributes
            processSpan.setAttribute("greeting.source", greeting.getSource().toString());
            processSpan.setAttribute("greeting.original_trace_id",
                    greeting.getTraceId() != null ? greeting.getTraceId().toString() : "unknown");

            // Get the current trace ID (extracted from Kafka headers by OTel instrumentation)
            String currentTraceId = Span.current().getSpanContext().getTraceId();

            // Store the message
            ReceivedGreeting received = new ReceivedGreeting(
                    greeting,
                    record.partition(),
                    record.offset(),
                    record.key(),
                    currentTraceId,
                    tenantId
            );
            messages.offer(received);
            totalReceived.incrementAndGet();

            processSpan.addEvent("message-stored");

            LOG.infof("Stored greeting from partition %d, offset %d: '%s' (traceId: %s)",
                    record.partition(), record.offset(),
                    greeting.getMessage(), currentTraceId);

        } finally {
            processSpan.end();
        }
    }

    /**
     * Poll for the next available greeting.
     */
    public Optional<ReceivedGreeting> poll() {
        ReceivedGreeting received = messages.poll();
        if (received != null) {
            totalProcessed.incrementAndGet();
        }
        return Optional.ofNullable(received);
    }

    /**
     * Poll for multiple greetings at once.
     */
    @WithSpan("poll-multiple-greetings")
    public List<ReceivedGreeting> pollMany(int maxCount) {
        List<ReceivedGreeting> result = new ArrayList<>();
        for (int i = 0; i < maxCount; i++) {
            ReceivedGreeting received = messages.poll();
            if (received == null) break;
            result.add(received);
            totalProcessed.incrementAndGet();
        }
        Span.current().setAttribute("messages.polled", result.size());
        return result;
    }

    /**
     * Get queue size.
     */
    public int getQueueSize() {
        return messages.size();
    }

    /**
     * Get statistics about message processing.
     */
    public Stats getStats() {
        return new Stats(
                totalReceived.get(),
                totalProcessed.get(),
                totalErrors.get(),
                messages.size(),
                running.get()
        );
    }

    public static class Stats {
        public final long totalReceived;
        public final long totalProcessed;
        public final long totalErrors;
        public final int queueSize;
        public final boolean consumerRunning;

        public Stats(long totalReceived, long totalProcessed, long totalErrors,
                     int queueSize, boolean consumerRunning) {
            this.totalReceived = totalReceived;
            this.totalProcessed = totalProcessed;
            this.totalErrors = totalErrors;
            this.queueSize = queueSize;
            this.consumerRunning = consumerRunning;
        }
    }
}
