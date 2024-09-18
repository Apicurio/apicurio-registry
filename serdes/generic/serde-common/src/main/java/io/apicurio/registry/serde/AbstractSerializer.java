package io.apicurio.registry.serde;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaLookupResult;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.serde.data.SerdeMetadata;
import io.apicurio.registry.serde.data.SerdeRecord;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;

public abstract class AbstractSerializer<T, U> extends AbstractSerDe<T, U> {

    public AbstractSerializer() {
        super();
    }

    public AbstractSerializer(RegistryClient client) {
        super(client);
    }

    public AbstractSerializer(SchemaResolver<T, U> schemaResolver) {
        super(schemaResolver);
    }

    public AbstractSerializer(RegistryClient client, SchemaResolver<T, U> schemaResolver) {
        super(client, schemaResolver);
    }

    public AbstractSerializer(RegistryClient client, ArtifactReferenceResolverStrategy<T, U> strategy,
            SchemaResolver<T, U> schemaResolver) {
        super(client, strategy, schemaResolver);
    }

    public abstract void serializeData(ParsedSchema<T> schema, U data, OutputStream out) throws IOException;

    public byte[] serializeData(String topic, U data) {
        // just return null
        if (data == null) {
            return null;
        }
        try {
            SerdeMetadata resolverMetadata = new SerdeMetadata(topic, isKey());

            SchemaLookupResult<T> schema = getSchemaResolver()
                    .resolveSchema(new SerdeRecord<>(resolverMetadata, data));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(MAGIC_BYTE);
            getIdHandler().writeId(schema.toArtifactReference(), out);
            this.serializeData(schema.getParsedSchema(), data, out);

            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
