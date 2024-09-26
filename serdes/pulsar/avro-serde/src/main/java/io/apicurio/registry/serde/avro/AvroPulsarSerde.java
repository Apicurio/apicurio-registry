package io.apicurio.registry.serde.avro;

import io.apicurio.registry.serde.config.SerdeConfig;
import org.apache.pulsar.functions.api.SerDe;

/**
 * Wraps the AvroKafkaSerializer and AvroKafkaDeserializer.
 */
public class AvroPulsarSerde<T> implements SerDe<T>, AutoCloseable {

    final private AvroSerializer<T> serializer;
    final private AvroDeserializer<T> deserializer;

    final private String topicName;

    public AvroPulsarSerde(String topicName) {
        this.serializer = new AvroSerializer<>();
        this.deserializer = new AvroDeserializer<>();
        this.topicName = topicName;
    }

    public AvroPulsarSerde(AvroSerializer<T> serializer, AvroDeserializer<T> deserializer, String topicName) {
        this.serializer = serializer;
        this.deserializer = deserializer;
        this.topicName = topicName;
    }

    public void configure(SerdeConfig configs, boolean isKey) {
        serializer.configure(configs, isKey);
        deserializer.configure(configs, isKey);
    }

    @Override
    public void close() {
        serializer.close();
        deserializer.close();
    }

    @Override
    public T deserialize(byte[] input) {
        return deserializer.deserializeData(topicName, input);
    }

    @Override
    public byte[] serialize(T input) {
        return serializer.serializeData(topicName, input);
    }
}
