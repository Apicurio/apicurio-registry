package io.apicurio.registry.serde.kafka;

import io.apicurio.registry.contracts.rules.RuleViolation;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class KafkaDeadLetterQueueTest {

    @Test
    void sendsThePayloadWithTheSourceTopicAndTheFailedRules() {
        MockProducer<byte[], byte[]> producer = new MockProducer<>(true, null, new ByteArraySerializer(),
                new ByteArraySerializer());
        KafkaDeadLetterQueue dlq = new KafkaDeadLetterQueue("orders-dlq", () -> producer);
        byte[] payload = { 0, 1, 2 };

        dlq.send("orders", payload, List.of(new RuleViolation("positive-amount", "Condition failed", "DLQ")));

        assertEquals(1, producer.history().size());
        ProducerRecord<byte[], byte[]> record = producer.history().get(0);
        assertEquals("orders-dlq", record.topic());
        assertArrayEquals(payload, record.value());
        assertEquals("orders", new String(record.headers().lastHeader(KafkaDeadLetterQueue.SOURCE_TOPIC_HEADER)
                .value(), StandardCharsets.UTF_8));
        assertEquals("positive-amount: Condition failed", new String(
                record.headers().lastHeader(KafkaDeadLetterQueue.VIOLATIONS_HEADER).value(), StandardCharsets.UTF_8));
    }

    @Test
    void producerUsesTheClientConfigurationWithByteArraySerializers() {
        Map<String, Object> config = KafkaDeadLetterQueue.producerConfig(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092",
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName()));

        assertEquals("kafka:9092", config.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG));
        assertEquals(ByteArraySerializer.class.getName(), config.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG));
        assertEquals(ByteArraySerializer.class.getName(), config.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG));
    }
}
