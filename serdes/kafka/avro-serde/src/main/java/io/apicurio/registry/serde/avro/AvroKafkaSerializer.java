package io.apicurio.registry.serde.avro;

import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.serde.KafkaSerializer;
import org.apache.avro.Schema;
import org.apache.kafka.common.header.Headers;

import java.util.Map;

public class AvroKafkaSerializer<U> extends KafkaSerializer<Schema, U> {

    private AvroSerdeHeaders avroHeaders;

    public AvroKafkaSerializer() {
        super(new AvroSerializer<>());
    }

    public AvroKafkaSerializer(RegistryClient client) {
        super(new AvroSerializer<>(client));
    }

    public AvroKafkaSerializer(SchemaResolver<Schema, U> schemaResolver) {
        super(new AvroSerializer<>(schemaResolver));
    }

    public AvroKafkaSerializer(RegistryClient client, SchemaResolver<Schema, U> schemaResolver) {
        super(new AvroSerializer<>(client, schemaResolver));
    }

    public AvroKafkaSerializer(RegistryClient client, ArtifactReferenceResolverStrategy<Schema, U> strategy,
            SchemaResolver<Schema, U> schemaResolver) {
        super(new AvroSerializer<>(client, strategy, schemaResolver));
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        super.configure(configs, isKey);
        avroHeaders = new AvroSerdeHeaders(isKey);
    }

    @Override
    public byte[] serialize(String topic, Headers headers, U data) {
        if (headers != null) {
            avroHeaders.addEncodingHeader(headers,
                    ((AvroSerializer<U>) delegatedSerializer).getEncoding().name());
        }

        return super.serialize(topic, headers, data);
    }
}
