package io.apicurio.registry.serde.protobuf;

import com.google.protobuf.Message;

import org.apache.kafka.common.header.Headers;

import java.util.HashMap;
import java.util.Map;

import io.apicurio.registry.serde.kafka.KafkaDeserializer;
import io.apicurio.registry.utils.protobuf.schema.ProtobufSchema;

public class ProtobufKafkaDeserializer<U extends Message> extends KafkaDeserializer<ProtobufSchema, U> {

    private ProtobufSerdeHeaders serdeHeaders;

    public ProtobufKafkaDeserializer() {
        super(ProtobufDeserializer::new);
    }

    @Override
    protected void initializeHeaders(Map<String, ?> configs, boolean isKey) {
        serdeHeaders = new ProtobufSerdeHeaders(new HashMap<>(configs), isKey);
    }

    @Override
    public U deserialize(String topic, Headers headers, byte[] data) {
        String messageTypeHeader = serdeHeaders.getMessageType(headers);

        if (messageTypeHeader != null) {
            ((ProtobufDeserializer<U>) delegatedDeserializer).setMessageTypeName(messageTypeHeader);
        }

        return super.deserialize(topic, headers, data);
    }
}
