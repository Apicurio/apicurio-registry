package io.apicurio.registry.serde.kafka;

import io.apicurio.registry.contracts.rules.RuleViolation;
import io.apicurio.registry.serde.DeadLetterQueue;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Sends the records that fail a contract rule with {@code onFailure: DLQ} to a Kafka topic, with a
 * producer created from the serializer's or deserializer's own client configuration.
 */
public class KafkaDeadLetterQueue implements DeadLetterQueue, AutoCloseable {

    /**
     * Header with the topic the record was written to or read from.
     */
    public static final String SOURCE_TOPIC_HEADER = "apicurio.contract-rules.source-topic";

    /**
     * Header with the failed rules, as {@code rule: message} entries separated by {@code ; }.
     */
    public static final String VIOLATIONS_HEADER = "apicurio.contract-rules.violations";

    private final String topic;
    private final Supplier<Producer<byte[], byte[]>> producerFactory;
    private Producer<byte[], byte[]> producer;

    public KafkaDeadLetterQueue(String topic, Map<String, ?> clientConfig) {
        this(topic, () -> new KafkaProducer<>(producerConfig(clientConfig)));
    }

    public KafkaDeadLetterQueue(String topic, Supplier<Producer<byte[], byte[]>> producerFactory) {
        this.topic = topic;
        this.producerFactory = producerFactory;
    }

    /**
     * The client configuration (bootstrap servers, security settings) with byte array serializers.
     */
    static Map<String, Object> producerConfig(Map<String, ?> clientConfig) {
        Map<String, Object> config = new HashMap<>(clientConfig);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        return config;
    }

    @Override
    public String topic() {
        return topic;
    }

    @Override
    public synchronized void send(String sourceTopic, byte[] payload, List<RuleViolation> violations) {
        if (producer == null) {
            producer = producerFactory.get();
        }
        ProducerRecord<byte[], byte[]> record = new ProducerRecord<>(topic, payload);
        if (sourceTopic != null) {
            record.headers().add(SOURCE_TOPIC_HEADER, sourceTopic.getBytes(StandardCharsets.UTF_8));
        }
        String causes = violations.stream().map(v -> v.getRuleName() + ": " + v.getMessage())
                .collect(Collectors.joining("; "));
        record.headers().add(VIOLATIONS_HEADER, causes.getBytes(StandardCharsets.UTF_8));
        try {
            producer.send(record).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while sending a record to the dead letter queue topic " + topic, e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Could not send a record to the dead letter queue topic " + topic,
                    e.getCause());
        }
    }

    @Override
    public synchronized void close() {
        if (producer != null) {
            producer.close();
            producer = null;
        }
    }
}
