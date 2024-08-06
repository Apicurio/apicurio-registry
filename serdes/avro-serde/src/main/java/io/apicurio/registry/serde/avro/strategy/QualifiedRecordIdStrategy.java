package io.apicurio.registry.serde.avro.strategy;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.data.Record;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.serde.strategy.ArtifactReference;
import org.apache.avro.Schema;
import org.apache.kafka.common.errors.SerializationException;

public class QualifiedRecordIdStrategy implements ArtifactReferenceResolverStrategy<Schema, Object> {

    /**
     * @see io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy#artifactReference(io.apicurio.registry.resolver.data.Record,
     *      io.apicurio.registry.resolver.ParsedSchema)
     */
    @Override
    public io.apicurio.registry.resolver.strategy.ArtifactReference artifactReference(Record<Object> data,
            ParsedSchema<Schema> parsedSchema) {
        if (parsedSchema != null && parsedSchema.getParsedSchema() != null
                && (parsedSchema.getParsedSchema().getType() == Schema.Type.RECORD
                        || parsedSchema.getParsedSchema().getType() == Schema.Type.ENUM)) {
            return ArtifactReference.builder().groupId(null)
                    .artifactId(parsedSchema.getParsedSchema().getFullName()).build();
        }
        throw new SerializationException("The message must only be an Avro record schema!");
    }

}
