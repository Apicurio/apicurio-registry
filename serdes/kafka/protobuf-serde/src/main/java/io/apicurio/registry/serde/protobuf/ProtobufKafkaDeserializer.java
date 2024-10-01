package io.apicurio.registry.serde.protobuf;

import com.google.protobuf.Message;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.serde.KafkaDeserializer;
import io.apicurio.registry.utils.protobuf.schema.ProtobufSchema;
import org.apache.kafka.common.header.Headers;

import java.util.HashMap;
import java.util.Map;

public class ProtobufKafkaDeserializer<U extends Message> extends KafkaDeserializer<ProtobufSchema, U> {

    private ProtobufSerdeHeaders serdeHeaders;

    public ProtobufKafkaDeserializer() {
        super(new ProtobufDeserializer<>());
    }

    public ProtobufKafkaDeserializer(RegistryClient client) {
        super(new ProtobufDeserializer<>(client));
    }

    public ProtobufKafkaDeserializer(SchemaResolver<ProtobufSchema, U> schemaResolver) {
        super(new ProtobufDeserializer<>(schemaResolver));
    }

    public ProtobufKafkaDeserializer(RegistryClient client,
            SchemaResolver<ProtobufSchema, U> schemaResolver) {
        super(new ProtobufDeserializer<>(client, schemaResolver));
    }

    public ProtobufKafkaDeserializer(RegistryClient client,
            ArtifactReferenceResolverStrategy<ProtobufSchema, U> strategy,
            SchemaResolver<ProtobufSchema, U> schemaResolver) {
        super(new ProtobufDeserializer<>(client, schemaResolver, strategy));
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        super.configure(configs, isKey);
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
