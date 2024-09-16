package io.apicurio.registry.serde.protobuf;

import com.google.protobuf.Message;
import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.serde.AbstractDeserializer;
import io.apicurio.registry.serde.AbstractKafkaDeserializer;
import io.apicurio.registry.utils.protobuf.schema.ProtobufSchema;
import org.apache.kafka.common.header.Headers;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class ProtobufKafkaDeserializer<U extends Message>
        extends AbstractKafkaDeserializer<ProtobufSchema, U> {

    private ProtobufSerdeHeaders serdeHeaders;

    private ProtobufDeserializer<U> protobufDeserializer;

    public ProtobufKafkaDeserializer() {
        super();
    }

    public ProtobufKafkaDeserializer(RegistryClient client,
            SchemaResolver<ProtobufSchema, U> schemaResolver) {
        super(client, schemaResolver);
    }

    public ProtobufKafkaDeserializer(RegistryClient client) {
        super(client);
    }

    public ProtobufKafkaDeserializer(SchemaResolver<ProtobufSchema, U> schemaResolver) {
        super(schemaResolver);
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        serdeHeaders = new ProtobufSerdeHeaders(new HashMap<>(configs), isKey);
        ProtobufDeserializerConfig protobufDeserializerConfig = new ProtobufDeserializerConfig(configs,
                isKey);
        this.protobufDeserializer = new ProtobufDeserializer<>();
        this.protobufDeserializer.setSchemaResolver(getSchemaResolver());
        protobufDeserializer.configure(protobufDeserializerConfig, isKey);

        super.configure(configs, isKey);
    }

    /**
     * @see io.apicurio.registry.serde.AbstractKafkaSerDe#schemaParser()
     */
    @Override
    public SchemaParser<ProtobufSchema, U> schemaParser() {
        return protobufDeserializer.schemaParser();
    }

    /**
     * @see AbstractDeserializer#readData(io.apicurio.registry.resolver.ParsedSchema, java.nio.ByteBuffer,
     *      int, int)
     */
    @Override
    protected U readData(ParsedSchema<ProtobufSchema> schema, ByteBuffer buffer, int start, int length) {
        return protobufDeserializer.readData(schema, buffer, start, length);
    }

    @Override
    public U deserialize(String topic, Headers headers, byte[] data) {
        String messageTypeHeader = serdeHeaders.getMessageType(headers);

        if (messageTypeHeader != null) {
            protobufDeserializer.setMessageTypeName(messageTypeHeader);
        }

        return protobufDeserializer.deserializeData(topic, data);
    }
}
