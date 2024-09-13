package io.apicurio.registry.serde.protobuf;

import com.google.protobuf.Message;

/****
 * Wraps the ProtobufKafkaSerializer and ProtobufKafkaDeserializer.
 */
public class ProtobufSerde<T extends Message> extends AbstractSerde<T> {
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public ProtobufSerde() {
        super(new ProtobufKafkaSerializer<T>(), new ProtobufKafkaDeserializer());
    }
}
