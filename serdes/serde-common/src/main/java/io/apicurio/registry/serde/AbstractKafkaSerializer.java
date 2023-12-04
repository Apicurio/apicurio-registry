package io.apicurio.registry.serde;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaLookupResult;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.rest.client.RegistryClient;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Serializer;

import io.apicurio.registry.serde.config.BaseKafkaSerDeConfig;
import io.apicurio.registry.serde.data.KafkaSerdeRecord;
import io.apicurio.registry.serde.data.KafkaSerdeMetadata;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Map;


public abstract class AbstractKafkaSerializer<T, U> extends AbstractKafkaSerDe<T, U> implements Serializer<U> {

    public AbstractKafkaSerializer() {
        super();
    }

    public AbstractKafkaSerializer(RegistryClient client) {
        super(client);
    }

    public AbstractKafkaSerializer(SchemaResolver<T, U> schemaResolver) {
        super(schemaResolver);
    }

    public AbstractKafkaSerializer(RegistryClient client, ArtifactReferenceResolverStrategy<T, U> artifactResolverStrategy, SchemaResolver<T, U> schemaResolver) {
        super(client, schemaResolver);
        getSchemaResolver().setArtifactResolverStrategy(artifactResolverStrategy);
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        super.configure(new BaseKafkaSerDeConfig(configs), isKey);
    }

    protected abstract void serializeData(ParsedSchema<T> schema, U data, OutputStream out) throws IOException;

    protected abstract void serializeData(Headers headers, ParsedSchema<T> schema, U data, OutputStream out) throws IOException;

    @Override
    public byte[] serialize(String topic, U data) {
        return serialize(topic, null, data);
    }

    @Override
    public byte[] serialize(String topic, Headers headers, U data) {
        // just return null
        if (data == null) {
            return null;
        }
        try {

            KafkaSerdeMetadata resolverMetadata = new KafkaSerdeMetadata(topic, isKey(), headers);

            SchemaLookupResult<T> schema = getSchemaResolver().resolveSchema(new KafkaSerdeRecord<>(resolverMetadata, data));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (headersHandler != null && headers != null) {
                headersHandler.writeHeaders(headers, schema.toArtifactReference());
                serializeData(headers, schema.getParsedSchema(), data, out);
            } else {
                out.write(MAGIC_BYTE);
                getIdHandler().writeId(schema.toArtifactReference(), out);
                serializeData(schema.getParsedSchema(), data, out);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
        try {
            this.schemaResolver.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
