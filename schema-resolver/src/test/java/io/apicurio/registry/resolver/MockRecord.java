package io.apicurio.registry.resolver;

import io.apicurio.registry.resolver.data.Metadata;
import io.apicurio.registry.resolver.data.Record;
import io.apicurio.registry.resolver.strategy.ArtifactReference;

public class MockRecord<T> implements Record<T> {

    private T payload;
    private ArtifactReference reference;

    public MockRecord(T payload, ArtifactReference reference) {
        this.payload = payload;
        this.reference = reference;
    }

    @Override
    public Metadata metadata() {
        return new Metadata() {
            @Override
            public ArtifactReference artifactReference() {
                return reference;
            }
        };
    }

    @Override
    public T payload() {
        return payload;
    }
}
