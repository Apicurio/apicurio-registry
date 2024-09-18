package io.apicurio.registry.serde.protobuf;

import com.google.protobuf.Message;
import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.serde.AbstractSerializer;
import io.apicurio.registry.serde.KafkaSerializer;
import io.apicurio.registry.utils.protobuf.schema.ProtobufSchema;
import org.apache.kafka.common.header.Headers;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

public class ProtobufKafkaSerializer<U extends Message> extends KafkaSerializer<ProtobufSchema, U> {

    private ProtobufSerdeHeaders serdeHeaders;

    public ProtobufKafkaSerializer(AbstractSerializer<ProtobufSchema, U> delegatedSerializer) {
        super(delegatedSerializer);
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        super.configure(configs, isKey);
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
