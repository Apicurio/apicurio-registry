package io.apicurio.registry.resolver.strategy;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.data.Metadata;
import io.apicurio.registry.resolver.data.Record;

/**
 * {@link ArtifactReferenceResolverStrategy} implementation that simply returns
 * {@link Metadata#artifactReference()} from the given {@link Record}
 */
public class DynamicArtifactReferenceResolverStrategy<SCHEMA, DATA>
        implements ArtifactReferenceResolverStrategy<SCHEMA, DATA> {

    /**
     * @see io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy#artifactReference(io.apicurio.registry.resolver.data.Record,
     *      io.apicurio.registry.resolver.ParsedSchema)
     */
    @Override
    public ArtifactReference artifactReference(Record<DATA> data, ParsedSchema<SCHEMA> parsedSchema) {
        ArtifactReference reference = data.metadata().artifactReference();
        if (reference == null) {
            throw new IllegalStateException(
                    "Wrong configuration. Missing metadata.artifactReference in Record, it's required by "
                            + this.getClass().getName());
        }
        return reference;
    }

    /**
     * @see io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy#loadSchema()
     */
    @Override
    public boolean loadSchema() {
        return false;
    }

}
