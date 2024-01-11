package io.apicurio.registry.serde.generic;

import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.SchemaResolverConfig;
import io.apicurio.registry.serde.IdHandler;
import io.apicurio.registry.serde.Legacy4ByteIdHandler;
import io.apicurio.registry.serde.SerdeConfig;
import io.apicurio.registry.serde.headers.HeadersHandler;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

import static io.apicurio.registry.serde.SerdeConfig.*;
import static io.apicurio.registry.serde.generic.Utils.castOr;

public abstract class GenericSerDe<SCHEMA, DATA> implements Configurable, Closeable {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    public static final byte MAGIC_BYTE = 0x0;

    @Getter
    @Setter
    protected IdHandler idHandler;

    @Getter
    @Setter
    protected HeadersHandler headersHandler;

    @Getter
    @Setter
    @NonNull // For the setter
    protected SchemaResolver<SCHEMA, DATA> schemaResolver;

    protected GenericSerDeDatatype<SCHEMA, DATA> serDeDatatype;

    protected GenericSerDeConfig serDeConfig;


    public GenericSerDe(GenericSerDeDatatype<SCHEMA, DATA> serDeDatatype, SchemaResolver<SCHEMA, DATA> schemaResolver) {
        this.serDeDatatype = serDeDatatype;
        this.schemaResolver = schemaResolver;
    }


    @Override
    public void configure(GenericConfig config) {
        Objects.requireNonNull(config);
        serDeConfig = castOr(config, GenericSerDeConfig.class, () -> new GenericSerDeConfig(config));

        if (schemaResolver == null) {
            if (!config.hasConfig(SCHEMA_RESOLVER)) {
                config.setConfig(SCHEMA_RESOLVER, SCHEMA_RESOLVER_DEFAULT);
            }
            schemaResolver = (SchemaResolver<SCHEMA, DATA>) serDeConfig.getSchemaResolver();
        }

        // enforce default artifactResolverStrategy for kafka apps // TODO
        if (!config.hasConfig(SchemaResolverConfig.ARTIFACT_RESOLVER_STRATEGY)) {
            config.setConfig(SchemaResolverConfig.ARTIFACT_RESOLVER_STRATEGY, SerdeConfig.ARTIFACT_RESOLVER_STRATEGY_DEFAULT);
        }

        schemaResolver.configure(config.getRawConfig(), serDeDatatype.getSchemaParser());

        if (serDeConfig.enableConfluentIdHandler()) {
            if (idHandler != null && !(idHandler instanceof Legacy4ByteIdHandler)) {
                log.warn("Conflicting ID handler configuration. Using Legacy4ByteIdHandler (Confluent ID handler).");
            }
            idHandler = new Legacy4ByteIdHandler();
        } else {
            if (idHandler == null) {
                if (!config.hasConfig(ID_HANDLER)) {
                    config.setConfig(ID_HANDLER, ID_HANDLER_DEFAULT);
                }
                idHandler = serDeConfig.getIDHandler();
            }
        }
        idHandler.configure(config.getRawConfig(), serDeConfig.isKey());

        if (serDeConfig.enableHeaders()) {
            headersHandler = serDeConfig.getHeadersHandler();
            headersHandler.configure(config.getRawConfig(), serDeConfig.isKey());
        }
    }


    @Override
    public void close() {
        try {
            schemaResolver.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
