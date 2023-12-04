package io.apicurio.registry.serde.protobuf;

import com.google.protobuf.Message;

import io.apicurio.registry.serde.AbstractSerde;

/****
 * Wraps the ProtobufKafkaSerializer and ProtobufKafkaDeserializer.
 */
public class ProtobufSerde<T extends Message> extends AbstractSerde<T> {
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public ProtobufSerde() {
        super(new ProtobufKafkaSerializer<T>(), new ProtobufKafkaDeserializer());
    }
}

