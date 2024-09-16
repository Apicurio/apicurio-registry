package io.apicurio.registry.serde.avro;

import io.apicurio.registry.serde.config.SerdeConfig;
import org.apache.pulsar.functions.api.SerDe;

/**
 * Wraps the AvroKafkaSerializer and AvroKafkaDeserializer.
 */
public class AvroPulsarSerde<T> implements SerDe<T>, AutoCloseable {

    final private AvroPulsarSerializer<T> serializer;
    final private AvroPulsarDeserializer<T> deserializer;

    public AvroPulsarSerde() {
        this.serializer = new AvroPulsarSerializer<>();
        this.deserializer = new AvroPulsarDeserializer<>();
    }

    public AvroPulsarSerde(AvroPulsarSerializer<T> serializer, AvroPulsarDeserializer<T> deserializer) {
        this.serializer = serializer;
        this.deserializer = deserializer;
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
        return deserializer.deserializeData(null, input);
    }

    @Override
    public byte[] serialize(T input) {
        return serializer.serializeData(null, input);
    }
}
