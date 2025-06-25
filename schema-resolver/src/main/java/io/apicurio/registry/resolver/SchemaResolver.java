package io.apicurio.registry.resolver;

import io.apicurio.registry.resolver.client.RegistrySDK;
import io.apicurio.registry.resolver.data.Record;
import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceImpl;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;

import java.io.Closeable;
import java.util.Map;

public interface SchemaResolver<SCHEMA, DATA> extends Closeable {

    /**
     * Configure, if supported.
     */
    default void configure(Map<String, ?> configs, SchemaParser<SCHEMA, DATA> schemaMapper) {
    }

    public void setSDK(RegistrySDK sdk);

    public void setArtifactResolverStrategy(
            ArtifactReferenceResolverStrategy<SCHEMA, DATA> artifactResolverStrategy);

    public SchemaParser<SCHEMA, DATA> getSchemaParser();

    /**
     * Used to register or to lookup a schema in the registry
     * 
     * @param data, record containing metadata about it that can be used by the resolver to lookup a schema in
     *            the registry
     * @return SchemaLookupResult
     */
    public SchemaLookupResult<SCHEMA> resolveSchema(Record<DATA> data);

    /**
     * The schema resolver may use different pieces of information from the {@link ArtifactReferenceImpl}
     * depending on the configuration of the schema resolver.
     * 
     * @param reference
     * @return SchemaLookupResult
     */
    public SchemaLookupResult<SCHEMA> resolveSchemaByArtifactReference(ArtifactReference reference);

    /**
     * Hard reset cache
     */
    public void reset();

}
