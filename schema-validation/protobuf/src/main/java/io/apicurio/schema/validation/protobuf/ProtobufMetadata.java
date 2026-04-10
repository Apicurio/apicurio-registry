package io.apicurio.schema.validation.protobuf;

import io.apicurio.registry.resolver.data.Metadata;
import io.apicurio.registry.resolver.strategy.ArtifactReference;

public class ProtobufMetadata implements Metadata {

    private final ArtifactReference artifactReference;

    public ProtobufMetadata(ArtifactReference artifactReference) {
        this.artifactReference = artifactReference;
    }

    @Override
    public ArtifactReference artifactReference() {
        return this.artifactReference;
    }
}