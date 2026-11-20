package io.apicurio.registry.examples.serdes;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.data.Record;
import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.serde.data.SerdeRecord;

/**
 * A custom strategy (tour step 10): every topic's schema goes to the group of the team that owns the
 * topic, here the part of the topic name before the first dot, under the rest of the name.
 * Topic {@code inventory.stock-levels} uses artifact {@code inventory/stock-levels}.
 */
public class TeamArtifactStrategy<T> implements ArtifactReferenceResolverStrategy<T, Object> {

    @Override
    public ArtifactReference artifactReference(Record<Object> data, ParsedSchema<T> parsedSchema) {
        String topic = ((SerdeRecord<Object>) data).metadata().getTopic();
        int dot = topic.indexOf('.');
        return ArtifactReference.builder()
                .groupId(dot > 0 ? topic.substring(0, dot) : "default")
                .artifactId(dot > 0 ? topic.substring(dot + 1) : topic)
                .build();
    }

    @Override
    public boolean loadSchema() {
        return false;
    }
}
