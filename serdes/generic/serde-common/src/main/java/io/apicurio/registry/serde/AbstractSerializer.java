package io.apicurio.registry.serde;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaLookupResult;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.serde.config.SerdeConfig;
import io.apicurio.registry.serde.data.SerdeMetadata;
import io.apicurio.registry.serde.data.SerdeRecord;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;

import static io.apicurio.registry.serde.SerdeConfigurer.MAGIC_BYTE;

public abstract class AbstractSerializer<T, U> implements AutoCloseable {

    private final SerdeConfigurer<T, U> serdeConfigurer;

    public AbstractSerializer() {
        this.serdeConfigurer = new SerdeConfigurer<>();
    }

    public AbstractSerializer(RegistryClient client) {
        this.serdeConfigurer = new SerdeConfigurer<>(client);
    }

    public AbstractSerializer(SchemaResolver<T, U> schemaResolver) {
        this.serdeConfigurer = new SerdeConfigurer<>(schemaResolver);
    }

    public AbstractSerializer(RegistryClient client, SchemaResolver<T, U> schemaResolver) {
        this.serdeConfigurer = new SerdeConfigurer<>(client, schemaResolver);
    }

    public AbstractSerializer(RegistryClient client, ArtifactReferenceResolverStrategy<T, U> strategy,
                              SchemaResolver<T, U> schemaResolver) {
        this.serdeConfigurer = new SerdeConfigurer<>(client, strategy, schemaResolver);
    }

    public abstract SchemaParser<T, U> schemaParser();

    public abstract void serializeData(ParsedSchema<T> schema, U data, OutputStream out) throws IOException;

    public void configure(SerdeConfig config, boolean isKey) {
        serdeConfigurer.configure(config, isKey, schemaParser());
    }

    public byte[] serializeData(String topic, U data) {
        // just return null
        if (data == null) {
            return null;
        }
        try {
            SerdeMetadata resolverMetadata = new SerdeMetadata(topic, serdeConfigurer.isKey());

            SchemaLookupResult<T> schema = serdeConfigurer.getSchemaResolver()
                    .resolveSchema(new SerdeRecord<>(resolverMetadata, data));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(MAGIC_BYTE);
            serdeConfigurer.getIdHandler().writeId(schema.toArtifactReference(), out);
            this.serializeData(schema.getParsedSchema(), data, out);

            return out.toByteArray();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public SerdeConfigurer<T, U> getSerdeConfigurer() {
        return serdeConfigurer;
    }

    @Override
    public void close() {
        this.serdeConfigurer.close();
    }
}
