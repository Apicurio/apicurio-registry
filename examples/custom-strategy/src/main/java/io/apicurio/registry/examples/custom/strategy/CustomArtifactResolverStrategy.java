package io.apicurio.registry.examples.custom.strategy;

import io.apicurio.registry.serde.strategy.ArtifactReference;
import io.apicurio.registry.serde.strategy.ArtifactResolverStrategy;

public class CustomArtifactResolverStrategy implements ArtifactResolverStrategy<Object> {

    @Override
    public ArtifactReference artifactReference(String topic, boolean isKey, Object schema) {
        return ArtifactReference.builder()
            .artifactId("my-artifact-" + topic + (isKey ? "-key" : "-value"))
            .build();
    }

    @Override
    public boolean loadSchema() {
        return false;
    }
    
}
