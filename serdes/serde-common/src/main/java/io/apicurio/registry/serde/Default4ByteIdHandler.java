package io.apicurio.registry.serde;

import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.serde.config.BaseKafkaSerDeConfig;
import io.apicurio.registry.serde.config.IdOption;
import org.apache.kafka.common.errors.SerializationException;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Map;

/**
 * IdHandler that assumes 4 bytes for the magic number (the ID).
 */
public class Default4ByteIdHandler implements IdHandler {
    static final int idSize = 4; // e.g. Confluent uses 4 / int

    private IdOption idOption = IdOption.contentId;

    /**
     * @see io.apicurio.registry.serde.IdHandler#configure(java.util.Map, boolean)
     */
    @Override
    public void configure(Map<String, Object> configs, boolean isKey) {
        BaseKafkaSerDeConfig config = new BaseKafkaSerDeConfig(configs);
        idOption = config.useIdOption();
    }

    @Override
    public void writeId(ArtifactReference reference, OutputStream out) throws IOException {
        long id;
        if (idOption == IdOption.globalId) {
            if (reference.getGlobalId() == null) {
                throw new SerializationException(
                        "Missing globalId. IdOption is globalId but there is no contentId in the ArtifactReference");
            }
            id = reference.getGlobalId();
        } else {
            id = reference.getContentId();
        }
        out.write(ByteBuffer.allocate(idSize).putInt((int) id).array());
    }

    @Override
    public void writeId(ArtifactReference reference, ByteBuffer buffer) {
        long id;
        if (idOption == IdOption.globalId) {
            if (reference.getGlobalId() == null) {
                throw new SerializationException(
                        "Missing globalId. IdOption is globalId but there is no globalId in the ArtifactReference");
            }
            id = reference.getGlobalId();
        } else {
            id = reference.getContentId();
        }
        buffer.putInt((int) id);
    }

    /**
     * @see io.apicurio.registry.serde.IdHandler#readId(java.nio.ByteBuffer)
     */
    @Override
    public ArtifactReference readId(ByteBuffer buffer) {
        if (idOption == IdOption.globalId) {
            return ArtifactReference.builder().globalId(Long.valueOf(buffer.getInt())).build();
        } else {
            return ArtifactReference.builder().contentId(Long.valueOf(buffer.getInt())).build();
        }
    }

    /**
     * @see io.apicurio.registry.serde.IdHandler#idSize()
     */
    @Override
    public int idSize() {
        return idSize;
    }
}
