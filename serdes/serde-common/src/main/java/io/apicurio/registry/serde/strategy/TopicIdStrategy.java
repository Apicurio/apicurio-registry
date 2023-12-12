package io.apicurio.registry.serde.strategy;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.data.Record;
import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.serde.data.KafkaSerdeMetadata;
import io.apicurio.registry.serde.data.KafkaSerdeRecord;

public class TopicIdStrategy<T> implements ArtifactReferenceResolverStrategy<T, Object> {

    @Override
    public ArtifactReference artifactReference(Record<Object> data, ParsedSchema<T> parsedSchema) {
        KafkaSerdeRecord<Object> kdata = (KafkaSerdeRecord<Object>) data;
        KafkaSerdeMetadata metadata = kdata.metadata();
        return ArtifactReference.builder().groupId(null)
                .artifactId(String.format("%s-%s", metadata.getTopic(), metadata.isKey() ? "key" : "value"))
                .build();
    }

    /**
     * @see io.apicurio.registry.serde.strategy.ArtifactResolverStrategy#loadSchema()
     */
    @Override
    public boolean loadSchema() {
        return false;
    }

}
