package io.apicurio.registry.serde.avro;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.serde.AbstractKafkaSerializer;
import org.apache.avro.Schema;
import org.apache.kafka.common.header.Headers;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

public class AvroKafkaSerializer<U> extends AbstractKafkaSerializer<Schema, U> {

    AvroSerializer<U> avroSerializer;

    private AvroSerdeHeaders avroHeaders;

    public AvroKafkaSerializer() {
        super();
    }

    public AvroKafkaSerializer(RegistryClient client) {
        super(client);
    }

    public AvroKafkaSerializer(SchemaResolver<Schema, U> schemaResolver) {
        super(schemaResolver);
    }

    public AvroKafkaSerializer(RegistryClient client,
            ArtifactReferenceResolverStrategy<Schema, U> artifactResolverStrategy,
            SchemaResolver<Schema, U> schemaResolver) {
        super(client, artifactResolverStrategy, schemaResolver);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        avroHeaders = new AvroSerdeHeaders(isKey);
        AvroSerdeConfig avroSerdeConfig = new AvroSerdeConfig(configs);
        this.avroSerializer = new AvroSerializer<>();
        if (getSchemaResolver() != null) {
            this.avroSerializer.setSchemaResolver(getSchemaResolver());
        }
        avroSerializer.configure(avroSerdeConfig, isKey);

        super.configure(configs, isKey);
    }

    /**
     * @see io.apicurio.registry.serde.AbstractKafkaSerDe#schemaParser()
     */
    @Override
    public SchemaParser<Schema, U> schemaParser() {
        return avroSerializer.schemaParser();
    }

    /**
     * @see io.apicurio.registry.serde.AbstractKafkaSerializer#serializeData(io.apicurio.registry.resolver.ParsedSchema,
     *      java.lang.Object, java.io.OutputStream)
     */
    @SuppressWarnings("unchecked")
    @Override
    protected void serializeData(ParsedSchema<Schema> schema, U data, OutputStream out) throws IOException {
        avroSerializer.serializeData(schema, data, out);
    }

    /**
     * @see io.apicurio.registry.serde.AbstractKafkaSerializer#serializeData(org.apache.kafka.common.header.Headers,
     *      io.apicurio.registry.resolver.ParsedSchema, java.lang.Object, java.io.OutputStream)
     */
    @Override
    protected void serializeData(Headers headers, ParsedSchema<Schema> schema, U data, OutputStream out)
            throws IOException {
        if (headers != null) {
            avroHeaders.addEncodingHeader(headers, avroSerializer.getEncoding().name());
        }
        serializeData(schema, data, out);
    }
}
