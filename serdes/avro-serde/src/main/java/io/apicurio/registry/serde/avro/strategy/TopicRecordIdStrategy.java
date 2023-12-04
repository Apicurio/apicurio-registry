package io.apicurio.registry.serde.avro.strategy;

import org.apache.avro.Schema;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.data.Record;
import io.apicurio.registry.serde.data.KafkaSerdeRecord;
import io.apicurio.registry.resolver.strategy.ArtifactReference;


public class TopicRecordIdStrategy extends RecordIdStrategy {

    /**
     * @see io.apicurio.registry.serde.avro.strategy.RecordIdStrategy#artifactReference(io.apicurio.registry.resolver.data.Record, io.apicurio.registry.resolver.ParsedSchema)
     */
    @Override
    public ArtifactReference artifactReference(Record<Object> data, ParsedSchema<Schema> parsedSchema) {
        ArtifactReference reference = super.artifactReference(data, parsedSchema);
        KafkaSerdeRecord<Object> kdata = (KafkaSerdeRecord<Object>) data;
        return ArtifactReference.builder()
                .groupId(reference.getGroupId())
                .artifactId(kdata.metadata().getTopic() + "-" + reference.getArtifactId())
                .version(reference.getVersion())
                .build();
    }



}
