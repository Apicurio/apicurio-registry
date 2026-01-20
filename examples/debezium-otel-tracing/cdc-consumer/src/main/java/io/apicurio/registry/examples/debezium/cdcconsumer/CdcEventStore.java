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

package io.apicurio.registry.examples.debezium.cdcconsumer;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.kafkaclients.v2_6.KafkaTelemetry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Background consumer that receives CDC events from Debezium via Kafka.
 * Demonstrates trace context extraction from Debezium messages.
 */
@ApplicationScoped
public class CdcEventStore {

    private static final Logger LOG = Logger.getLogger(CdcEventStore.class);
    private static final String CDC_TOPIC = "dbserver1.public.orders";

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @Inject
    OpenTelemetry openTelemetry;

    @Inject
    Tracer tracer;

    private final ConcurrentLinkedQueue<CdcEvent> events = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong totalReceived = new AtomicLong(0);
    private final AtomicLong totalProcessed = new AtomicLong(0);

    private ExecutorService executor;
    private Consumer<String, String> consumer;

    /**
     * Wrapper class for CDC events with tracing metadata.
     */
    public static class CdcEvent {
        private final String key;
        private final String value;
        private final String operation;
        private final int partition;
        private final long offset;
        private final long timestamp;
        private final String extractedTraceId;

        public CdcEvent(String key, String value, String operation,
                        int partition, long offset, long timestamp, String extractedTraceId) {
            this.key = key;
            this.value = value;
            this.operation = operation;
            this.partition = partition;
            this.offset = offset;
            this.timestamp = timestamp;
            this.extractedTraceId = extractedTraceId;
        }

        public String getKey() { return key; }
        public String getValue() { return value; }
        public String getOperation() { return operation; }
        public int getPartition() { return partition; }
        public long getOffset() { return offset; }
        public long getTimestamp() { return timestamp; }
        public String getExtractedTraceId() { return extractedTraceId; }

        public Map<String, Object> toMap() {
            return Map.of(
                    "key", key != null ? key : "null",
                    "operation", operation,
                    "partition", partition,
                    "offset", offset,
                    "timestamp", timestamp,
                    "extractedTraceId", extractedTraceId != null ? extractedTraceId : "unknown"
            );
        }
    }

    @PostConstruct
    void init() {
        LOG.info("Initializing CDC Event Store with background consumer");
        startBackgroundConsumer();
    }

    @PreDestroy
    void cleanup() {
        LOG.info("Shutting down CDC Event Store");
        stopBackgroundConsumer();
    }

    private void startBackgroundConsumer() {
        if (running.compareAndSet(false, true)) {
            executor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "cdc-consumer-thread");
                t.setDaemon(true);
                return t;
            });
            executor.submit(this::consumeLoop);
            LOG.info("CDC background consumer started");
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
            consumer = createConsumer();
            consumer.subscribe(Collections.singletonList(CDC_TOPIC));
            LOG.infof("CDC consumer subscribed to topic: %s", CDC_TOPIC);

            while (running.get()) {
                try {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));

                    for (ConsumerRecord<String, String> record : records) {
                        processRecord(record);
                    }
                } catch (org.apache.kafka.common.errors.WakeupException e) {
                    if (running.get()) {
                        LOG.warn("Consumer wakeup while still running");
                    }
                } catch (Exception e) {
                    LOG.errorf("Error in CDC consumer loop: %s", e.getMessage());
                }
            }
        } catch (Exception e) {
            LOG.errorf("Failed to start CDC consumer: %s", e.getMessage());
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

    private Consumer<String, String> createConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "cdc-consumer-group");
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, UUID.randomUUID().toString());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");

        // Create instrumented consumer for trace context extraction
        KafkaTelemetry kafkaTelemetry = KafkaTelemetry.create(openTelemetry);
        KafkaConsumer<String, String> kafkaConsumer = new KafkaConsumer<>(props);
        return kafkaTelemetry.wrap(kafkaConsumer);
    }

    private void processRecord(ConsumerRecord<String, String> record) {
        Span processSpan = tracer.spanBuilder("process-cdc-event")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute("kafka.topic", record.topic())
                .setAttribute("kafka.partition", record.partition())
                .setAttribute("kafka.offset", record.offset())
                .startSpan();

        try (Scope scope = processSpan.makeCurrent()) {
            String currentTraceId = Span.current().getSpanContext().getTraceId();

            // Parse operation type from Debezium payload
            String operation = parseOperation(record.value());

            CdcEvent event = new CdcEvent(
                    record.key(),
                    record.value(),
                    operation,
                    record.partition(),
                    record.offset(),
                    record.timestamp(),
                    currentTraceId
            );

            events.offer(event);
            totalReceived.incrementAndGet();

            processSpan.setAttribute("cdc.operation", operation);
            processSpan.addEvent("cdc-event-stored");

            LOG.infof("CDC event received: operation=%s, partition=%d, offset=%d (traceId: %s)",
                    operation, record.partition(), record.offset(), currentTraceId);

        } finally {
            processSpan.end();
        }
    }

    private String parseOperation(String value) {
        // Simple parsing of Debezium operation from JSON
        if (value == null) return "unknown";
        if (value.contains("\"op\":\"c\"")) return "CREATE";
        if (value.contains("\"op\":\"u\"")) return "UPDATE";
        if (value.contains("\"op\":\"d\"")) return "DELETE";
        if (value.contains("\"op\":\"r\"")) return "READ";
        return "unknown";
    }

    public Optional<CdcEvent> poll() {
        CdcEvent event = events.poll();
        if (event != null) {
            totalProcessed.incrementAndGet();
        }
        return Optional.ofNullable(event);
    }

    public List<CdcEvent> pollMany(int maxCount) {
        java.util.ArrayList<CdcEvent> result = new java.util.ArrayList<>();
        for (int i = 0; i < maxCount; i++) {
            CdcEvent event = events.poll();
            if (event == null) break;
            result.add(event);
            totalProcessed.incrementAndGet();
        }
        return result;
    }

    public Stats getStats() {
        return new Stats(
                totalReceived.get(),
                totalProcessed.get(),
                events.size(),
                running.get()
        );
    }

    public static class Stats {
        public final long totalReceived;
        public final long totalProcessed;
        public final int queueSize;
        public final boolean consumerRunning;

        public Stats(long totalReceived, long totalProcessed, int queueSize, boolean consumerRunning) {
            this.totalReceived = totalReceived;
            this.totalProcessed = totalProcessed;
            this.queueSize = queueSize;
            this.consumerRunning = consumerRunning;
        }
    }
}
