package io.apicurio.registry.serde.data;

import io.apicurio.registry.resolver.data.Metadata;
import io.apicurio.registry.resolver.strategy.ArtifactReference;
import org.apache.kafka.common.header.Headers;

/**
 * Kafka specific implementation for the Record Metadata abstraction used by the SchemaResolver
 */
public class KafkaSerdeMetadata implements Metadata {

    private String topic;
    private boolean isKey;
    private Headers headers;

    public KafkaSerdeMetadata(String topic, boolean isKey, Headers headers) {
        this.topic = topic;
        this.isKey = isKey;
        this.headers = headers;
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

    /**
     * @return the headers
     */
    public Headers getHeaders() {
        return headers;
    }

}
