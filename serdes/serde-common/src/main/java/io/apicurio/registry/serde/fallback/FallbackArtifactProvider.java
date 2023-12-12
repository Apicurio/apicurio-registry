package io.apicurio.registry.serde.fallback;

import io.apicurio.registry.resolver.strategy.ArtifactReference;
import org.apache.kafka.common.header.Headers;

import java.util.Map;

/**
 * Interface for providing a fallback ArtifactReference when the SchemaResolver is not able to find an
 * ArtifactReference in the kafka message
 */
public interface FallbackArtifactProvider {

    default void configure(Map<String, Object> configs, boolean isKey) {
    }

    /**
     * Returns an ArtifactReference that will be used as the fallback to search in the registry for the
     * artifact that will be used to deserialize the kafka message
     * 
     * @param topic
     * @param headers , can be null
     * @param data
     * @return
     */
    public ArtifactReference get(String topic, Headers headers, byte[] data);

}
