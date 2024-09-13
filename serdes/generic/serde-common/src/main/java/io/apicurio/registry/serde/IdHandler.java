package io.apicurio.registry.serde;

import io.apicurio.registry.resolver.strategy.ArtifactReference;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Map;

/**
 * Handle artifact id in the msg bytes.
 */
public interface IdHandler {

    default void configure(Map<String, Object> configs, boolean isKey) {
    }

    void writeId(ArtifactReference reference, OutputStream out) throws IOException;

    void writeId(ArtifactReference reference, ByteBuffer buffer);

    ArtifactReference readId(ByteBuffer buffer);

    int idSize();
}
