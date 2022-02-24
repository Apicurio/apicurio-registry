package io.apicurio.registry.serde;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Map;

import org.apache.kafka.common.errors.SerializationException;

import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.serde.config.BaseKafkaSerDeConfig;
import io.apicurio.registry.serde.config.IdOption;

/**
 * IdHandler that assumes 4 bytes for the magic number (the ID).
 *
 * @author Ales Justin
 */
public class Legacy4ByteIdHandler implements IdHandler {
    static final int idSize = 4; // e.g. Confluent uses 4 / int

    private IdOption idOption;

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
        if (idOption == IdOption.contentId) {
            if (reference.getContentId() == null) {
                throw new SerializationException("Missing contentId. IdOption is contentId but there is no contentId in the ArtifactReference");
            }
            id = reference.getContentId();
        } else {
            id = reference.getGlobalId();
        }
        out.write(ByteBuffer.allocate(idSize).putInt((int) id).array());
    }

    @Override
    public void writeId(ArtifactReference reference, ByteBuffer buffer) {
        long id;
        if (idOption == IdOption.contentId) {
            if (reference.getContentId() == null) {
                throw new SerializationException("Missing contentId. IdOption is contentId but there is no contentId in the ArtifactReference");
            }
            id = reference.getContentId();
        } else {
            id = reference.getGlobalId();
        }
        buffer.putInt((int) id);
    }

    /**
     * @see io.apicurio.registry.serde.IdHandler#readId(java.nio.ByteBuffer)
     */
    @Override
    public ArtifactReference readId(ByteBuffer buffer) {
        if (idOption == IdOption.contentId) {
            return ArtifactReference.builder().contentId(Long.valueOf(buffer.getInt())).build();
        } else {
            return ArtifactReference.builder().globalId(Long.valueOf(buffer.getInt())).build();
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
