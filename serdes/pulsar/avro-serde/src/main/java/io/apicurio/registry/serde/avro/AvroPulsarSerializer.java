package io.apicurio.registry.serde.avro;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.serde.AbstractSerializer;
import io.apicurio.registry.serde.config.SerdeConfig;
import org.apache.avro.Schema;

import java.io.IOException;
import java.io.OutputStream;

public class AvroPulsarSerializer<U> extends AbstractSerializer<Schema, U> {

    AvroSerializer<U> avroSerializer;

    public AvroPulsarSerializer() {
        super();
    }

    public AvroPulsarSerializer(RegistryClient client) {
        super(client);
    }

    public AvroPulsarSerializer(SchemaResolver<Schema, U> schemaResolver) {
        super(schemaResolver);
    }

    public AvroPulsarSerializer(RegistryClient client,
            ArtifactReferenceResolverStrategy<Schema, U> artifactResolverStrategy,
            SchemaResolver<Schema, U> schemaResolver) {
        super(client, artifactResolverStrategy, schemaResolver);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void configure(SerdeConfig configs, boolean isKey) {
        AvroSerdeConfig avroSerdeConfig = new AvroSerdeConfig(configs.originals());
        this.avroSerializer = new AvroSerializer<>();
        if (getSchemaResolver() != null) {
            this.avroSerializer.setSchemaResolver(getSchemaResolver());
        }
        avroSerializer.configure(avroSerdeConfig, isKey);

        super.configure(configs, isKey);
    }

    /**
     * @see io.apicurio.registry.serde.AbstractSerDe#schemaParser()
     */
    @Override
    public SchemaParser<Schema, U> schemaParser() {
        return avroSerializer.schemaParser();
    }

    /**
     * @see io.apicurio.registry.serde.AbstractSerializer#serializeData(io.apicurio.registry.resolver.ParsedSchema,
     *      java.lang.Object, java.io.OutputStream)
     */
    @SuppressWarnings("unchecked")
    @Override
    protected void serializeData(ParsedSchema<Schema> schema, U data, OutputStream out) throws IOException {
        avroSerializer.serializeData(schema, data, out);
    }
}
