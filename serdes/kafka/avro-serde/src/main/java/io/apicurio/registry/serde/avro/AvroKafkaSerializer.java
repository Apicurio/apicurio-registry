package io.apicurio.registry.serde.avro;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.serde.AbstractSerializer;
import io.apicurio.registry.serde.KafkaSerializer;
import org.apache.avro.Schema;
import org.apache.kafka.common.header.Headers;

import java.io.IOException;
import java.io.OutputStream;
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

    public AvroKafkaSerializer(AbstractSerializer<Schema, U> delegatedSerializer) {
        super(delegatedSerializer);
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        super.configure(configs, isKey);
        avroHeaders = new AvroSerdeHeaders(isKey);
    }

    /**
     * @see KafkaSerializer#serializeData(org.apache.kafka.common.header.Headers,
     *         io.apicurio.registry.resolver.ParsedSchema, java.lang.Object, java.io.OutputStream)
     */
    @Override
    protected void serializeData(Headers headers, ParsedSchema<Schema> schema, U data, OutputStream out)
            throws IOException {
        if (headers != null) {
            avroHeaders.addEncodingHeader(headers,
                    ((AvroSerializer<U>) delegatedSerializer).getEncoding().name());
        }

        delegatedSerializer.serializeData(schema, data, out);
    }
}
