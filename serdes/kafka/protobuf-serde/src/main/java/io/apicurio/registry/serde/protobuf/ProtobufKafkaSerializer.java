package io.apicurio.registry.serde.protobuf;

import com.google.protobuf.Message;

import org.apache.kafka.common.header.Headers;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.serde.kafka.KafkaSerializer;
import io.apicurio.registry.utils.protobuf.schema.ProtobufSchema;

public class ProtobufKafkaSerializer<U extends Message> extends KafkaSerializer<ProtobufSchema, U> {

    private ProtobufSerdeHeaders serdeHeaders;

    public ProtobufKafkaSerializer() {
        super(ProtobufSerializer::new);
    }

    @Override
    protected void initializeHeaders(Map<String, ?> configs, boolean isKey) {
        serdeHeaders = new ProtobufSerdeHeaders(new HashMap<>(configs), isKey);
    }

    /**
     * @see KafkaSerializer#serializeData(org.apache.kafka.common.header.Headers,
     *      io.apicurio.registry.resolver.ParsedSchema, java.lang.Object, java.io.OutputStream)
     */
    @Override
    protected void serializeData(Headers headers, ParsedSchema<ProtobufSchema> schema, U data,
            OutputStream out) throws IOException {
        if (headers != null) {
            serdeHeaders.addMessageTypeHeader(headers, data.getClass().getName());
            serdeHeaders.addProtobufTypeNameHeader(headers, data.getDescriptorForType().getName());
        } else {
            ((ProtobufSerializer<U>) delegatedSerializer).setWriteRef(false);
        }

        delegatedSerializer.serializeData(schema, data, out);
    }
}
