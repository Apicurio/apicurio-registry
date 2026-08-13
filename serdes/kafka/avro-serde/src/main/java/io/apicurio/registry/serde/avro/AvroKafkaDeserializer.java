package io.apicurio.registry.serde.avro;

import org.apache.avro.Schema;
import org.apache.kafka.common.header.Headers;

import java.util.Map;

import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.client.RegistryClientFacade;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.serde.kafka.KafkaDeserializer;

public class AvroKafkaDeserializer<U> extends KafkaDeserializer<Schema, U> {

    private AvroSerdeHeaders avroHeaders;

    public AvroKafkaDeserializer() {
        super(AvroDeserializer::new);
    }

    /**
     * @deprecated inject dependencies via the configuration map instead
     * ({@code SerdeConfig.REGISTRY_CLIENT_FACADE}).
     * Will be removed in a future release.
     */
    @Deprecated(since = "3.3.2", forRemoval = true)
    public AvroKafkaDeserializer(RegistryClientFacade clientFacade) {
        super(() -> new AvroDeserializer<>(clientFacade));
    }

    /**
     * @deprecated inject dependencies via the configuration map instead
     * ({@code SerdeConfig.SCHEMA_RESOLVER}).
     * Will be removed in a future release.
     */
    @Deprecated(since = "3.3.2", forRemoval = true)
    public AvroKafkaDeserializer(SchemaResolver<Schema, U> schemaResolver) {
        super(() -> new AvroDeserializer<>(schemaResolver));
    }

    /**
     * @deprecated inject dependencies via the configuration map instead
     * ({@code SerdeConfig.REGISTRY_CLIENT_FACADE}, {@code SerdeConfig.SCHEMA_RESOLVER}).
     * Will be removed in a future release.
     */
    @Deprecated(since = "3.3.2", forRemoval = true)
    public AvroKafkaDeserializer(RegistryClientFacade clientFacade,
                                 SchemaResolver<Schema, U> schemaResolver) {
        super(() -> new AvroDeserializer<>(clientFacade, schemaResolver));
    }

    /**
     * @deprecated inject dependencies via the configuration map instead
     * ({@code SerdeConfig.REGISTRY_CLIENT_FACADE}, {@code SerdeConfig.ARTIFACT_RESOLVER_STRATEGY},
     * {@code SerdeConfig.SCHEMA_RESOLVER}).
     * Will be removed in a future release.
     */
    @Deprecated(since = "3.3.2", forRemoval = true)
    public AvroKafkaDeserializer(RegistryClientFacade clientFacade,
                                 ArtifactReferenceResolverStrategy<Schema, U> strategy, SchemaResolver<Schema, U> schemaResolver) {
        super(() -> new AvroDeserializer<>(clientFacade, strategy, schemaResolver));
    }

    @Override
    protected void initializeHeaders(Map<String, ?> configs, boolean isKey) {
        avroHeaders = new AvroSerdeHeaders(isKey);
    }

    @Override
    public U deserialize(String topic, Headers headers, byte[] data) {
        AvroEncoding encoding = null;
        if (headers != null) {
            String encodingHeader = avroHeaders.getEncoding(headers);
            if (encodingHeader != null) {
                encoding = AvroEncoding.valueOf(encodingHeader);
            }
        }
        if (encoding != null) {
            ((AvroDeserializer<U>) delegatedDeserializer).setEncoding(encoding);
        }

        return super.deserialize(topic, headers, data);
    }
}
