package io.apicurio.registry.serde.data;

import io.apicurio.registry.resolver.strategy.ArtifactReference;
import org.apache.kafka.common.header.Headers;

/**
 * Kafka specific implementation for the Record Metadata abstraction used by the SchemaResolver
 */
public class KafkaSerdeMetadata extends SerdeMetadata {

    private final Headers headers;

    public KafkaSerdeMetadata(String topic, boolean isKey, Headers headers) {
        super(topic, isKey);
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
     * @return the headers
     */
    public Headers getHeaders() {
        return headers;
    }

}
