package io.apicurio.registry.serde.avro.strategy;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.data.Record;
import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import org.apache.avro.Schema;

public class NatsSubjectSchemaIdStrategy implements ArtifactReferenceResolverStrategy<Schema, Object> {
    @Override
    public ArtifactReference artifactReference(Record<Object> data, ParsedSchema<Schema> parsedSchema) {
        return ArtifactReference.builder()
                .groupId(parsedSchema.getParsedSchema().getNamespace())
                .contentId(Long.valueOf(parsedSchema.getParsedSchema().hashCode()))
                .artifactId(parsedSchema.getParsedSchema().getName())
                .build();
    }
}
