package io.apicurio.registry.serde;

import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.resolver.utils.Utils;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.serde.config.SerdeConfig;
import io.apicurio.registry.serde.config.SerdeDeserializerConfig;
import io.apicurio.registry.serde.fallback.DefaultFallbackArtifactProvider;
import io.apicurio.registry.serde.fallback.FallbackArtifactProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Common class for both serializer and deserializer.
 */
public abstract class AbstractSerDe<T, U> extends SchemaResolverConfigurer<T, U> implements AutoCloseable {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected FallbackArtifactProvider fallbackArtifactProvider;

    public static final byte MAGIC_BYTE = 0x0;

    protected boolean key; // do we handle key or value with this ser/de?

    protected boolean isKey() {
        return key;
    }

    protected IdHandler idHandler;

    public AbstractSerDe() {
        super();
    }

    public AbstractSerDe(RegistryClient client) {
        super(client);
    }

    public AbstractSerDe(SchemaResolver<T, U> schemaResolver) {
        super(schemaResolver);
    }

    public AbstractSerDe(RegistryClient client, SchemaResolver<T, U> schemaResolver) {
        super(client, schemaResolver);
    }

    public AbstractSerDe(RegistryClient client, ArtifactReferenceResolverStrategy<T, U> strategy,
            SchemaResolver<T, U> schemaResolver) {
        super(client, strategy, schemaResolver);
    }

    public void configure(SerdeConfig config, boolean isKey) {
        super.configure(config.originals(), isKey, schemaParser());

        configureSerialization(config, isKey);
        configureDeserialization(config, isKey);
    }

    private void configureSerialization(SerdeConfig config, boolean isKey) {
        // First we configure the serialization part.
        key = isKey;
        if (idHandler == null) {
            Object idh = config.getIdHandler();
            Utils.instantiate(IdHandler.class, idh, this::setIdHandler);
        }
        idHandler.configure(config.originals(), isKey);
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

    public IdHandler getIdHandler() {
        return idHandler;
    }

    public void setIdHandler(IdHandler idHandler) {
        this.idHandler = Objects.requireNonNull(idHandler);
    }

    /**
     * @param fallbackArtifactProvider the fallbackArtifactProvider to set
     */
    public void setFallbackArtifactProvider(FallbackArtifactProvider fallbackArtifactProvider) {
        this.fallbackArtifactProvider = fallbackArtifactProvider;
    }

    public void reset() {
        schemaResolver.reset();
    }

    public static ByteBuffer getByteBuffer(byte[] payload) {
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        if (buffer.get() != MAGIC_BYTE) {
            throw new IllegalStateException("Unknown magic byte!");
        }
        return buffer;
    }

    @Override
    public void close() {
        try {
            this.schemaResolver.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void as4ByteId() {
        this.idHandler = new Default4ByteIdHandler();
    }
}