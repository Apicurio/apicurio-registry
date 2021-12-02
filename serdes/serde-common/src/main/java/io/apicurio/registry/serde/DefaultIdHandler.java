package io.apicurio.registry.serde;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Map;

import org.apache.kafka.common.errors.SerializationException;

import io.apicurio.registry.serde.config.BaseKafkaSerDeConfig;
import io.apicurio.registry.serde.config.IdOption;
import io.apicurio.registry.serde.strategy.ArtifactReference;

/**
 * @author Ales Justin
 */
public class DefaultIdHandler implements IdHandler {
    static final int idSize = 8; // we use 8 / long

    private IdOption idOption = IdOption.globalId;

    /**
     * @see io.apicurio.registry.serde.IdHandler#configure(java.util.Map, boolean)
     */
    @Override
    public void configure(Map<String, Object> configs, boolean isKey) {
        BaseKafkaSerDeConfig config = new BaseKafkaSerDeConfig(configs);
        idOption = config.useIdOption();
    }

    /**
     * @see io.apicurio.registry.serde.IdHandler#writeId(io.apicurio.registry.serde.strategy.ArtifactReference, java.io.OutputStream)
     */
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
        out.write(ByteBuffer.allocate(idSize).putLong(id).array());
    }

    /**
     * @see io.apicurio.registry.serde.IdHandler#writeId(io.apicurio.registry.serde.strategy.ArtifactReference, java.nio.ByteBuffer)
     */
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
        buffer.putLong(id);
    }

    /**
     * @see io.apicurio.registry.serde.IdHandler#readId(java.nio.ByteBuffer)
     */
    @Override
    public ArtifactReference readId(ByteBuffer buffer) {
        if (idOption == IdOption.contentId) {
            return ArtifactReference.builder().contentId(buffer.getLong()).build();
        } else {
            return ArtifactReference.builder().globalId(buffer.getLong()).build();
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
