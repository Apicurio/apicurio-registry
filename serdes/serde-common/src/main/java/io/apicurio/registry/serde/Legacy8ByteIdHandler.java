package io.apicurio.registry.serde;

import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.serde.config.IdOption;
import io.apicurio.registry.serde.config.SerdeConfig;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Map;

public class Legacy8ByteIdHandler implements IdHandler {
    static final int idSize = 8; // we use 8 / long
    private IdOption idOption = IdOption.globalId;

    @Override
    public void configure(Map<String, Object> configs, boolean isKey) {
        SerdeConfig config = new SerdeConfig(configs);
        idOption = config.useIdOption();
    }

    @Override
    public void writeId(ArtifactReference reference, OutputStream out) throws IOException {
        long id;
        if (idOption == IdOption.globalId) {
            if (reference.getGlobalId() == null) {
                throw new IllegalStateException(
                        "Missing globalId. IdOption is globalId but there is no contentId in the ArtifactReference");
            }
            id = reference.getGlobalId();
        } else {
            id = reference.getContentId();
        }
        out.write(ByteBuffer.allocate(idSize).putLong(id).array());
    }

    @Override
    public void writeId(ArtifactReference reference, ByteBuffer buffer) {
        long id;
        if (idOption == IdOption.globalId) {
            if (reference.getGlobalId() == null) {
                throw new IllegalStateException(
                        "Missing globalId. IdOption is globalId but there is no globalId in the ArtifactReference");
            }
            id = reference.getGlobalId();
        } else {
            id = reference.getContentId();
        }
        buffer.putLong(id);
    }

    @Override
    public ArtifactReference readId(ByteBuffer buffer) {
        if (idOption == IdOption.globalId) {
            return ArtifactReference.builder().globalId(buffer.getLong()).build();
        } else {
            return ArtifactReference.builder().contentId(buffer.getLong()).build();
        }
    }

    @Override
    public int idSize() {
        return idSize;
    }
}
