package io.apicurio.registry.serde.strategy;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.data.Record;
import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.serde.data.SerdeRecord;

public class SimpleTopicIdStrategy<T> implements ArtifactReferenceResolverStrategy<T, Object> {

    /**
     * @see io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy#artifactReference(io.apicurio.registry.resolver.data.Record,
     *      io.apicurio.registry.resolver.ParsedSchema)
     */
    @Override
    public ArtifactReference artifactReference(Record<Object> data, ParsedSchema<T> parsedSchema) {
        SerdeRecord<Object> kdata = (SerdeRecord<Object>) data;
        return ArtifactReference.builder().groupId(null).artifactId(kdata.metadata().getTopic()).build();
    }

    /**
     * @see io.apicurio.registry.serde.strategy.ArtifactResolverStrategy#loadSchema()
     */
    @Override
    public boolean loadSchema() {
        return false;
    }

}
