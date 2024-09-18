package io.apicurio.registry.serde;

import io.apicurio.registry.resolver.DefaultSchemaResolver;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.SchemaResolverConfig;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.resolver.utils.Utils;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.serde.config.SerdeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;

/**
 * Base class for all classes that implement serialization/deserialization.
 */
public class BaseSerde<T, U> implements AutoCloseable {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    public static final byte MAGIC_BYTE = 0x0;

    protected boolean key; // do we handle key or value with this ser/de?
    protected IdHandler idHandler;
    private SchemaResolver<T, U> schemaResolver;

    public BaseSerde() {
        super();
    }

    public BaseSerde(RegistryClient client) {
        this.schemaResolver = new DefaultSchemaResolver<>();
        this.schemaResolver.setClient(client);
    }

    public BaseSerde(SchemaResolver<T, U> schemaResolver) {
        this.schemaResolver = schemaResolver;
    }

    public BaseSerde(RegistryClient client, SchemaResolver<T, U> schemaResolver) {
        this.schemaResolver = schemaResolver;
        this.schemaResolver.setClient(client);
    }

    public BaseSerde(RegistryClient client, ArtifactReferenceResolverStrategy<T, U> strategy,
            SchemaResolver<T, U> schemaResolver) {
        this.schemaResolver = schemaResolver;
        this.schemaResolver.setClient(client);
        this.schemaResolver.setArtifactResolverStrategy(strategy);
    }

    public void configure(SerdeConfig config, boolean isKey, SchemaParser<T, U> schemaParser) {
        // First we configure the serialization part.
        this.key = isKey;
        if (this.idHandler == null) {
            Object idh = config.getIdHandler();
            Utils.instantiate(IdHandler.class, idh, this::setIdHandler);
        }
        this.idHandler.configure(config.originals(), isKey);
        configureSchemaResolver(config.originals(), isKey, schemaParser);
    }

    private void configureSchemaResolver(Map<String, Object> configs, boolean isKey,
            SchemaParser<T, U> schemaParser) {
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
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}