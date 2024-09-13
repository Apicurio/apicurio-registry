package io.apicurio.registry.serde.protobuf;

import com.google.protobuf.Message;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Map;

/****
 * Wraps the ProtobufKafkaSerializer and ProtobufKafkaDeserializer.
 */
public class ProtobufSerde<T extends Message> implements Serde<T> {

    final private Serializer<T> serializer;
    final private Deserializer<T> deserializer;

    protected ProtobufSerde(Serializer<T> serializer, Deserializer<T> deserializer) {
        this.serializer = serializer;
        this.deserializer = deserializer;
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        serializer.configure(configs, isKey);
        deserializer.configure(configs, isKey);
    }

    @Override
    public void close() {
        serializer.close();
        deserializer.close();
    }

    @Override
    public Serializer<T> serializer() {
        return serializer;
    }

    @Override
    public Deserializer<T> deserializer() {
        return deserializer;
    }
}
