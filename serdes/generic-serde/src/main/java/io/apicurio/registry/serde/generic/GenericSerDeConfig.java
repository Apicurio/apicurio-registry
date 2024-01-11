package io.apicurio.registry.serde.generic;

import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.serde.IdHandler;
import io.apicurio.registry.serde.SerdeConfig;
import io.apicurio.registry.serde.fallback.FallbackArtifactProvider;
import io.apicurio.registry.serde.headers.HeadersHandler;

import java.util.Map;

import static io.apicurio.registry.serde.SerdeConfig.*;

public class GenericSerDeConfig extends GenericConfig {


    public GenericSerDeConfig(Map<String, Object> rawConfig) {
        super(rawConfig);
    }


    public GenericSerDeConfig(GenericConfig config) {
        super(config.getRawConfig());
    }


    public SchemaResolver getSchemaResolver() {
        return getInstance(SerdeConfig.SCHEMA_RESOLVER, SchemaResolver.class);
    }


    public boolean enableConfluentIdHandler() {
        return getBooleanOr(ENABLE_CONFLUENT_ID_HANDLER, false);
    }


    public boolean isKey() {
        return getBooleanOr(IS_KEY, false);
    }


    public boolean enableHeaders() {
        return getBooleanOr(ENABLE_HEADERS, false);
    }


    public HeadersHandler getHeadersHandler() {
        return getInstance(HEADERS_HANDLER, HeadersHandler.class);
    }


    public IdHandler getIDHandler() {
        return getInstance(ID_HANDLER, IdHandler.class);
    }


    public FallbackArtifactProvider getFallbackArtifactProvider() {
        return getInstance(FALLBACK_ARTIFACT_PROVIDER, FallbackArtifactProvider.class);
    }
}
