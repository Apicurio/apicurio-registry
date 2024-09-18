package io.apicurio.registry.serde;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaLookupResult;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.serde.config.SerdeConfig;

import java.nio.ByteBuffer;

import static io.apicurio.registry.serde.SerdeConfigurer.getByteBuffer;

public abstract class AbstractDeserializer<T, U> implements AutoCloseable {

    private final SerdeConfigurer<T, U> serdeConfigurer;

    public AbstractDeserializer() {
        this.serdeConfigurer = new SerdeConfigurer<>();
    }

    public AbstractDeserializer(RegistryClient client) {
        this.serdeConfigurer = new SerdeConfigurer<>(client);
    }

    public AbstractDeserializer(SchemaResolver<T, U> schemaResolver) {
        this.serdeConfigurer = new SerdeConfigurer<>(schemaResolver);
    }

    public AbstractDeserializer(RegistryClient client, SchemaResolver<T, U> schemaResolver) {
        this.serdeConfigurer = new SerdeConfigurer<>(client, schemaResolver);
    }

    public AbstractDeserializer(RegistryClient client, ArtifactReferenceResolverStrategy<T, U> strategy,
                                SchemaResolver<T, U> schemaResolver) {
        this.serdeConfigurer = new SerdeConfigurer<>(client, strategy, schemaResolver);
    }

    public SerdeConfigurer<T, U> getSerdeConfigurer() {
        return serdeConfigurer;
    }

    public void configure(SerdeConfig config, boolean isKey) {
        serdeConfigurer.configure(config, isKey, schemaParser());
    }

    public abstract SchemaParser<T, U> schemaParser();

    public U deserializeData(String topic, byte[] data) {
        if (data == null) {
            return null;
        }

        ByteBuffer buffer = getByteBuffer(data);
        ArtifactReference artifactReference = serdeConfigurer.getIdHandler().readId(buffer);

        SchemaLookupResult<T> schema = resolve(topic, data, artifactReference);

        int length = buffer.limit() - 1 - serdeConfigurer.getIdHandler().idSize();
        int start = buffer.position() + buffer.arrayOffset();

        return readData(schema.getParsedSchema(), buffer, start, length);
    }

    protected abstract U readData(ParsedSchema<T> schema, ByteBuffer buffer, int start, int length);

    protected U readData(String topic, byte[] data, ArtifactReference artifactReference) {
        SchemaLookupResult<T> schema = resolve(topic, data, artifactReference);

        ByteBuffer buffer = ByteBuffer.wrap(data);
        int length = buffer.limit();
        int start = buffer.position();

        return readData(schema.getParsedSchema(), buffer, start, length);
    }

    protected SchemaLookupResult<T> resolve(String topic, byte[] data, ArtifactReference artifactReference) {
        try {
            return serdeConfigurer.getSchemaResolver().resolveSchemaByArtifactReference(artifactReference);
        }
        catch (RuntimeException e) {
            if (serdeConfigurer.getFallbackArtifactProvider() == null) {
                throw e;
            }
            else {
                try {
                    ArtifactReference fallbackReference = serdeConfigurer.getFallbackArtifactProvider().get(topic, data);
                    return serdeConfigurer.getSchemaResolver().resolveSchemaByArtifactReference(fallbackReference);
                }
                catch (RuntimeException fe) {
                    fe.addSuppressed(e);
                    throw fe;
                }
            }
        }
    }

    @Override
    public void close() {
        this.serdeConfigurer.close();
    }
}
