package io.apicurio.registry.serde.avro;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.client.RegistryClientFacade;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.serde.kafka.KafkaSerializer;
import org.apache.avro.Schema;
import org.apache.kafka.common.header.Headers;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

public class AvroKafkaSerializer<U> extends KafkaSerializer<Schema, U> {

    private AvroSerdeHeaders avroHeaders;

    public AvroKafkaSerializer() {
        super(AvroSerializer::new);
    }

    public AvroKafkaSerializer(RegistryClientFacade clientFacade) {
        super(() -> new AvroSerializer<>(clientFacade));
    }

    @Deprecated
    public AvroKafkaSerializer(SchemaResolver<Schema, U> schemaResolver) {
        super(() -> new AvroSerializer<>(schemaResolver));
    }

    @Deprecated
    public AvroKafkaSerializer(RegistryClientFacade clientFacade, SchemaResolver<Schema, U> schemaResolver) {
        super(() -> new AvroSerializer<>(clientFacade, schemaResolver));
    }

    @Deprecated
    public AvroKafkaSerializer(RegistryClientFacade clientFacade, ArtifactReferenceResolverStrategy<Schema, U> strategy,
                               SchemaResolver<Schema, U> schemaResolver) {
        super(() -> new AvroSerializer<>(clientFacade, strategy, schemaResolver));
    }

    @Override
    protected void initializeHeaders(Map<String, ?> configs, boolean isKey) {
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
