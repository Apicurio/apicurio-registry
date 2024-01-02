package io.apicurio.registry.serde.avro.strategy;

import org.apache.avro.Schema;
import org.apache.kafka.common.errors.SerializationException;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.data.Record;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.resolver.strategy.ArtifactReference;

public class RecordIdStrategy implements ArtifactReferenceResolverStrategy<Schema, Object> {

    /**
     * @see io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy#artifactReference(io.apicurio.registry.resolver.data.Record, io.apicurio.registry.resolver.ParsedSchema)
     */
    @Override
    public ArtifactReference artifactReference(Record<Object> data, ParsedSchema<Schema> parsedSchema) {
        Schema schema = parsedSchema.getParsedSchema();
        if (schema != null && (schema.getType() == Schema.Type.RECORD || schema.getType() == Schema.Type.ENUM)) {
            return ArtifactReference.builder()
                    .groupId(schema.getNamespace())
                    .artifactId(schema.getName())
                    .build();
        }
        throw new SerializationException("The message must only be an Avro record schema!");
    }


}
