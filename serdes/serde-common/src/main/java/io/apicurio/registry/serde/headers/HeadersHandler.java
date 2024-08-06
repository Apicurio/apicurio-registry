package io.apicurio.registry.serde.headers;

import io.apicurio.registry.resolver.strategy.ArtifactReference;
import org.apache.kafka.common.header.Headers;

import java.util.Map;

/**
 * Common interface for headers handling when serializing/deserializing kafka records that have
 * {@link Headers}
 */
public interface HeadersHandler {

    default void configure(Map<String, Object> configs, boolean isKey) {
    }

    public void writeHeaders(Headers headers, ArtifactReference reference);

    /**
     * Reads the kafka message headers and returns an ArtifactReference that can contain or not information to
     * identify an Artifact in the registry.
     * 
     * @param headers
     * @return ArtifactReference
     */
    public ArtifactReference readHeaders(Headers headers);

}
