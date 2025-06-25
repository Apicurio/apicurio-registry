package io.apicurio.registry.serde.avro;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.client.RegistrySDK;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
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

    public AvroKafkaSerializer(RegistrySDK sdk) {
        super(new AvroSerializer<>(sdk));
    }

    public AvroKafkaSerializer(SchemaResolver<Schema, U> schemaResolver) {
        super(new AvroSerializer<>(schemaResolver));
    }

    public AvroKafkaSerializer(RegistrySDK sdk, SchemaResolver<Schema, U> schemaResolver) {
        super(new AvroSerializer<>(sdk, schemaResolver));
    }

    public AvroKafkaSerializer(RegistrySDK sdk, ArtifactReferenceResolverStrategy<Schema, U> strategy,
            SchemaResolver<Schema, U> schemaResolver) {
        super(new AvroSerializer<>(sdk, strategy, schemaResolver));
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        super.configure(configs, isKey);
        avroHeaders = new AvroSerdeHeaders(isKey);
    }

    /**
     * @see KafkaSerializer#serializeData(org.apache.kafka.common.header.Headers,
     *      io.apicurio.registry.resolver.ParsedSchema, java.lang.Object, java.io.OutputStream)
     */
    @Override
    protected void serializeData(Headers headers, ParsedSchema<Schema> schema, U data, OutputStream out)
            throws IOException {
        if (headers != null) {
            avroHeaders.addEncodingHeader(headers,
                    ((AvroSerializer<U>) delegatedSerializer).getEncoding().name());
        }

        super.serializeData(headers, schema, data, out);
    }
}
