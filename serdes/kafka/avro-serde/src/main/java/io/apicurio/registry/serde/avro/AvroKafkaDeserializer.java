package io.apicurio.registry.serde.avro;

import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.serde.KafkaDeserializer;
import org.apache.avro.Schema;
import org.apache.kafka.common.header.Headers;

import java.util.Map;

public class AvroKafkaDeserializer<U> extends KafkaDeserializer<Schema, U> {

    private AvroSerdeHeaders avroHeaders;

    public AvroKafkaDeserializer() {
        super(new AvroDeserializer<>());
    }

    public AvroKafkaDeserializer(RegistryClient client) {
        super(new AvroDeserializer<>(client));
    }

    public AvroKafkaDeserializer(SchemaResolver<Schema, U> schemaResolver) {
        super(new AvroDeserializer<>(schemaResolver));
    }

    public AvroKafkaDeserializer(RegistryClient client, SchemaResolver<Schema, U> schemaResolver) {
        super(new AvroDeserializer<>(client, schemaResolver));
    }

    public AvroKafkaDeserializer(RegistryClient client, ArtifactReferenceResolverStrategy<Schema, U> strategy,
            SchemaResolver<Schema, U> schemaResolver) {
        super(new AvroDeserializer<>(client, strategy, schemaResolver));
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        super.configure(configs, isKey);
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
