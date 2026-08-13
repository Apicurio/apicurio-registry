package io.apicurio.registry.serde.protobuf;

import com.google.protobuf.Message;

import org.apache.kafka.common.header.Headers;

import java.util.HashMap;
import java.util.Map;

import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.client.RegistryClientFacade;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.serde.kafka.KafkaDeserializer;
import io.apicurio.registry.utils.protobuf.schema.ProtobufSchema;

public class ProtobufKafkaDeserializer<U extends Message> extends KafkaDeserializer<ProtobufSchema, U> {

    private ProtobufSerdeHeaders serdeHeaders;

    public ProtobufKafkaDeserializer() {
        super(ProtobufDeserializer::new);
    }

    /**
     * @deprecated inject dependencies via the configuration map instead
     * ({@code SerdeConfig.REGISTRY_CLIENT_FACADE}).
     */
    @Deprecated
    public ProtobufKafkaDeserializer(RegistryClientFacade clientFacade) {
        super(() -> new ProtobufDeserializer<>(clientFacade));
    }

    /**
     * @deprecated inject dependencies via the configuration map instead
     * ({@code SerdeConfig.SCHEMA_RESOLVER}).
     */
    @Deprecated
    public ProtobufKafkaDeserializer(SchemaResolver<ProtobufSchema, U> schemaResolver) {
        super(() -> new ProtobufDeserializer<>(schemaResolver));
    }

    /**
     * @deprecated inject dependencies via the configuration map instead
     * ({@code SerdeConfig.REGISTRY_CLIENT_FACADE}, {@code SerdeConfig.SCHEMA_RESOLVER}).
     */
    @Deprecated
    public ProtobufKafkaDeserializer(RegistryClientFacade clientFacade,
                                     SchemaResolver<ProtobufSchema, U> schemaResolver) {
        super(() -> new ProtobufDeserializer<>(clientFacade, schemaResolver));
    }

    /**
     * @deprecated inject dependencies via the configuration map instead
     * ({@code SerdeConfig.REGISTRY_CLIENT_FACADE}, {@code SerdeConfig.ARTIFACT_RESOLVER_STRATEGY},
     * {@code SerdeConfig.SCHEMA_RESOLVER}).
     */
    @Deprecated
    public ProtobufKafkaDeserializer(RegistryClientFacade clientFacade,
                                     ArtifactReferenceResolverStrategy<ProtobufSchema, U> strategy,
                                     SchemaResolver<ProtobufSchema, U> schemaResolver) {
        super(() -> new ProtobufDeserializer<>(clientFacade, schemaResolver, strategy));
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
