package io.apicurio.registry.serde.data;

import io.apicurio.registry.resolver.data.Metadata;
import io.apicurio.registry.resolver.strategy.ArtifactReference;

/**
 * Kafka specific implementation for the Record Metadata abstraction used by the SchemaResolver
 */
public class SerdeMetadata implements Metadata {

    private final String topic;
    private final boolean isKey;
    private final String operation;

    public SerdeMetadata(String topic, boolean isKey) {
        this(topic, isKey, null);
    }

    public SerdeMetadata(String topic, boolean isKey, String operation) {
        this.topic = topic;
        this.isKey = isKey;
        this.operation = operation;
    }

    /**
     * @see io.apicurio.registry.resolver.data.Metadata#artifactReference()
     */
    @Override
    public ArtifactReference artifactReference() {
        return null;
    }

    /**
     * @return the topic
     */
    public String getTopic() {
        return topic;
    }

    /**
     * @return the isKey
     */
    public boolean isKey() {
        return isKey;
    }

    @Override
    public String operation() {
        return operation;
    }

}
