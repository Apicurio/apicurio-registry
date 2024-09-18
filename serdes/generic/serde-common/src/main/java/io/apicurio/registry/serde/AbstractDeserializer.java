package io.apicurio.registry.serde;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaLookupResult;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.resolver.utils.Utils;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.serde.config.SerdeConfig;
import io.apicurio.registry.serde.config.SerdeDeserializerConfig;
import io.apicurio.registry.serde.fallback.DefaultFallbackArtifactProvider;
import io.apicurio.registry.serde.fallback.FallbackArtifactProvider;

import java.nio.ByteBuffer;

import static io.apicurio.registry.serde.BaseSerde.getByteBuffer;

public abstract class AbstractDeserializer<T, U> implements AutoCloseable {

    private FallbackArtifactProvider fallbackArtifactProvider;
    private final BaseSerde<T, U> baseSerde;

    public AbstractDeserializer() {
        this.baseSerde = new BaseSerde<>();
    }

    public AbstractDeserializer(RegistryClient client) {
        this.baseSerde = new BaseSerde<>(client);
    }

    public AbstractDeserializer(SchemaResolver<T, U> schemaResolver) {
        this.baseSerde = new BaseSerde<>(schemaResolver);
    }

    public AbstractDeserializer(RegistryClient client, SchemaResolver<T, U> schemaResolver) {
        this.baseSerde = new BaseSerde<>(client, schemaResolver);
    }

    public AbstractDeserializer(RegistryClient client, ArtifactReferenceResolverStrategy<T, U> strategy,
            SchemaResolver<T, U> schemaResolver) {
        this.baseSerde = new BaseSerde<>(client, strategy, schemaResolver);
    }

    public BaseSerde<T, U> getSerdeConfigurer() {
        return baseSerde;
    }

    public void configure(SerdeConfig config, boolean isKey) {
        baseSerde.configure(config, isKey, schemaParser());

        configureDeserialization(config, isKey);
    }

    private void configureDeserialization(SerdeConfig config, boolean isKey) {
        SerdeDeserializerConfig deserializerConfig = new SerdeDeserializerConfig(config.originals());

        Object fallbackProvider = deserializerConfig.getFallbackArtifactProvider();
        Utils.instantiate(FallbackArtifactProvider.class, fallbackProvider,
                this::setFallbackArtifactProvider);
        fallbackArtifactProvider.configure(config.originals(), isKey);

        if (fallbackArtifactProvider instanceof DefaultFallbackArtifactProvider) {
            if (!((DefaultFallbackArtifactProvider) fallbackArtifactProvider).isConfigured()) {
                // it's not configured, just remove it so it's not executed
                fallbackArtifactProvider = null;
            }
        }
    }

    public abstract SchemaParser<T, U> schemaParser();

    public U deserializeData(String topic, byte[] data) {
        if (data == null) {
            return null;
        }

        ByteBuffer buffer = getByteBuffer(data);
        ArtifactReference artifactReference = baseSerde.getIdHandler().readId(buffer);

        SchemaLookupResult<T> schema = resolve(topic, data, artifactReference);

        int length = buffer.limit() - 1 - baseSerde.getIdHandler().idSize();
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
            return baseSerde.getSchemaResolver().resolveSchemaByArtifactReference(artifactReference);
        } catch (RuntimeException e) {
            if (getFallbackArtifactProvider() == null) {
                throw e;
            } else {
                try {
                    ArtifactReference fallbackReference = getFallbackArtifactProvider().get(topic, data);
                    return baseSerde.getSchemaResolver().resolveSchemaByArtifactReference(fallbackReference);
                } catch (RuntimeException fe) {
                    fe.addSuppressed(e);
                    throw fe;
                }
            }
        }
    }

    public FallbackArtifactProvider getFallbackArtifactProvider() {
        return this.fallbackArtifactProvider;
    }

    public void setFallbackArtifactProvider(FallbackArtifactProvider fallbackArtifactProvider) {
        this.fallbackArtifactProvider = fallbackArtifactProvider;
    }

    @Override
    public void close() {
        this.baseSerde.close();
    }
}
