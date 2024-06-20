package io.apicurio.registry.serde;

import io.apicurio.registry.resolver.DefaultSchemaResolver;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.SchemaResolverConfig;
import io.apicurio.registry.resolver.utils.Utils;
import io.apicurio.registry.rest.client.RegistryClient;

import java.util.Map;
import java.util.Objects;

/**
 * Base class for any kind of serializer/deserializer that depends on {@link SchemaResolver}
 */
public class SchemaResolverConfigurer<T, U> {

    protected SchemaResolver<T, U> schemaResolver;

    /**
     * Constructor.
     */
    public SchemaResolverConfigurer() {
        super();
    }

    public SchemaResolverConfigurer(RegistryClient client) {
        this(client, new DefaultSchemaResolver<>());
    }

    public SchemaResolverConfigurer(SchemaResolver<T, U> schemaResolver) {
        this(null, schemaResolver);
    }

    public SchemaResolverConfigurer(RegistryClient client, SchemaResolver<T, U> schemaResolver) {
        this();
        setSchemaResolver(schemaResolver);
        getSchemaResolver().setClient(client);
    }

    public SchemaResolver<T, U> getSchemaResolver() {
        return schemaResolver;
    }

    public void setSchemaResolver(SchemaResolver<T, U> schemaResolver) {
        this.schemaResolver = Objects.requireNonNull(schemaResolver);
    }

    protected void configure(Map<String, Object> configs, boolean isKey, SchemaParser<T, U> schemaParser) {
        Objects.requireNonNull(configs);
        Objects.requireNonNull(schemaParser);
        if (this.schemaResolver == null) {
            Object sr = configs.get(SerdeConfig.SCHEMA_RESOLVER);
            if (null == sr) {
                this.setSchemaResolver(new DefaultSchemaResolver<>());
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
        getSchemaResolver().configure(configs, schemaParser);
    }

}