package io.apicurio.registry.serde;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaLookupResult;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.rest.client.RegistryClient;

import java.nio.ByteBuffer;

public abstract class AbstractDeserializer<T, U> extends AbstractSerDe<T, U> {

    public AbstractDeserializer() {
        super();
    }

    public AbstractDeserializer(RegistryClient client) {
        super(client);
    }

    public AbstractDeserializer(SchemaResolver<T, U> schemaResolver) {
        super(schemaResolver);
    }

    public AbstractDeserializer(RegistryClient client, SchemaResolver<T, U> schemaResolver) {
        super(client, schemaResolver);
    }

    public AbstractDeserializer(RegistryClient client, ArtifactReferenceResolverStrategy<T, U> strategy,
            SchemaResolver<T, U> schemaResolver) {
        super(client, strategy, schemaResolver);
    }

    public U deserializeData(String topic, byte[] data) {
        if (data == null) {
            return null;
        }

        ByteBuffer buffer = getByteBuffer(data);
        ArtifactReference artifactReference = getIdHandler().readId(buffer);

        SchemaLookupResult<T> schema = resolve(topic, data, artifactReference);

        int length = buffer.limit() - 1 - getIdHandler().idSize();
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
            return getSchemaResolver().resolveSchemaByArtifactReference(artifactReference);
        } catch (RuntimeException e) {
            if (fallbackArtifactProvider == null) {
                throw e;
            } else {
                try {
                    ArtifactReference fallbackReference = fallbackArtifactProvider.get(topic, data);
                    return getSchemaResolver().resolveSchemaByArtifactReference(fallbackReference);
                } catch (RuntimeException fe) {
                    fe.addSuppressed(e);
                    throw fe;
                }
            }
        }
    }
}
