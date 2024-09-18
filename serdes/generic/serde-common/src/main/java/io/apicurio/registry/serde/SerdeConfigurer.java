package io.apicurio.registry.serde;

import io.apicurio.registry.resolver.DefaultSchemaResolver;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.SchemaResolverConfig;
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
import java.util.Map;
import java.util.Objects;

/**
 * Class that holds all the configuration options related to serialization.
 */
public class SerdeConfigurer<T, U> implements AutoCloseable {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    public static final byte MAGIC_BYTE = 0x0;

    private boolean key; // do we handle key or value with this ser/de?
    private FallbackArtifactProvider fallbackArtifactProvider;
    private IdHandler idHandler;
    private SchemaResolver<T, U> schemaResolver;

    public SerdeConfigurer() {
        super();
    }

    public SerdeConfigurer(RegistryClient client) {
        this.schemaResolver = new DefaultSchemaResolver<>();
        this.schemaResolver.setClient(client);
    }

    public SerdeConfigurer(SchemaResolver<T, U> schemaResolver) {
        this.schemaResolver = schemaResolver;
    }

    public SerdeConfigurer(RegistryClient client, SchemaResolver<T, U> schemaResolver) {
        this.schemaResolver = schemaResolver;
        this.schemaResolver.setClient(client);
    }

    public SerdeConfigurer(RegistryClient client, ArtifactReferenceResolverStrategy<T, U> strategy,
                           SchemaResolver<T, U> schemaResolver) {
        this.schemaResolver = schemaResolver;
        this.schemaResolver.setClient(client);
        this.schemaResolver.setArtifactResolverStrategy(strategy);
    }

    public void configure(SerdeConfig config, boolean isKey, SchemaParser<T, U> schemaParser) {
        configureSchemaResolver(config.originals(), isKey, schemaParser);
        configureSerialization(config, isKey);
        configureDeserialization(config, isKey);
    }

    private void configureSchemaResolver(Map<String, Object> configs, boolean isKey, SchemaParser<T, U> schemaParser) {
        Objects.requireNonNull(configs);
        Objects.requireNonNull(schemaParser);
        if (this.schemaResolver == null) {
            Object sr = configs.get(SerdeConfig.SCHEMA_RESOLVER);
            if (null == sr) {
                this.schemaResolver = new DefaultSchemaResolver<>();
            } else {
                Utils.instantiate(SchemaResolver.class, sr, this::setSchemaResolver);
            }
        }
        // enforce default artifactResolverStrategy for kafka apps
        if (!configs.containsKey(SchemaResolverConfig.ARTIFACT_RESOLVER_STRATEGY)) {
            configs.put(SchemaResolverConfig.ARTIFACT_RESOLVER_STRATEGY,
                    SerdeConfig.ARTIFACT_RESOLVER_STRATEGY_DEFAULT);
        }
        // isKey is passed via config property
        configs.put(SerdeConfig.IS_KEY, isKey);
        this.schemaResolver.configure(configs, schemaParser);
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

    public SchemaResolver<T, U> getSchemaResolver() {
        return schemaResolver;
    }

    public void setSchemaResolver(SchemaResolver<T, U> schemaResolver) {
        this.schemaResolver = Objects.requireNonNull(schemaResolver);
    }

    public boolean isKey() {
        return key;
    }

    public IdHandler getIdHandler() {
        return idHandler;
    }

    public void setIdHandler(IdHandler idHandler) {
        this.idHandler = Objects.requireNonNull(idHandler);
    }

    public FallbackArtifactProvider getFallbackArtifactProvider() {
        return this.fallbackArtifactProvider;
    }

    /**
     * @param fallbackArtifactProvider the fallbackArtifactProvider to set
     */
    public void setFallbackArtifactProvider(FallbackArtifactProvider fallbackArtifactProvider) {
        this.fallbackArtifactProvider = fallbackArtifactProvider;
    }

    public void reset() {
        this.schemaResolver.reset();
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
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}